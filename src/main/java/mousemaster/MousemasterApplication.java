package mousemaster;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.OutputStreamAppender;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.Iterator;
import java.util.Scanner;
import java.util.logging.LogManager;

public class MousemasterApplication {

    private static final Logger logger;
    public static String tempDirectory;
    private static int modeColumnWidth;

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
            if (Os.windows) {
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
                    System.getProperty("java.io.tmpdir") + "mousemaster-" +
                    System.getProperty("user.name").hashCode();
        System.setProperty("jna.tmpdir", MousemasterApplication.tempDirectory + "/jna");
    }

    public static void shutdownAfterException(Throwable e, Platform platform,
                                              boolean jnaCallback,
                                              boolean pauseOnError) {
        if (platform != null)
            platform.shutdown();
        logger.error(jnaCallback ? "Error in JNA callback" : "", e);
        // Without a terminal there is nobody to press Enter: the read blocks or throws,
        // either of which skips the shutdown below. System.console() alone stopped
        // meaning interactive in java 22, it is now non null on a redirected stream too.
        if (pauseOnError && System.console() != null && System.console().isTerminal()) {
            logger.info(
                    "An error has occurred. The details of the error should be right above this message. Press Enter in this window to close mousemaster.");
            new Scanner(System.in).nextLine();
        }
        if (platform == null)
            System.exit(1); // Qt was never initialized: exiting cannot hang.
        platform.killProcess(1);
    }

    public static void enableLogToFile() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        if (rootLogger.getAppender("FILE") != null)
            return;
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        startWithCurrentPattern(encoder);
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

    /**
     * The mode column is only as wide as the longest mode name of the loaded
     * configuration.
     */
    public static void setModeColumnWidth(int width) {
        modeColumnWidth = width;
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        for (Iterator<Appender<ILoggingEvent>> appenders =
             rootLogger.iteratorForAppenders(); appenders.hasNext(); ) {
            Appender<ILoggingEvent> appender = appenders.next();
            if (appender instanceof OutputStreamAppender<ILoggingEvent> outputStreamAppender &&
                outputStreamAppender.getEncoder() instanceof PatternLayoutEncoder encoder)
                startWithCurrentPattern(encoder);
        }
    }

    private static void startWithCurrentPattern(PatternLayoutEncoder encoder) {
        encoder.stop();
        encoder.setPattern("%d{HH:mm:ss.SSS} %-5level %-" + modeColumnWidth +
                           "X{mode} %-16logger{0} %msg%n");
        encoder.start();
    }

}
