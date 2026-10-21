package mousemaster;

import mousemaster.platform.DesktopCapture;
import mousemaster.platform.UiAutomation.UiElement;
import org.junit.jupiter.api.Test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VisionTest {

    private static final int density = Vision.densities;
    private static final int defaultDensity = 6;

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

    private static List<UiElement> elements(BufferedImage image, int density) {
        return elements(image, 1, density);
    }

    private static List<UiElement> elements(BufferedImage image, int scale, int density) {
        byte[] rgb = new byte[width * height * 3];
        for (int y = 0, i = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                rgb[i++] = (byte) (pixel >> 16);
                rgb[i++] = (byte) (pixel >> 8);
                rgb[i++] = (byte) pixel;
            }
        return new Vision(null).findElements(
                new DesktopCapture(new Rectangle(0, 0, width * scale, height * scale), rgb,
                        width, height), scale, density);
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
        List<UiElement> elements = elements(image, density);
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
        assertEquals(1, within(elements(image, density), 160, 130, 240, 170));
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
        List<UiElement> elements = elements(image, density);
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
        assertTrue(within(elements(image, density), 44, 96, 106, 120) >= 1);
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
        List<UiElement> elements = elements(image, density);
        assertTrue(within(elements, 40, 60, 360, 85) >= 1, "first line");
        assertTrue(within(elements, 40, 120, 360, 145) >= 1, "second line");
        assertTrue(within(elements, 40, 180, 360, 205) >= 1, "third line");
        assertTrue(elements.size() <= 30,
                "three lines of text, not sixty glyphs: " + elements.size());
    }

    @Test
    public void frameOnePixelWideIsScannedAtEveryDensity() {
        for (int density = 1; density <= Vision.densities; density++) {
            int scanned = density;
            assertDoesNotThrow(() -> new Vision(null).findElements(
                    new DesktopCapture(new Rectangle(0, 0, 1, 5), new byte[15], 1, 5), 1,
                    scanned), "density " + scanned);
        }
    }

    @Test
    public void linkInASentenceIsHintedApart() {
        BufferedImage image = screen(Color.WHITE);
        Graphics2D pen = pen(image);
        FontMetrics metrics = pen.getFontMetrics();
        int linkX = 40 + metrics.stringWidth("Before you start, read the ");
        int linkRight = linkX + metrics.stringWidth("guide");
        pen.setColor(new Color(0x202122));
        pen.drawString("Before you start, read the ", 40, 150);
        pen.setColor(new Color(0x3366CC));
        pen.drawString("guide", linkX, 150);
        pen.dispose();
        assertEquals(1,
                within(elements(image, defaultDensity), linkX, 130, linkRight, 160));
    }

    @Test
    public void lineOnTheEdgeOfTheScreenIsScannedAtEveryDensity() {
        BufferedImage image = screen(Color.WHITE);
        Graphics2D pen = pen(image);
        pen.setColor(new Color(0x333333));
        pen.drawLine(0, 100, 0, 200);
        pen.drawLine(width - 1, 100, width - 1, 200);
        pen.dispose();
        for (int density = 1; density <= Vision.densities; density++) {
            int scanned = density;
            assertDoesNotThrow(() -> elements(image, scanned), "density " + scanned);
        }
    }

    @Test
    public void firstDensityHintsAFaintButtonButNotTheTextBesideIt() {
        BufferedImage image = screen(Color.BLACK);
        Graphics2D pen = pen(image);
        pen.setColor(new Color(0x0E100E));
        pen.drawRoundRect(40, 100, 90, 32, 12, 12);
        pen.setColor(new Color(0xF0F6FC));
        pen.drawString("Sign in", 60, 122);
        pen.drawString("Assigned to me", 180, 122);
        pen.dispose();
        List<UiElement> elements = elements(image, 1);
        assertEquals(1, within(elements, 40, 100, 130, 132), "the button");
        assertEquals(0, within(elements, 180, 100, 360, 132), "the text");
    }

    @Test
    public void capturedWordAloneIsLeftOutAtTheFirstDensity() {
        BufferedImage drawn = new BufferedImage(3 * width, 3 * height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D pen = pen(drawn);
        pen.setColor(new Color(0xF6F6EF));
        pen.fillRect(0, 0, 3 * width, 3 * height);
        pen.setColor(new Color(0x828282));
        pen.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 39));
        pen.drawString("past", 300, 450);
        pen.dispose();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.getGraphics().drawImage(
                drawn.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING), 0, 0,
                null);
        assertEquals(0, elements(image, 3, 1).size());
    }

    @Test
    public void darkThemeOutlineIsInk() {
        BufferedImage image = screen(new Color(0x17191A));
        Graphics2D pen = pen(image);
        pen.setColor(new Color(0x3E3E3F));
        pen.setStroke(new BasicStroke(2));
        pen.drawRoundRect(193, 143, 14, 14, 4, 4);
        pen.dispose();
        assertEquals(1, within(elements(image, density), 190, 140, 210, 160));
    }

    @Test
    public void rowMenuOfThreeDotsIsHinted() {
        BufferedImage image = screen(Color.WHITE);
        Graphics2D pen = pen(image);
        pen.setColor(new Color(0x333333));
        for (int dot = 0; dot < 3; dot++)
            pen.fillOval(190 + dot * 4, 150, 2, 2);
        pen.dispose();
        assertEquals(1, within(elements(image, defaultDensity), 185, 140, 210, 160));
    }

    @Test
    public void greyTextBesideBlackIsHintedApart() {
        BufferedImage image = screen(new Color(0xF6F6EF));
        Graphics2D pen = pen(image);
        FontMetrics metrics = pen.getFontMetrics();
        int domainX = 40 + metrics.stringWidth("Black hole singularity ");
        int domainRight = domainX + metrics.stringWidth("(arxiv.org)");
        pen.setColor(Color.BLACK);
        pen.drawString("Black hole singularity ", 40, 150);
        pen.setColor(new Color(0x828282));
        pen.drawString("(arxiv.org)", domainX, 150);
        pen.dispose();
        assertEquals(1,
                within(elements(image, defaultDensity), domainX, 130, domainRight, 160));
    }
}
