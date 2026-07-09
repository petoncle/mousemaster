package mousemaster;

import io.qt.QtUtilities;
import io.qt.widgets.QApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

public class QtManager {

    private static final Logger logger = LoggerFactory.getLogger(QtManager.class.getName());

    private static final boolean IS_LINUX =
            System.getProperty("os.name").toLowerCase().contains("linux");

    // Windows: Qt6 runtime DLLs bundled in src/main/resources/qt/bin/
    private static final List<String> windowsResourcesPaths = List.of(
            "qt/bin/Qt6Core.dll",
            "qt/bin/Qt6Gui.dll",
            "qt/bin/Qt6Widgets.dll",
            "qt/plugins/platforms/qdirect2d.dll",
            "qt/plugins/platforms/qdirect2dd.dll",
            "qt/plugins/platforms/qminimal.dll",
            "qt/plugins/platforms/qminimald.dll",
            "qt/plugins/platforms/qoffscreen.dll",
            "qt/plugins/platforms/qoffscreend.dll",
            "qt/plugins/platforms/qwindows.dll",
            "qt/plugins/platforms/qwindowsd.dll"
    );

    private static final List<String> qtJambiWindowsPaths = List.of(
            "bin/QtJambi6.dll",
            "bin/QtJambiCore6.dll",
            "bin/QtJambiGui6.dll",
            "bin/QtJambiWidgets6.dll"
    );

    private static final List<String> msvcpDllPaths = List.of(
            "qt-msvcp/msvcp140.dll",
            "qt-msvcp/msvcp140_1.dll",
            "qt-msvcp/msvcp140_2.dll"
    );

    // Linux: QtJambi bridge .so files from qtjambi-native-linux-x64-6.8.2.jar
    // Qt6 runtime (libQt6Core.so.6 etc.) comes from LD_LIBRARY_PATH (nix devshell / system)
    private static final List<String> qtJambiLinuxPaths = List.of(
            "lib/libQtJambi.so.6.8.2",
            "lib/libQtJambiCore.so.6.8.2",
            "lib/libQtJambiGui.so.6.8.2",
            "lib/libQtJambiWidgets.so.6.8.2",
            "lib/libQtJambiGuiRhi.so.6.8.2"
    );

    // For each base name, the symlinks the dynamic linker needs (per qtjambi-deployment.xml)
    private static final Map<String, String> linuxSymlinkBases = Map.of(
            "libQtJambi", "libQtJambi.so.6.8.2",
            "libQtJambiCore", "libQtJambiCore.so.6.8.2",
            "libQtJambiGui", "libQtJambiGui.so.6.8.2",
            "libQtJambiWidgets", "libQtJambiWidgets.so.6.8.2",
            "libQtJambiGuiRhi", "libQtJambiGuiRhi.so.6.8.2"
    );

    public static void initialize() throws IOException {
        if (IS_LINUX)
            initializeLinux();
        else
            initializeWindows();
    }

    private static void initializeWindows() throws IOException {
        File extractDirectory = createExtractDirectory(MousemasterApplication.tempDirectory);
        for (String resourcesPath : windowsResourcesPaths) {
            Path extractPath = Paths.get(extractDirectory.getAbsolutePath() + "/" + resourcesPath);
            Files.createDirectories(extractPath.getParent());
            extractResourceFile(resourcesPath, extractPath);
        }
        for (String qtJambiPath : qtJambiWindowsPaths) {
            Path extractPath = Paths.get(extractDirectory.getAbsolutePath() + "/qt/" + qtJambiPath);
            extractResourceFile(qtJambiPath, extractPath);
        }
        logger.trace("Extracted Qt files to " + extractDirectory.getAbsolutePath());
        System.setProperty("io.qt.library-path-override",
                extractDirectory.getAbsolutePath() + "/qt/bin");
        // QtJambi expects DLLs in io.qt.library-path-override, and io.qt.library-path-override/../plugins/platforms
        try {
            QtUtilities.jambiDeploymentDir();
        } catch (UnsatisfiedLinkError e) {
            for (String msvcpDllResourcePath : msvcpDllPaths) {
                extractResourceFile(msvcpDllResourcePath,
                        Paths.get(msvcpDllResourcePath.replaceAll(".*/", "")));
            }
            UnsatisfiedLinkError e2 =
                    new UnsatisfiedLinkError("Unable to initialize Qt. msvcp DLLs have been extracted next to mousemaster.exe. Try to restart mousemaster. " + e.getMessage());
            e2.setStackTrace(e.getStackTrace());
            throw e2;
        }
        QtUtilities.putenv("QT_ENABLE_HIGHDPI_SCALING", "0");
        logger.trace("highDpiScaleFactorRoundingPolicy is " + QApplication.highDpiScaleFactorRoundingPolicy());
        QApplication.initialize(new String[] { "-platform", "windows:fontengine=gdi" });
    }

    private static void initializeLinux() throws IOException {
        File extractDirectory = createExtractDirectory(MousemasterApplication.tempDirectory);
        Path libDir = Paths.get(extractDirectory.getAbsolutePath(), "qt", "lib");
        Files.createDirectories(libDir);

        // Extract QtJambi bridge .so files from the qtjambi-native-linux-x64 JAR on classpath
        for (String qtJambiPath : qtJambiLinuxPaths) {
            String fileName = qtJambiPath.substring(qtJambiPath.lastIndexOf('/') + 1);
            Path extractPath = libDir.resolve(fileName);
            extractResourceFile(qtJambiPath, extractPath);
        }

        // Create SONAME symlinks required by the dynamic linker (from qtjambi-deployment.xml)
        for (Map.Entry<String, String> entry : linuxSymlinkBases.entrySet()) {
            String base = entry.getKey();
            String target = entry.getValue();
            createSymlinkIfAbsent(libDir.resolve(base + ".so"), libDir.resolve(target));
            createSymlinkIfAbsent(libDir.resolve(base + ".so.6"), libDir.resolve(target));
            createSymlinkIfAbsent(libDir.resolve(base + ".so.6.8"), libDir.resolve(target));
        }

        logger.trace("Extracted QtJambi .so files to " + libDir);
        System.setProperty("io.qt.library-path-override", libDir.toString());
        // Qt6 runtime (libQt6Core.so.6 etc.) resolved via LD_LIBRARY_PATH from nix devshell / system
        QtUtilities.jambiDeploymentDir();
        logger.trace("highDpiScaleFactorRoundingPolicy is " + QApplication.highDpiScaleFactorRoundingPolicy());
        QApplication.initialize(new String[]{});
    }

    private static void createSymlinkIfAbsent(Path link, Path target) {
        try {
            // Use relative target so the symlinks work regardless of temp dir path
            Path relativeTarget = link.getParent().relativize(target);
            Files.createSymbolicLink(link, relativeTarget);
        } catch (FileAlreadyExistsException ignored) {
            // Already exists from a prior run — fine
        } catch (IOException e) {
            logger.warn("Could not create symlink {} -> {}: {}", link, target, e.getMessage());
        }
    }

    private static void extractResourceFile(String resourcesPath, Path extractPath)
            throws IOException {
        try (InputStream inputStream = MousemasterApplication.class.getClassLoader().getResourceAsStream(
                resourcesPath)) {
            try (OutputStream outputStream = Files.newOutputStream(extractPath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                inputStream.transferTo(outputStream);
            } catch (IOException e) {
                // File in use (Windows: DLL loaded by a prior process instance) — skip silently
            }
        }
    }

    public static void stop() {
        QApplication.shutdown();
    }

    public static void processEvents() {
        QApplication.processEvents();
    }

    private static File createExtractDirectory(String tempDirectory) throws IOException {
        File tempDirectoryFile = new File(tempDirectory);
        tempDirectoryFile.mkdirs();
        if (!tempDirectoryFile.canWrite()) {
            throw new IOException("Qt extract directory '" + tempDirectoryFile + "' is not writable");
        }
        return tempDirectoryFile;
    }

}
