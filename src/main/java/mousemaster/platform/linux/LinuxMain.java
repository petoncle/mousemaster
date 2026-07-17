package mousemaster.platform.linux;

import com.sun.jna.Native;
import mousemaster.ApplicationOptions;
import mousemaster.Mousemaster;
import mousemaster.MousemasterApplication;
import mousemaster.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class LinuxMain {

    private static final Logger logger = LoggerFactory.getLogger(LinuxMain.class);

    public static void main(String[] args) throws InterruptedException, IOException {
        ApplicationOptions options = ApplicationOptions.parse(args);
        MousemasterApplication.setTempDirectory(options.tempDirectory());
        if (options.logLevel() != null)
            MousemasterApplication.setLogLevel(options.logLevel());
        if (options.logToFile())
            MousemasterApplication.enableLogToFile();
        String version;
        String commitId;
        try (InputStream versionInputStream = LinuxMain.class.getClassLoader()
                                                              .getResourceAsStream(
                                                                      "application.properties")) {
            Properties versionProp = new Properties();
            versionProp.load(versionInputStream);
            version = versionProp.getProperty("version");
            commitId = versionProp.getProperty("commitId");
        }
        if (options.showVersion()) {
            System.out.println("mousemaster v" + version + " (" + commitId + ")");
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
        Thread failsafe = new Thread(() -> {
            try {
                Thread.sleep(300_000);
            } catch (InterruptedException ignored) {
                return;
            }
            logger.warn("5-minute failsafe triggered — forcing exit to release keyboard grab");
            System.exit(0);
        }, "failsafe-shutdown");
        failsafe.setDaemon(true);
        failsafe.start();

        Platform platform = createPlatform(options.multipleInstancesAllowed(),
                options.keyRegurgitationEnabled(), options.pauseOnError());
        logger.info("mousemaster v" + version + " (" + commitId + ") [Linux]");
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

    private static Platform createPlatform(boolean multipleInstancesAllowed,
                                           boolean keyRegurgitationEnabled,
                                           boolean pauseOnError) {
        try {
            return new LinuxPlatform(multipleInstancesAllowed, keyRegurgitationEnabled);
        } catch (Exception e) {
            MousemasterApplication.shutdownAfterException(e, null, false, pauseOnError);
        }
        return null;
    }

}
