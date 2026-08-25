package mousemaster;

import mousemaster.platform.Overlay;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A grid of the active screen is pre-warmed for every screen, so a second monitor does not
 * build its own on first use.
 */
class PreWarmScreensTest {

    private static final Screen SCREEN_A =
            new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1.0);
    /** Same size and scale as A, to its right. */
    private static final Screen SCREEN_A_RIGHT =
            new Screen(new Rectangle(1920, 0, 1920, 1080), 96, 1.0);
    private static final Screen SCREEN_B =
            new Screen(new Rectangle(1920, 0, 3840, 2160), 96, 1.0);

    private List<HintMesh> preWarm(Screen... screens) throws Exception {
        List<String> properties;
        try (var reader = Files.newBufferedReader(Path.of("configuration/author.properties"),
                StandardCharsets.UTF_8)) {
            // Font names are validated through Qt, whose natives are absent here.
            properties = PropertiesReader.readPropertiesFile(reader).stream()
                                        .filter(line -> !line.contains("font-name"))
                                        .toList();
        }
        Configuration configuration = ConfigurationParser.parse(properties,
                KeyboardLayout.keyboardLayout("00000409", null));
        Set<Screen> screenSet = new LinkedHashSet<>(List.of(screens));
        ScreenManager screenManager = new ScreenManager(() -> screenSet);
        List<HintMesh> preWarmed = new ArrayList<>();
        Overlay overlay = (Overlay) Proxy.newProxyInstance(
                Overlay.class.getClassLoader(), new Class<?>[] {Overlay.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("preWarmHintMesh"))
                        preWarmed.add((HintMesh) args[0]);
                    return null;
                });
        HintManager hintManager =
                new HintManager(Map.of(), screenManager, null, overlay, null, null,
                        new KeyRedactor(KeyRedaction.NONE), null);
        hintManager.preWarmHintMeshes(configuration.modeMap());
        return preWarmed;
    }

    @Test
    void eachScreenGetsItsOwnMesh() throws Exception {
        List<HintMesh> one = preWarm(SCREEN_A);
        List<HintMesh> two = preWarm(SCREEN_A, SCREEN_B);
        assertFalse(one.isEmpty());
        assertEquals(2 * one.size(), two.size(),
                "a second, differently sized screen doubles the pre-warmed meshes");
    }

    @Test
    void identicalScreensShareTheirMeshes() throws Exception {
        List<HintMesh> one = preWarm(SCREEN_A);
        List<HintMesh> identical = preWarm(SCREEN_A, SCREEN_A_RIGHT);
        assertEquals(one.size() * 2, identical.size(),
                "screens of the same size still sit at different positions");
        assertEquals(identical.size(), Set.copyOf(identical).size(),
                "no mesh is pre-warmed twice");
    }
}
