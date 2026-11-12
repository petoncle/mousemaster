package mousemaster;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LastSelectedHintBoxColorTest {

    private Configuration parse(String indicatorColor) throws IOException {
        List<String> lines = PropertiesReader.readPropertiesFile(new BufferedReader(
                new StringReader("""
                        idle-mode.to.hint-mode=+u
                        hint-mode.to.idle-mode=+esc
                        hint-mode.hint.selection-keys=a b c d
                        hint-mode.indicator.fill-color=""" + indicatorColor + "\n")));
        return ConfigurationParser.parse(lines,
                KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty"));
    }

    private Color indicatorColor(Configuration configuration) {
        return configuration.modeMap().get("hint-mode").indicator().layerByName().get("indicator").fillColor();
    }

    private static final Color.LastSelectedHintColors lastSelectedHintColors =
            new Color.LastSelectedHintColors(
                    GradientColor.parse("across-screen per-pixel left-to-right #FF0000 #0000FF"),
                    GradientColor.parse("#123456"));

    @Test
    void theKeywordDrawsTheColorTheSelectedBoxWasFilledWith() throws IOException {
        Color color = indicatorColor(parse(Color.lastSelectedHintBoxColor));
        assertEquals(new Color.LastSelectedHintBoxColor(), color);
        assertEquals(lastSelectedHintColors.boxColor(),
                color.gradientColor(lastSelectedHintColors));
    }

    @Test
    void theCenterKeywordDrawsTheColorAtTheCenterOfTheSelectedBox() throws IOException {
        Color color = indicatorColor(parse(Color.lastSelectedHintBoxCenterColor));
        assertEquals(new Color.LastSelectedHintBoxCenterColor(), color);
        assertEquals(GradientColor.parse("#123456"), color.gradientColor(lastSelectedHintColors));
    }

    @Test
    void aHexColorIgnoresTheSelectedBox() throws IOException {
        Color color = indicatorColor(parse("#FF8800"));
        assertEquals(GradientColor.parse("#FF8800"), color);
        assertEquals(GradientColor.parse("#FF8800"), color.gradientColor(lastSelectedHintColors));
    }

    @Test
    void aGradientIsSampledAtTheCenterOfTheBox() {
        GradientColor color = GradientColor.parse("left-to-right #FF0000 #0000FF");
        assertEquals(color.rgbAt(0.5), color.rgbAt(new Rectangle(0, 0, 100, 100), 50, 50));
    }

    @Test
    void anAcrossGradientStartsAtTheCenterOfTheBox() {
        GradientColor color =
                GradientColor.parse("across-box center-to-edge #FF0000 #0000FF");
        assertEquals(0xFF0000,
                color.rgbAt(GradientColor.unitArea, 0.5, 0.5));
    }
}
