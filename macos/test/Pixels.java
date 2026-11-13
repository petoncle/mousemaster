import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Finds the pixels of a saturated color in a capture, for run-two-screens.sh. A color is a class
 * of channels, not an exact value, because a capture can go through a color profile.
 *
 *   java Pixels.java box <png> <color> [x y width height]
 *       prints "count minX minY maxX maxY", or "0" if no pixel matches
 *   java Pixels.java runs <png> <color> row|column <index>
 *       prints the start and the length of each run of the color along that row or column
 *
 * With the environment variable BEFORE set to a capture of the same screen, a pixel that has the
 * color there too does not match: the desktop can hold the colors the checks look for.
 */
public class Pixels {

    private static BufferedImage before;

    public static void main(String[] args) throws Exception {
        BufferedImage image = ImageIO.read(new File(args[1]));
        String color = args[2];
        String beforeFile = System.getenv("BEFORE");
        if (beforeFile != null && !beforeFile.isEmpty())
            before = ImageIO.read(new File(beforeFile));
        if (args[0].equals("box")) {
            int x = args.length > 3 ? Integer.parseInt(args[3]) : 0;
            int y = args.length > 3 ? Integer.parseInt(args[4]) : 0;
            int width = args.length > 3 ? Integer.parseInt(args[5]) : image.getWidth();
            int height = args.length > 3 ? Integer.parseInt(args[6]) : image.getHeight();
            int count = 0, minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
            for (int py = Math.max(0, y); py < Math.min(image.getHeight(), y + height); py++)
                for (int px = Math.max(0, x); px < Math.min(image.getWidth(), x + width); px++)
                    if (matches(image, px, py, color)) {
                        count++;
                        minX = Math.min(minX, px);
                        minY = Math.min(minY, py);
                        maxX = Math.max(maxX, px);
                        maxY = Math.max(maxY, py);
                    }
            System.out.println(count == 0 ? "0" :
                    count + " " + minX + " " + minY + " " + maxX + " " + maxY);
        }
        else {
            boolean row = args[3].equals("row");
            int index = Integer.parseInt(args[4]);
            int length = row ? image.getWidth() : image.getHeight();
            StringBuilder runs = new StringBuilder();
            int start = -1;
            for (int i = 0; i <= length; i++) {
                boolean match = i < length &&
                                (row ? matches(image, i, index, color) : matches(image, index, i, color));
                if (match && start == -1)
                    start = i;
                else if (!match && start != -1) {
                    runs.append(start).append(':').append(i - start).append(' ');
                    start = -1;
                }
            }
            System.out.println(runs.toString().trim());
        }
    }

    private static boolean matches(BufferedImage image, int x, int y, String color) {
        return matches(image.getRGB(x, y), color) &&
               (before == null || !matches(before.getRGB(x, y), color));
    }

    private static boolean matches(int rgb, String color) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        boolean rOn = r > 190, gOn = g > 190, bOn = b > 190;
        boolean rOff = r < 70, gOff = g < 70, bOff = b < 70;
        return switch (color) {
            case "green" -> rOff && gOn && bOff;
            case "yellow" -> rOn && gOn && bOff;
            case "magenta" -> rOn && gOff && bOn;
            case "cyan" -> rOff && gOn && bOn;
            case "blue" -> rOff && gOff && bOn;
            default -> throw new IllegalArgumentException(color);
        };
    }

}
