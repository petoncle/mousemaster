package mousemaster.platform.windows;

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

public class WindowsMain {

    private static final Logger logger = LoggerFactory.getLogger(WindowsMain.class);

    public static void main(String[] args) throws InterruptedException, IOException {
        ApplicationOptions options = ApplicationOptions.parse(args);
        MousemasterApplication.setTempDirectory(options.tempDirectory());
        if (options.logLevel() != null)
            MousemasterApplication.setLogLevel(options.logLevel());
        if (options.logToFile())
            MousemasterApplication.enableLogToFile();
        String version;
        String commitId;
        try (InputStream versionInputStream = WindowsMain.class.getClassLoader()
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
        Platform platform = createPlatform(options.multipleInstancesAllowed(),
                options.keyRegurgitationEnabled(), options.pauseOnError(),
                options.ignoreInjectedEvents());
        logger.info("mousemaster v" + version + " (" + commitId + ")");
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
                                           boolean pauseOnError,
                                           boolean ignoreInjectedEvents) {
        try {
            return new WindowsPlatform(multipleInstancesAllowed, keyRegurgitationEnabled,
                    ignoreInjectedEvents);
        } catch (Exception e) {
            MousemasterApplication.shutdownAfterException(e, null, false, pauseOnError);
        }
        return null;
    }

}
