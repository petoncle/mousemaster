package mousemaster.renderer;

import io.qt.core.Qt;
import io.qt.gui.QImage;
import io.qt.widgets.QApplication;
import mousemaster.Configuration;
import mousemaster.ConfigurationParser;
import mousemaster.Hint;
import mousemaster.HintMesh;
import mousemaster.Key;
import mousemaster.KeyboardLayout;
import mousemaster.Mousemaster;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** A grid shown again from its cached image still knows where its boxes are, after another
 *  mesh was shown in between. */
class HintBoxRectangleTest {

    private static final Screen screen = new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1);
    private static final Zoom zoom = new Zoom(1, screen.rectangle().center(), screen.rectangle());

    private static boolean qtAvailable;

    @BeforeAll
    static void initializeQt() {
        MousemasterApplication.tempDirectory =
                System.getProperty("java.io.tmpdir") + "/mousemaster-hint-box-rectangle-test";
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

    private static HintMesh hintMesh(boolean grid) throws IOException {
        Configuration configuration = ConfigurationParser.parse(
                PropertiesReader.readPropertiesFile(new BufferedReader(new StringReader("""
                        idle-mode.to.hint-mode=+u
                        hint-mode.to.idle-mode=+esc
                        hint-mode.overlay.selection-keys=a b c d
                        """))),
                KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty"));
        List<Hint> hints = List.of("a", "b", "c", "d").stream().map(keyName -> {
            int index = "abcd".indexOf(keyName);
            Rectangle cell = new Rectangle(200 + index * 100, grid ? 200 : 400, 100, 60);
            return new Hint(cell.x() + 50, cell.y() + 30, grid ? 100 : -1, grid ? 60 : -1,
                    List.of(Key.ofName(keyName)), cell);
        }).toList();
        return new HintMesh.HintMeshBuilder()
                .visible(true)
                .hints(hints)
                .styleByFilter(configuration.modeMap().get("hint-mode").hintMesh()
                                            .styleByFilter())
                .build();
    }

    private static void show(HintMeshRenderer hintMeshRenderer, HintMesh hintMesh) {
        hintMeshRenderer.setHintMesh(hintMesh, zoom, false, Set.of(screen));
        for (int frame = 0; frame < 10 || hintMeshRenderer.buildPending(); frame++) {
            QApplication.processEvents();
            hintMeshRenderer.update(Mousemaster.FRAME_INTERVAL);
            hintMeshRenderer.runPendingWork();
        }
    }

    @Test
    void aGridShownFromItsCachedImageKeepsItsBoxes() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        HintMeshRenderer hintMeshRenderer = new HintMeshRenderer(() -> {
            TransparentWindow window = new TransparentWindow();
            window.setAttribute(Qt.WidgetAttribute.WA_DontShowOnScreen);
            return window;
        }, null, () -> {
        });
        HintMesh grid = hintMesh(true);
        show(hintMeshRenderer, grid);
        assertNotNull(hintMeshRenderer.hintBoxRectangle(grid.hints().getFirst()));
        show(hintMeshRenderer, hintMesh(false));
        show(hintMeshRenderer, grid);
        assertNotNull(hintMeshRenderer.hintBoxRectangle(grid.hints().getFirst()));
        hintMeshRenderer.hideHintMesh();
        for (TransparentWindow window : hintMeshRenderer.windows())
            window.disposeLater();
    }
}
