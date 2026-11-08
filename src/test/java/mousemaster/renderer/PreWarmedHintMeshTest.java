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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** A pre-warmed grid keeps its image for its first show; the stand-in elements of a pre-warmed
 *  element mesh are never on screen, so its image is not kept. */
class PreWarmedHintMeshTest {

    private static final Screen screen = new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1);
    private static final Zoom zoom = new Zoom(1, screen.rectangle().center(), screen.rectangle());
    private static final List<String> keyNames =
            List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l");

    private static boolean qtAvailable;

    @BeforeAll
    static void initializeQt() {
        MousemasterApplication.tempDirectory =
                System.getProperty("java.io.tmpdir") + "/mousemaster-pre-warmed-hint-mesh-test";
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

    /** 144 hints of two keys in cells of 160x90, the cells of a grid or the elements of a UI. */
    private static HintMesh hintMesh(boolean grid) throws IOException {
        List<String> lines = PropertiesReader.readPropertiesFile(new BufferedReader(
                new StringReader("""
                        idle-mode.to.hint-mode=+u
                        hint-mode.to.idle-mode=+esc
                        hint-mode.overlay.selection-keys=a b c d e f g h i j k l
                        """)));
        Configuration configuration = ConfigurationParser.parse(lines,
                KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty"));
        List<Hint> hints = new ArrayList<>();
        for (int row = 0; row < keyNames.size(); row++)
            for (int column = 0; column < keyNames.size(); column++) {
                Rectangle cell = new Rectangle(column * 160, row * 90, 160, 90);
                hints.add(new Hint(cell.x() + 80, cell.y() + 45, grid ? 160 : -1, grid ? 90 : -1,
                        List.of(Key.ofName(keyNames.get(row)), Key.ofName(keyNames.get(column))),
                        cell));
            }
        return new HintMesh.HintMeshBuilder()
                .visible(true)
                .hints(hints)
                .styleByFilter(configuration.modeMap().get("hint-mode").hintMesh()
                                            .styleByFilter())
                .build();
    }

    private static boolean buildPendingAfterPreWarmedShow(boolean grid) throws IOException {
        HintMesh hintMesh = hintMesh(grid);
        HintMeshRenderer hintMeshRenderer = new HintMeshRenderer(TransparentWindow::new, () -> {
        });
        hintMeshRenderer.preWarmHintMesh(hintMesh, zoom, Set.of(screen));
        hintMeshRenderer.setHintMesh(hintMesh, zoom, false, Set.of(screen));
        boolean buildPending = hintMeshRenderer.buildPending();
        hintMeshRenderer.hideHintMesh();
        hintMeshRenderer.flushCache();
        return buildPending;
    }

    @Test
    void aPreWarmedGridShowsItsCachedImage() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        assertFalse(buildPendingAfterPreWarmedShow(true));
    }

    @Test
    void aPreWarmedElementMeshKeepsNoImage() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        assertTrue(buildPendingAfterPreWarmedShow(false));
    }
}
