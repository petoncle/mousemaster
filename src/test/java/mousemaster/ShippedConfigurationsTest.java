package mousemaster;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ShippedConfigurationsTest {

    @Test
    void everyShippedConfigurationParses() throws Exception {
        List<Path> files;
        try (Stream<Path> paths = Files.list(Path.of("configuration"))) {
            files = paths.filter(path -> path.toString().endsWith(".properties")).sorted().toList();
        }
        for (Path file : files) {
            Configuration configuration = ConfigurationParser.parse(
                    PropertiesReader.readPropertiesFile(Files.newBufferedReader(file)),
                    KeyboardLayout.keyboardLayout("00000409", null));
            assertNotNull(configuration.modeMap(), file.getFileName().toString());
        }
    }
}
