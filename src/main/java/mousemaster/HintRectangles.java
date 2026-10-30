package mousemaster;

/**
 * The rectangles of the hint at a position: its cell on the desktop, and the rectangle of the box
 * the overlay draws for it on the screen, null when it draws none. A hint without a cell has an
 * empty cell on its center.
 */
public record HintRectangles(Rectangle cell, Rectangle box) {
}
