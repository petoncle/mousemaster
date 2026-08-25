package mousemaster;

import mousemaster.platform.DesktopCapture;
import mousemaster.platform.UiAutomation.UiElement;
import org.junit.jupiter.api.Test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VisionTest {

    private static final int width = 400, height = 300;

    private static BufferedImage screen(Color background) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D pen = pen(image);
        pen.setColor(background);
        pen.fillRect(0, 0, width, height);
        pen.dispose();
        return image;
    }

    private static Graphics2D pen(BufferedImage image) {
        Graphics2D pen = image.createGraphics();
        pen.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        pen.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        pen.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        return pen;
    }

    private static List<UiElement> elements(BufferedImage image) {
        byte[] rgb = new byte[width * height * 3];
        for (int y = 0, i = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                rgb[i++] = (byte) (pixel >> 16);
                rgb[i++] = (byte) (pixel >> 8);
                rgb[i++] = (byte) pixel;
            }
        return new Vision(null).findElements(
                new DesktopCapture(new Rectangle(0, 0, width, height), rgb, width,
                        height), 1);
    }

    private static long within(List<UiElement> elements, int x, int y, int right,
                               int bottom) {
        return elements.stream()
                       .filter(element -> element.centerX() >= x
                                          && element.centerX() <= right
                                          && element.centerY() >= y
                                          && element.centerY() <= bottom)
                       .count();
    }

    @Test
    public void flatIconIsHintedOnceAtItsCentre() {
        BufferedImage image = screen(new Color(0x2E3035));
        Graphics2D pen = pen(image);
        pen.setColor(new Color(0x5865F2));
        pen.fillRoundRect(150, 100, 100, 100, 30, 30);
        pen.dispose();
        List<UiElement> elements = elements(image);
        assertEquals(1, elements.size(), "a flat icon is one thing to click");
        assertEquals(1, within(elements, 185, 135, 215, 165),
                "the hint belongs at the centre, not on the corners of the outline");
    }

    @Test
    public void colourThatKeepsTheBrightnessIsContent() {
        BufferedImage image = screen(new Color(0x767676));
        Graphics2D pen = pen(image);
        pen.setColor(new Color(0x0078D4));
        pen.fillRect(160, 130, 80, 40);
        pen.dispose();
        assertEquals(1, within(elements(image), 160, 130, 240, 170));
    }

    @Test
    public void borderedPanelSplitsIntoItsControls() {
        BufferedImage image = screen(Color.WHITE);
        Graphics2D pen = pen(image);
        pen.setColor(new Color(0x3A76D8));
        pen.setStroke(new BasicStroke(1));
        pen.drawRoundRect(40, 100, 320, 100, 8, 8);
        pen.setColor(new Color(0x333333));
        pen.drawString("Save", 80, 158);
        pen.drawString("Cancel", 180, 158);
        pen.drawString("Help", 290, 158);
        pen.dispose();
        List<UiElement> elements = elements(image);
        assertTrue(within(elements, 70, 140, 130, 170) >= 1, "Save");
        assertTrue(within(elements, 170, 140, 240, 170) >= 1, "Cancel");
        assertTrue(within(elements, 280, 140, 340, 170) >= 1, "Help");
    }

    @Test
    public void smallFilledButtonBesideTextIsHinted() {
        BufferedImage image = screen(Color.WHITE);
        Graphics2D pen = pen(image);
        pen.setColor(new Color(0xFF6766));
        pen.fillRoundRect(44, 100, 62, 15, 7, 7);
        pen.setColor(Color.WHITE);
        pen.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        pen.drawString("LEAVE", 54, 111);
        pen.setColor(new Color(0x333333));
        pen.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        pen.drawString("Show my flair on this subreddit", 44, 130);
        pen.dispose();
        assertTrue(within(elements(image), 44, 96, 106, 120) >= 1);
    }

    @Test
    public void textIsNotOneHintPerGlyph() {
        BufferedImage image = screen(Color.WHITE);
        Graphics2D pen = pen(image);
        pen.setColor(new Color(0x222222));
        pen.drawString("The quick brown fox", 40, 80);
        pen.drawString("jumps over the lazy dog", 40, 140);
        pen.drawString("Pack my box with jugs", 40, 200);
        pen.dispose();
        List<UiElement> elements = elements(image);
        assertTrue(within(elements, 40, 60, 360, 85) >= 1, "first line");
        assertTrue(within(elements, 40, 120, 360, 145) >= 1, "second line");
        assertTrue(within(elements, 40, 180, 360, 205) >= 1, "third line");
        assertTrue(elements.size() <= 30,
                "three lines of text, not sixty glyphs: " + elements.size());
    }
}
