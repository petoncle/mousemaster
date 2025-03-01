package mousemaster;

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
    private static final org.slf4j.Logger log =
            LoggerFactory.getLogger(QtManager.class);

    public static void initialize() throws IOException {
        File extractDirectory = createExtractDirectory();
        for (String resourcesPath : windowsResourcesPaths) {
            Path extractPath = Paths.get(extractDirectory.getAbsolutePath() + "/" + resourcesPath);
            Files.createDirectories(extractPath.getParent());
            try (InputStream inputStream = MousemasterApplication.class.getClassLoader().getResourceAsStream(resourcesPath)) {
                try (OutputStream outputStream = Files.newOutputStream(extractPath,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
                    inputStream.transferTo(outputStream);
                }
            }
        }
        logger.trace("Extracted Qt files to " + extractDirectory.getAbsolutePath());
        System.setProperty("io.qt.library-path-override", extractDirectory.getAbsolutePath() + "/qt/bin");
        QApplication.initialize(new String[]{});
    }

    public static void stop() {
        QApplication.shutdown();
    }

    public static void processEvents() {
        QApplication.processEvents();
    }

    private static File createExtractDirectory() throws IOException {
        // See com.sun.jna.Native.getTempDir.
        File tmp = new File(System.getProperty("java.io.tmpdir"));
        File qtTmp = new File(tmp, "mousemaster-" + System.getProperty("user.name").hashCode());
        qtTmp.mkdirs();
        if (!qtTmp.canWrite()) {
            throw new IOException("Qt extract directory '" + qtTmp + "' is not writable");
        }
        return qtTmp;
    }

}
