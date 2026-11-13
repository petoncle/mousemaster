package mousemaster;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    @Test
    void everyOverlayStyleSetsOnlyTheLook() throws Exception {
        List<Path> files;
        try (Stream<Path> paths = Files.walk(Path.of("configuration/overlay-styles"))) {
            files = paths.filter(path -> path.toString().endsWith(".properties")).sorted().toList();
        }
        HintMeshConfiguration hintMeshWithoutStyle = hintMesh(List.of());
        for (Path file : files) {
            List<String> styleProperties = new ArrayList<>(List.of("hint-mode=" +
                    file.getFileName().toString().replaceFirst("^(.+)\\.properties$", "_$1-style-mode")));
            styleProperties.addAll(PropertiesReader.readPropertiesFile(Files.newBufferedReader(file)));
            HintMeshConfiguration hintMesh = hintMesh(styleProperties);
            String fileName = file.getFileName().toString();
            assertNotEquals(hintMeshWithoutStyle.styleByFilter(), hintMesh.styleByFilter(), fileName);
            assertEquals(hintMeshWithoutStyle.type(), hintMesh.type(), fileName);
            assertEquals(hintMeshWithoutStyle.keysByFilter(), hintMesh.keysByFilter(), fileName);
        }
    }

    private static HintMeshConfiguration hintMesh(List<String> styleProperties) {
        List<String> properties = new ArrayList<>(
                List.of("idle-mode.to.hint-mode=+a", "hint-mode.overlay.type=grid"));
        properties.addAll(styleProperties);
        return ConfigurationParser.parse(properties, KeyboardLayout.keyboardLayout("00000409", null))
                                  .modeMap().get("hint-mode").hintMesh();
    }
}
