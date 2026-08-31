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
                        hint-mode.indicator.color=""" + indicatorColor + "\n")));
        return ConfigurationParser.parse(lines,
                KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty"));
    }

    private Color indicatorColor(Configuration configuration) {
        return configuration.modeMap().get("hint-mode").indicator().color();
    }

    @Test
    void theKeywordDrawsWhateverTheSelectedBoxWasFilledWith() throws IOException {
        Color color = indicatorColor(parse(Color.lastSelectedHintBoxColor));
        assertEquals(new Color.LastSelectedHintBoxColor(), color);
        assertEquals("#123456", color.hexColor("#123456"));
    }

    @Test
    void aHexColorIgnoresTheSelectedBox() throws IOException {
        Color color = indicatorColor(parse("#FF8800"));
        assertEquals(new Color.HexColor("#FF8800"), color);
        assertEquals("#FF8800", color.hexColor("#123456"));
    }

    @Test
    void aGradientIsSampledAtTheCenterOfTheBox() {
        HintGradientColor color = HintGradientColor.parse("left-to-right #FF0000 #0000FF");
        assertEquals(color.rgbAt(0.5), color.rgbAt(new Rectangle(0, 0, 100, 100), 50, 50));
    }

    @Test
    void anAcrossHintGradientStartsAtTheCenterOfTheBox() {
        HintGradientColor color =
                HintGradientColor.parse("across-hint center-to-edge #FF0000 #0000FF");
        assertEquals(0xFF0000,
                color.rgbAt(HintGradientColor.unitArea, 0.5, 0.5));
    }
}
