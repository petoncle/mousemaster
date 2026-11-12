package mousemaster.renderer;

import io.qt.gui.QImage;
import io.qt.widgets.QApplication;
import mousemaster.Hint;
import mousemaster.HintMesh;
import mousemaster.Key;
import mousemaster.MousemasterApplication;
import mousemaster.Os;
import mousemaster.QtManager;
import mousemaster.Rectangle;
import mousemaster.Screen;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PrefixInBackgroundTest {

    private static final List<String> colorProperties =
            List.of("color", "outline-color", "shadow-color");

    private static final String gradient =
            "across-screen per-group left-to-right #FF0000 #0000FF";

    private static boolean qtAvailable;

    @BeforeAll
    static void initializeQt() {
        MousemasterApplication.tempDirectory =
                System.getProperty("java.io.tmpdir") + "/mousemaster-prefix-in-background-test";
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
    void aKeyInTheBackgroundTakesTheColorOfItsGroup() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        double screenScale = Os.macos ? 2 :
                QApplication.primaryScreen().logicalDotsPerInchX() / 96;
        int cellSize = (int) (200 * screenScale);
        List<Hint> hints = List.of(
                new Hint(cellSize / 2d, cellSize / 2d, cellSize, cellSize,
                        List.of(Key.ofName("a"), Key.ofName("c")), null),
                new Hint(cellSize * 1.5, cellSize / 2d, cellSize, cellSize,
                        List.of(Key.ofName("b"), Key.ofName("c")), null));
        Screen screen = new Screen(new Rectangle(0, 0, 2 * cellSize, cellSize), 96, screenScale);
        List<String> wrongColors = new ArrayList<>();
        for (String colorProperty : colorProperties) {
            List<Ink> inks = HintLabelAlignmentTest.drawn(properties(colorProperty),
                    new HintMesh.HintMeshBuilder().hints(hints).prefixLength(1), screen,
                    (image, hintMeshRenderer) -> hints.stream()
                                                      .map(hint -> ink(image, hint))
                                                      .toList());
            for (int hintIndex = 0; hintIndex < hints.size(); hintIndex++) {
                Ink ink = inks.get(hintIndex);
                if (ink.red() + ink.blue() == 0 || ink.red() > ink.blue() != (hintIndex == 0))
                    wrongColors.add(String.format("prefix-font-%s, group %s: %d red, %d blue",
                            colorProperty, hints.get(hintIndex).keySequence().getFirst(),
                            ink.red(), ink.blue()));
            }
        }
        assertTrue(wrongColors.isEmpty(), String.join("\n", wrongColors));
    }

    private static String properties(String gradientProperty) {
        return """
                idle-mode.to.hint-mode=+u
                hint-mode.to.idle-mode=+esc
                hint-mode.overlay.selection-keys=a b c
                hint-mode.overlay.font-opacity=0
                hint-mode.overlay.font-shadow-opacity=0
                hint-mode.overlay.box-opacity=0
                hint-mode.overlay.box-border-opacity=0
                hint-mode.overlay.box-shadow-opacity=0
                hint-mode.overlay.prefix-in-background=true
                hint-mode.overlay.prefix-font-size=60
                hint-mode.overlay.prefix-font-opacity=1
                hint-mode.overlay.prefix-font-outline-thickness=2
                hint-mode.overlay.prefix-font-outline-opacity=1
                hint-mode.overlay.prefix-font-shadow-opacity=1
                hint-mode.overlay.prefix-font-shadow-blur-radius=0
                hint-mode.overlay.prefix-font-shadow-horizontal-offset=4
                hint-mode.overlay.prefix-font-shadow-vertical-offset=4
                hint-mode.overlay.fade-enabled=false
                hint-mode.overlay.transition-enabled=false
                """ + colorProperties.stream()
                                     .map(colorProperty -> "hint-mode.overlay.prefix-font-" +
                                                           colorProperty + "=" +
                                                           (colorProperty.equals(gradientProperty) ?
                                                                   gradient : "#000000") + "\n")
                                     .collect(Collectors.joining());
    }

    private record Ink(int red, int blue) {
    }

    private static Ink ink(QImage image, Hint hint) {
        int red = 0;
        int blue = 0;
        for (int x = (int) (hint.centerX() - hint.cellWidth() / 2);
             x < hint.centerX() + hint.cellWidth() / 2; x++) {
            for (int y = (int) (hint.centerY() - hint.cellHeight() / 2);
                 y < hint.centerY() + hint.cellHeight() / 2; y++) {
                int pixel = image.pixel(x, y);
                if (pixel >>> 24 != 255)
                    continue;
                red += pixel >> 16 & 0xFF;
                blue += pixel & 0xFF;
            }
        }
        return new Ink(red, blue);
    }
}
