package mousemaster;

import mousemaster.platform.DesktopCapture;
import mousemaster.platform.UiAutomation.UiElement;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VisionCorpusTest {

    private static final double scale = 3;
    private static final String[] screens =
            {"excalidraw", "github", "hn", "vscode", "wikipedia"};

    private record Target(int x, int y, int right, int bottom) {
    }

    private static InputStream resource(String name) {
        InputStream stream = VisionCorpusTest.class.getResourceAsStream("/vision/" + name);
        assertNotNull(stream, name);
        return stream;
    }

    private static List<Target> targets(String screen) throws Exception {
        List<Target> targets = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource(screen + ".tsv"), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                String[] parts = line.split("\t", -1);
                if (parts.length >= 4)
                    targets.add(new Target(Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                            Integer.parseInt(parts[3])));
            }
        }
        return targets;
    }

    private static List<UiElement> elements(String screen) throws Exception {
        BufferedImage source;
        try (InputStream stream = resource(screen + ".png")) {
            source = ImageIO.read(stream);
        }
        int width = (int) Math.ceil(source.getWidth() / scale);
        int height = (int) Math.ceil(source.getHeight() / scale);
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        scaled.getGraphics().drawImage(
                source.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING), 0, 0,
                null);
        byte[] rgb = new byte[width * height * 3];
        for (int y = 0, i = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                int pixel = scaled.getRGB(x, y);
                rgb[i++] = (byte) (pixel >> 16);
                rgb[i++] = (byte) (pixel >> 8);
                rgb[i++] = (byte) pixel;
            }
        return new Vision(null).findElements(new DesktopCapture(
                new Rectangle(0, 0, source.getWidth(), source.getHeight()), rgb, width,
                height), scale);
    }

    @Test
    public void everyScreenReachesMostOfWhatThePageCallsClickable() throws Exception {
        int allFound = 0, allTargets = 0, allElements = 0;
        StringBuilder report = new StringBuilder();
        for (String screen : screens) {
            List<UiElement> elements = elements(screen);
            List<Target> targets = targets(screen);
            int found = 0;
            for (Target target : targets)
                for (UiElement element : elements)
                    if (element.centerX() >= target.x() && element.centerX() <= target.right()
                        && element.centerY() >= target.y()
                        && element.centerY() <= target.bottom()) {
                        found++;
                        break;
                    }
            allFound += found;
            allTargets += targets.size();
            allElements += elements.size();
            report.append(String.format("%n  %-12s %3d/%-3d  elements %4d", screen, found,
                    targets.size(), elements.size()));
            assertTrue(found >= targets.size() * 0.7,
                    screen + " reached " + found + " of " + targets.size() + report);
        }
        assertTrue(allFound >= allTargets * 0.85,
                "reached " + allFound + " of " + allTargets + report);
        assertTrue(allElements <= 1700,
                "hints have multiplied: " + allElements + report);
    }
}
