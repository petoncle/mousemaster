package mousemaster;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.sun.jna.Native;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Scanner;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class MousemasterApplication {

    private static final Logger logger;
    public static String tempDirectory;

    static {
        System.setProperty("slf4j.internal.verbosity", "WARN");
        logger = (Logger) LoggerFactory.getLogger(MousemasterApplication.class);
        // See io/qtjambi/qtjambi/6.8.2/qtjambi-6.8.2-sources.jar!/io/qt/internal/ResourceUtility.java
        // It tries to use jrt which is not implemented in graalvm native, throws and logs an exception,
        // but that does not seem to prevent it from working.
        java.util.logging.Logger.getLogger("io.qt.internal.fileengine").setLevel(
                java.util.logging.Level.OFF);
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        String tempDirectory = Stream.of(args)
                                     .filter(arg -> arg.startsWith("--temp-directory="))
                                     .map(arg -> arg.split("=")[1])
                                     .findFirst().orElse(null);
        setTempDirectory(tempDirectory);
        Stream.of(args)
              .filter(arg -> arg.startsWith("--log-level="))
              .map(arg -> arg.split("=")[1])
              .findFirst()
              .ifPresent(MousemasterApplication::setLogLevel);
        boolean pauseOnError = Stream.of(args)
                                     .filter(arg -> arg.startsWith("--pause-on-error="))
                                     .map(arg -> arg.split("=")[1])
                                     .findFirst()
                                     .map(Boolean::parseBoolean)
                                     .orElse(true);
        String version;
        try (InputStream versionInputStream = MousemasterApplication.class.getClassLoader().getResourceAsStream("application.properties")) {
            Properties versionProp = new Properties();
            versionProp.load(versionInputStream);
            version = versionProp.getProperty("version");
        }
        Path configurationPath = Stream.of(args)
                                       .filter(arg -> arg.startsWith(
                                               "--configuration-file="))
                                       .map(arg -> arg.split("=")[1])
                                       .findFirst()
                                       .map(Paths::get)
                                       .orElse(Paths.get("mousemaster.properties"));
        boolean multipleInstancesAllowed =
                Stream.of(args)
                      .filter(arg -> arg.startsWith("--multiple-instances-allowed="))
                      .map(arg -> arg.split("=")[1])
                      .findFirst()
                      .map(Boolean::parseBoolean)
                      .orElse(false);
        boolean keyRegurgitationEnabled = // Feature flag.
                Stream.of(args)
                      .filter(arg -> arg.startsWith("--key-regurgitation-enabled="))
                      .map(arg -> arg.split("=")[1])
                      .findFirst()
                      .map(Boolean::parseBoolean)
                      .orElse(true); // Remove this feature flag if confirmed working
        if (Stream.of(args).anyMatch(Predicate.isEqual(("--graalvm-agent-run")))) {
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
        WindowsPlatform platform = platform(multipleInstancesAllowed, keyRegurgitationEnabled, pauseOnError);
        logger.info("mousemaster v" + version);
        if (platform == null)
            return;
        try {
            Native.setCallbackExceptionHandler((c, e) -> shutdownAfterException(e, platform, true, pauseOnError));
            new Mousemaster(configurationPath, platform).run();
        } catch (Throwable e) {
            shutdownAfterException(e, platform, false, pauseOnError);
        }
    }

    private static void setTempDirectory(String tempDirectory) {
        if (tempDirectory != null)
            MousemasterApplication.tempDirectory = tempDirectory;
        else {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                boolean userNameIsAscii =
                        System.getProperty("user.name").matches("[\\x00-\\x7F]+");
                if (!userNameIsAscii) {
                    // https://github.com/oracle/graal/issues/8095
                    MousemasterApplication.tempDirectory = "mousemaster-temp";
                }
            }
        }
        if (MousemasterApplication.tempDirectory == null)
            MousemasterApplication.tempDirectory = System.getProperty("java.io.tmpdir") + "mousemaster-" + System.getProperty("user.name").hashCode();
        System.setProperty("jna.tmpdir", MousemasterApplication.tempDirectory + "/jna");
    }

    private static WindowsPlatform platform(boolean multipleInstancesAllowed,
                                            boolean keyRegurgitationEnabled,
                                            boolean pauseOnError) {
        try {
            return new WindowsPlatform(multipleInstancesAllowed, keyRegurgitationEnabled);
        } catch (Exception e) {
            shutdownAfterException(e, null, false, pauseOnError);
        }
        return null;
    }

    private static void shutdownAfterException(Throwable e, Platform platform,
                                               boolean jnaCallback,
                                               boolean pauseOnError) {
        if (platform != null)
            platform.shutdown();
        logger.error(jnaCallback ? "Error in JNA callback" : "", e);
        if (pauseOnError) {
            logger.info(
                    "An error has occurred. The details of the error should be right above this message. Press Enter in this window to close mousemaster.");
            new Scanner(System.in).nextLine();
        }
        System.exit(1);
    }

    public static void setLogLevel(String level) {
        Logger logger = (Logger) LoggerFactory.getLogger("mousemaster");
        logger.setLevel(Level.valueOf(level));
    }

}
