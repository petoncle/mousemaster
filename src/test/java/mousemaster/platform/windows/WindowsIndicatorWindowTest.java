package mousemaster.platform.windows;

import mousemaster.Rectangle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowsIndicatorWindowTest {

    private static final Rectangle SCREEN = new Rectangle(0, 0, 3840, 2160);

    @Test
    void theWindowGrowsByAQuarterOfTheLargerSideOfItsImages() {
        assertEquals(new Rectangle(900, 900, 600, 400),
                WindowsIndicatorWindow.grownRectangle(new Rectangle(1000, 1000, 400, 200),
                        List.of(SCREEN)));
    }

    @Test
    void theWindowStaysInsideTheScreens() {
        assertEquals(new Rectangle(0, 0, 250, 250),
                WindowsIndicatorWindow.grownRectangle(new Rectangle(0, 0, 200, 200),
                        List.of(SCREEN)));
    }

    @Test
    void theWindowLeavesARowOfAScreenItsImagesDoNotCover() {
        assertEquals(new Rectangle(0, 1, 3840, 2159),
                WindowsIndicatorWindow.grownRectangle(new Rectangle(0, 100, 3840, 2060),
                        List.of(SCREEN)));
    }

    @Test
    void theWindowCoversAScreenItsImagesCover() {
        assertEquals(SCREEN, WindowsIndicatorWindow.grownRectangle(SCREEN, List.of(SCREEN)));
    }

}
