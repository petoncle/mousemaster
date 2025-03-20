package mousemaster;

import io.qt.QtUtilities;
import io.qt.widgets.QApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class QtManager {

    private static final Logger logger = LoggerFactory.getLogger(QtManager.class.getName());

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

    private static final List<String> qtJambiPaths = List.of(
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

    public static void initialize() throws IOException {
        File extractDirectory = createExtractDirectory(
                MousemasterApplication.tempDirectory);
        for (String resourcesPath : windowsResourcesPaths) {
            Path extractPath = Paths.get(extractDirectory.getAbsolutePath() + "/" + resourcesPath);
            Files.createDirectories(extractPath.getParent());
            extractResourceFile(resourcesPath, extractPath);
        }
        for (String qtJambiPath : qtJambiPaths) {
            Path extractPath = Paths.get(extractDirectory.getAbsolutePath() + "/qt/" + qtJambiPath);
            extractResourceFile(qtJambiPath, extractPath);
        }
        logger.trace("Extracted Qt files to " + extractDirectory.getAbsolutePath());
        System.setProperty("io.qt.library-path-override",
                extractDirectory.getAbsolutePath() + "/qt/bin");
        // QtJambi expects DLLs in io.qt.library-path-override, and io.qt.library-path-override/../plugins/platforms

//        System.setProperty("QT_ENABLE_HIGHDPI_SCALING", "0");
//        setEnv("QT_ENABLE_HIGHDPI_SCALING", "0");
//        System.setProperty("QT_AUTO_SCREEN_SCALE_FACTOR", "0");
//        System.setProperty("QT_SCALE_FACTOR", "1");
        // https://forum.qt.io/topic/141511/qt_enable_highdpi_scaling-has-no-effect
        try {
            // Just to trigger the static initializer which loads DLLs.
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
        QtUtilities.putenv("QT_ENABLE_HIGHDPI_SCALING", "0"); // Only works on Windows?
        logger.trace("highDpiScaleFactorRoundingPolicy is " + QApplication.highDpiScaleFactorRoundingPolicy());
        // Default font engine on Windows is directwrite. Antialiasing seems better with gdi.
        QApplication.initialize(new String[] { "-platform", "windows:fontengine=gdi" });
//        QApplication.initialize(new String[] { });
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
                // java.nio.file.FileSystemException: C:\Users\x\AppData\Local\Temp\mousemaster-110364797\qt\bin\Qt6Core.dll: The process cannot access the file because it is being used by another process
                // logger.debug("Unable to extract resource file " + resourcesPath, e);
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
