package mousemaster;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformBoundaryTest {

    private static final Map<String, List<String>> forbiddenPlatformReferencesByPackage =
            Map.of(
                    "windows", List.of(
                            "import com.sun.jna.platform.win32",
                            "import mousemaster.platform.windows",
                            "mousemaster.platform.windows."
                    ),
                    "macos", List.of(
                            "import mousemaster.platform.macos",
                            "mousemaster.platform.macos."
                    )
            );

    @Test
    void platformImplementationTypesStayInsideTheirPlatformPackage() throws IOException {
        Path sourceRoot = Path.of("src/main/java").toAbsolutePath();
        List<Violation> violations;
        try (var paths = Files.walk(sourceRoot)) {
            violations = paths.filter(path -> path.toString().endsWith(".java"))
                              .flatMap(path -> platformBoundaryViolations(sourceRoot, path).stream())
                              .sorted(Comparator.comparing(violation -> violation.path().toString()))
                              .toList();
        }

        assertTrue(violations.isEmpty(), "Platform implementation dependencies outside their owning package: " +
                                          violations);
    }

    private static List<Violation> platformBoundaryViolations(Path sourceRoot, Path path) {
        try {
            Path relativePath = sourceRoot.relativize(path);
            String source = Files.readString(path);
            return forbiddenPlatformReferencesByPackage.entrySet()
                                                       .stream()
                                                       .filter(entry -> !relativePath.startsWith(
                                                               Path.of("mousemaster/platform/" +
                                                                       entry.getKey())))
                                                       .flatMap(entry -> entry.getValue()
                                                                              .stream()
                                                                              .filter(source::contains)
                                                                              .map(reference ->
                                                                                      new Violation(
                                                                                              relativePath,
                                                                                              entry.getKey(),
                                                                                              reference)))
                                                       .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private record Violation(Path path, String platform, String reference) {
    }
}
