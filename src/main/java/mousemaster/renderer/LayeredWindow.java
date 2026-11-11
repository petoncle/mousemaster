package mousemaster.renderer;

import io.qt.gui.QPainter;
import mousemaster.Rectangle;

import java.util.function.BiConsumer;

/** The window under a hint mesh window that the moved hotspots, and what is under them, are
 *  drawn in, so that they move without the hint mesh window being painted again. A mesh that is
 *  not a grid is drawn there whole once its hotspots moved. */
public interface LayeredWindow {

    /** Paints the dirty part of the window at rectangle, both in screen pixels: paintArea paints
     *  in the coordinates of the window, given the area to paint. */
    void draw(Rectangle rectangle, Rectangle dirty, BiConsumer<QPainter, Rectangle> paintArea);

    void clear();

    void setOpacity(double opacity);

}
