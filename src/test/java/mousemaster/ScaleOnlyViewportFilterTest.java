package mousemaster;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScaleOnlyViewportFilterTest {

    private static double boxBorderRadius(Configuration configuration, int width,
                                          int height, double scale) {
        return configuration.modeMap()
                            .get("hint-mode")
                            .hintMesh()
                            .styleByFilter()
                            .get(new ViewportFilter.FixedViewportFilter(
                                    new Viewport(width, height, scale)))
                            .boxBorderRadius();
    }

    @Test
    void scaleOnlyFilterAppliesToAnyResolution() throws IOException {
        List<String> lines = PropertiesReader.readPropertiesFile(new BufferedReader(
                new StringReader("""
                        idle-mode.to.hint-mode=+u
                        hint-mode.to.idle-mode=+esc
                        hint-mode.hint.selection-keys=a b c d
                        hint-mode.hint.box-border-radius=1
                        hint-mode.hint.box-border-radius.300%=3
                        hint-mode.hint.box-border-radius.1920x1080=5
                        """)));
        Configuration configuration = ConfigurationParser.parse(lines,
                KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty"));
        assertEquals(3, boxBorderRadius(configuration, 3840, 2160, 3));
        assertEquals(5, boxBorderRadius(configuration, 1920, 1080, 3));
        assertEquals(1, boxBorderRadius(configuration, 2560, 1440, 1));
    }
}
