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

    static {
        System.setProperty("slf4j.internal.verbosity", "WARN");
        logger = (Logger) LoggerFactory.getLogger(MousemasterApplication.class);
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        Stream.of(args)
              .filter(arg -> arg.startsWith("--log-level="))
              .map(arg -> arg.split("=")[1])
              .findFirst()
              .ifPresent(MousemasterApplication::setLogLevel);
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
        WindowsPlatform platform = platform(keyRegurgitationEnabled);
        logger.info("mousemaster v" + version);
        if (platform == null)
            return;
        try {
            Native.setCallbackExceptionHandler((c, e) -> shutdownAfterException(e, platform, true));
            new Mousemaster(configurationPath, platform).run();
        } catch (Throwable e) {
            shutdownAfterException(e, platform, false);
        }
    }

    private static WindowsPlatform platform(boolean keyRegurgitationEnabled) {
        try {
            return new WindowsPlatform(keyRegurgitationEnabled);
        } catch (Exception e) {
            shutdownAfterException(e, null, false);
        }
        return null;
    }

    private static void shutdownAfterException(Throwable e, Platform platform, boolean jnaCallback) {
        if (platform != null)
            platform.shutdown();
        logger.error(jnaCallback ? "Error in JNA callback" : "", e);
        logger.info(
                "An error has occurred. The details of the error should be right above this message. Press Enter in this window to close mousemaster.");
        new Scanner(System.in).nextLine();
        System.exit(1);
    }

    private static void setLogLevel(String level) {
       Logger logger = (Logger) LoggerFactory.getLogger("mousemaster");
       logger.setLevel(Level.valueOf(level));
    }

}
