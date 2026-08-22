package mousemaster;

import io.qt.core.QEvent;
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
import java.nio.file.attribute.FileTime;
import java.util.List;

public class QtManager {

    private static final Logger logger = LoggerFactory.getLogger(QtManager.class.getName());

    private static final List<String> qtResourcesPaths = List.of(
            "qt/bin/Qt6Core.dll",
            "qt/bin/Qt6Gui.dll",
            "qt/bin/Qt6Widgets.dll",
            "qt/bin/QtJambi6.dll",
            "qt/bin/QtJambiCore6.dll",
            "qt/bin/QtJambiGui6.dll",
            "qt/bin/QtJambiWidgets6.dll",
            "qt/plugins/platforms/qwindows.dll"
    );

    private static final List<String> msvcpDllPaths = List.of(
            "qt-msvcp/msvcp140.dll",
            "qt-msvcp/msvcp140_1.dll",
            "qt-msvcp/msvcp140_2.dll"
    );


    public static void initialize() throws IOException {
        if (!Os.macos) {
            File extractDirectory = createExtractDirectory(
                    MousemasterApplication.tempDirectory);
            extractQtFiles(extractDirectory);
            System.setProperty("io.qt.library-path-override",
                    extractDirectory.getAbsolutePath() + "/qt/bin");
        }
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
            if (Os.macos)
                throw e;
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
        // Qt's raster engine caches glyph masks only below this size and otherwise falls back to
        // filling a path per glyph, ~50x slower. The default 64 is reached by a 16pt font at 300%
        // scaling, well within what a hint mesh uses.
        QtUtilities.putenv("QT_MAX_CACHED_GLYPH_SIZE", "256");
        logger.trace("High DPI scale factor rounding policy is " + QApplication.highDpiScaleFactorRoundingPolicy());
        // Default font engine on Windows is directwrite. Antialiasing seems better with gdi.
        QApplication.initialize(Os.macos ? new String[] {} :
                new String[] { "-platform", "windows:fontengine=gdi" });
    }

    /**
     * Writing the DLLs is most of the startup time, so they are left in the temp
     * directory and written again only once mousemaster.exe is newer than them.
     */
    private static void extractQtFiles(File extractDirectory) throws IOException {
        if (qtFilesAreNewerThanExecutable(extractDirectory)) {
            logger.trace("Reusing the Qt files in " + extractDirectory.getAbsolutePath());
            return;
        }
        for (String resourcesPath : qtResourcesPaths) {
            Path extractPath = extractPath(extractDirectory, resourcesPath);
            Files.createDirectories(extractPath.getParent());
            extractResourceFile(resourcesPath, extractPath);
        }
        logger.trace("Extracted Qt files to " + extractDirectory.getAbsolutePath());
    }

    private static boolean qtFilesAreNewerThanExecutable(File extractDirectory)
            throws IOException {
        String executable = ProcessHandle.current().info().command().orElse(null);
        if (executable == null)
            return false;
        FileTime executableTime = Files.getLastModifiedTime(Paths.get(executable));
        for (String resourcesPath : qtResourcesPaths) {
            Path extractPath = extractPath(extractDirectory, resourcesPath);
            if (!Files.exists(extractPath) ||
                Files.getLastModifiedTime(extractPath).compareTo(executableTime) < 0)
                return false;
        }
        return true;
    }

    private static Path extractPath(File extractDirectory, String resourcesPath) {
        return Paths.get(extractDirectory.getAbsolutePath() + "/" + resourcesPath);
    }

    private static void extractResourceFile(String resourcesPath, Path extractPath)
            throws IOException {
        try (InputStream inputStream = MousemasterApplication.resourceStream(resourcesPath)) {
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
        // disposeLater posts a deletion that only an exec() loop returning to itself carries
        // out, and this application never runs one: without this the widgets pile up.
        QApplication.sendPostedEvents(null, QEvent.Type.DeferredDispose);
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
