package mousemaster;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.util.Scanner;
import java.util.logging.LogManager;

public class MousemasterApplication {

    private static final Logger logger;
    public static String tempDirectory;

    static {
        System.setProperty("slf4j.internal.verbosity", "WARN");
        logger = (Logger) LoggerFactory.getLogger(MousemasterApplication.class);
        // QtJambi uses JUL. We want it bridged with slf4j.
        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.install();
    }

    public static void setTempDirectory(String tempDirectory) {
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
            MousemasterApplication.tempDirectory =
                    System.getProperty("java.io.tmpdir") + File.separator + "mousemaster-" +
                    System.getProperty("user.name").hashCode();
        String jnaTmpDir = MousemasterApplication.tempDirectory + "/jna";
        System.setProperty("jna.tmpdir", jnaTmpDir);
        new File(jnaTmpDir).mkdirs();
    }

    public static void shutdownAfterException(Throwable e, Platform platform,
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

    public static void enableLogToFile() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        if (rootLogger.getAppender("FILE") != null)
            return;
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} [%thread] %-5level %logger{36} - %msg%n");
        encoder.start();
        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setName("FILE");
        fileAppender.setContext(context);
        fileAppender.setFile("mousemaster.log");
        fileAppender.setEncoder(encoder);
        fileAppender.setAppend(true);
        fileAppender.start();
        rootLogger.addAppender(fileAppender);
    }

    public static void disableLogToFile() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        Appender<ILoggingEvent> fileAppender = rootLogger.getAppender("FILE");
        if (fileAppender != null) {
            rootLogger.detachAppender(fileAppender);
            fileAppender.stop();
        }
    }

    public static void setLogLevel(String level) {
        Logger logger = (Logger) LoggerFactory.getLogger("mousemaster");
        logger.setLevel(Level.valueOf(level));
    }

}
