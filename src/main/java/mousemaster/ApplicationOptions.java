package mousemaster;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;
import java.util.stream.Stream;

public record ApplicationOptions(String tempDirectory,
                                 String logLevel,
                                 boolean logToFile,
                                 boolean pauseOnError,
                                 boolean showVersion,
                                 Path configurationPath,
                                 boolean multipleInstancesAllowed,
                                 boolean keyRegurgitationEnabled,
                                 boolean graalvmAgentRun) {

    public static ApplicationOptions parse(String[] args) {
        return new ApplicationOptions(
                stringArg(args, "--temp-directory=", null),
                stringArg(args, "--log-level=", null),
                booleanArg(args, "--log-to-file=", false),
                booleanArg(args, "--pause-on-error=", true),
                Stream.of(args).anyMatch(Predicate.isEqual("--version")),
                Paths.get(stringArg(args, "--configuration-file=",
                        "mousemaster.properties")),
                booleanArg(args, "--multiple-instances-allowed=", false),
                booleanArg(args, "--key-regurgitation-enabled=", true),
                Stream.of(args).anyMatch(Predicate.isEqual("--graalvm-agent-run"))
        );
    }

    private static String stringArg(String[] args, String prefix, String defaultValue) {
        return Stream.of(args)
                     .filter(arg -> arg.startsWith(prefix))
                     .map(arg -> arg.substring(prefix.length()))
                     .findFirst()
                     .orElse(defaultValue);
    }

    private static boolean booleanArg(String[] args, String prefix, boolean defaultValue) {
        return Stream.of(args)
                     .filter(arg -> arg.startsWith(prefix))
                     .map(arg -> arg.substring(prefix.length()))
                     .findFirst()
                     .map(Boolean::parseBoolean)
                     .orElse(defaultValue);
    }

}
