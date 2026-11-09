package mousemaster.renderer;

import io.qt.gui.QImage;
import mousemaster.Configuration;
import mousemaster.ConfigurationParser;
import mousemaster.Hint;
import mousemaster.HintMesh;
import mousemaster.Key;
import mousemaster.KeyboardLayout;
import mousemaster.MousemasterApplication;
import mousemaster.PropertiesReader;
import mousemaster.QtManager;
import mousemaster.Rectangle;
import mousemaster.Screen;
import mousemaster.Zoom;
import mousemaster.qt.TransparentWindow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SavedHintTest {

    private static boolean qtAvailable;

    @BeforeAll
    static void initializeQt() {
        MousemasterApplication.tempDirectory =
                System.getProperty("java.io.tmpdir") + "/mousemaster-saved-hint-test";
        try {
            QtManager.initialize();
        } catch (Throwable alreadyInitializedByAnotherTest) {
        }
        try {
            new QImage(1, 1, QImage.Format.Format_ARGB32_Premultiplied).dispose();
            qtAvailable = true;
        } catch (Throwable e) {
            qtAvailable = false;
        }
    }

    @Test
    void aSavedHintStaysWhenTheSelectedKeysHideIt() {
        Hint savedHint = new Hint(150, 120, -1, -1, List.of(Key.ofName("b")),
                new Rectangle(100, 100, 100, 40));
        Hint hint = new Hint(450, 120, -1, -1, List.of(Key.ofName("c")),
                new Rectangle(400, 100, 100, 40));
        HintMesh hintMesh = new HintMesh.HintMeshBuilder()
                .hints(List.of(savedHint, hint))
                .selectedKeySequence(List.of(Key.ofName("a")))
                .savedHints(List.of(savedHint))
                .build();
        assertTrue(HintMeshRenderer.visible(savedHint, hintMesh));
        assertFalse(HintMeshRenderer.visible(hint, hintMesh));
    }

    /** A saved box color that is not set is the color of the box. */
    @Test
    void hintsWithoutSavedBoxColorsAreDrawn() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        List<String> lines = PropertiesReader.readPropertiesFile(new BufferedReader(
                new StringReader("""
                        idle-mode.to.hint-mode=+u
                        hint-mode.to.idle-mode=+esc
                        hint-mode.overlay.selection-keys=a b c d
                        """)));
        Configuration configuration = ConfigurationParser.parse(lines,
                KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty"));
        HintMesh hintMesh = new HintMesh.HintMeshBuilder()
                .visible(true)
                .hints(List.of(new Hint(150, 120, -1, -1, List.of(Key.ofName("a")),
                        new Rectangle(100, 100, 100, 40))))
                .styleByFilter(configuration.modeMap().get("hint-mode").hintMesh()
                                            .styleByFilter())
                .build();
        Screen screen = new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1);
        HintMeshRenderer hintMeshRenderer = new HintMeshRenderer(TransparentWindow::new, null, () -> {
        });
        hintMeshRenderer.preWarmHintMesh(hintMesh,
                new Zoom(1, screen.rectangle().center(), screen.rectangle()), Set.of(screen));
        assertFalse(hintMeshRenderer.windows().isEmpty());
    }
}
