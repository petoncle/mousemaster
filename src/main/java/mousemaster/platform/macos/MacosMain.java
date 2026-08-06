package mousemaster.platform.macos;

import mousemaster.ApplicationOptions;
import mousemaster.Licenses;
import mousemaster.Mousemaster;
import mousemaster.MousemasterApplication;
import mousemaster.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class MacosMain {

    static {
        System.setProperty("slf4j.internal.verbosity", "WARN");
    }

    private static final Logger logger = LoggerFactory.getLogger(MacosMain.class);

    /** Nothing can pass a native image -Djava.library.path, so point it at the bundle. */
    private static void useBundledFrameworks() {
        String executable = ProcessHandle.current().info().command().orElse(null);
        if (executable == null)
            return;
        Path frameworks =
                Path.of(executable).resolveSibling("../Frameworks").normalize();
        if (!Files.isDirectory(frameworks))
            return;
        System.setProperty("java.library.path", frameworks.toString());
        System.setProperty("jna.library.path", frameworks.toString());
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        useBundledFrameworks();
        ApplicationOptions options = ApplicationOptions.parse(args);
        MousemasterApplication.setTempDirectory(options.tempDirectory());
        if (options.logLevel() != null)
            MousemasterApplication.setLogLevel(options.logLevel());
        if (options.logToFile())
            MousemasterApplication.enableLogToFile();
        String version;
        String commitId;
        try (InputStream versionInputStream = MacosMain.class.getClassLoader()
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
        if (options.showLicenses()) {
            Licenses.print();
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
                options.simulationKeyEvents());
        logger.info("mousemaster v" + version + " (" + commitId + ")");
        if (platform == null)
            return;
        try {
            new Mousemaster(options.configurationPath(), platform,
                    options.preWarmHints()).run();
        } catch (Throwable e) {
            MousemasterApplication.shutdownAfterException(e, platform, false,
                    options.pauseOnError());
        }
    }

    /** ignoreInjectedEvents is unused: the grab reads the device, so a posted event never arrives. */
    private static Platform createPlatform(boolean multipleInstancesAllowed,
                                           boolean keyRegurgitationEnabled,
                                           boolean pauseOnError, String simulationKeyEvents) {
        try {
            return new MacosPlatform(multipleInstancesAllowed, keyRegurgitationEnabled,
                    null, simulationKeyEvents);
        } catch (Exception e) {
            MousemasterApplication.shutdownAfterException(e, null, false, pauseOnError);
        }
        return null;
    }

}
