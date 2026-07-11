package mousemaster;

import com.sun.jna.Native;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ApplicationLauncher {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationLauncher.class);

    @FunctionalInterface
    public interface PlatformFactory {
        Platform create(ApplicationOptions options) throws Exception;
    }

    private ApplicationLauncher() {
    }

    public static void run(String[] args, PlatformFactory platformFactory)
            throws InterruptedException, IOException {
        ApplicationOptions options = ApplicationOptions.parse(args);
        MousemasterApplication.setTempDirectory(options.tempDirectory());
        if (options.logLevel() != null)
            MousemasterApplication.setLogLevel(options.logLevel());
        if (options.logToFile())
            MousemasterApplication.enableLogToFile();
        Version version = readVersion();
        if (options.showVersion()) {
            System.out.println("mousemaster v" + version.version() + " (" +
                               version.commitId() + ")");
            return;
        }
        if (options.graalvmAgentRun()) {
            logger.info("--graalvm-agent-run flag found, exiting in 20s");
            new Thread(() -> {
                try {
                    Thread.sleep(20000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                System.exit(0);
            }).start();
        }
        Platform platform = createPlatform(platformFactory, options);
        logger.info("mousemaster v" + version.version() + " (" + version.commitId() + ")");
        if (platform == null)
            return;
        try {
            Native.setCallbackExceptionHandler((c, e) ->
                    MousemasterApplication.shutdownAfterException(e, platform, true,
                            options.pauseOnError()));
            new Mousemaster(options.configurationPath(), platform).run();
        } catch (Throwable e) {
            MousemasterApplication.shutdownAfterException(e, platform, false,
                    options.pauseOnError());
        }
    }

    private static Platform createPlatform(PlatformFactory platformFactory,
                                           ApplicationOptions options) {
        try {
            return platformFactory.create(options);
        } catch (Exception e) {
            MousemasterApplication.shutdownAfterException(e, null, false,
                    options.pauseOnError());
        }
        return null;
    }

    private static Version readVersion() throws IOException {
        try (InputStream versionInputStream = ApplicationLauncher.class.getClassLoader()
                                                                       .getResourceAsStream(
                                                                               "application.properties")) {
            Properties versionProp = new Properties();
            versionProp.load(versionInputStream);
            return new Version(versionProp.getProperty("version"),
                    versionProp.getProperty("commitId"));
        }
    }

    private record Version(String version, String commitId) {
    }
}
