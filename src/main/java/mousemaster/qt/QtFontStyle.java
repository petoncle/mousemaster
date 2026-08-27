package mousemaster.qt;

import io.qt.gui.QColor;
import io.qt.gui.QFont;
import io.qt.gui.QFontMetrics;
import io.qt.gui.QPainterPath;

public record QtFontStyle(QFont font, QFontMetrics metrics,
                          QColor color,
                          QColor outlineColor, int outlineThickness,
                          QColor shadowColor, int shadowStackCount,
                          double shadowBlurRadius,
                          double shadowHorizontalOffset, double shadowVerticalOffset) {

    /** Adds the text's glyph outline to {@code path}, its baseline origin at (x, y). */
    public void addTextPath(QPainterPath path, String text, double x, double y) {
        QtHintFont.addTextPath(path, metrics, text, x, y);
    }

    /** Whether this style puts no ink on its layer, so a shadow of it would be a shadow of
     *  nothing. */
    public boolean invisible() {
        return color.alpha() == 0 &&
               (outlineThickness == 0 || outlineColor.alpha() == 0);
    }

    public boolean hasTransparency() {
        if (outlineThickness != 0 &&
            outlineColor.alpha() < 255 &&
            // 0 means outline will not be rendered.
            outlineColor.alpha() != 0)
            return true;
        return color.alpha() < 255 && color.alpha() != 0;
    }
}
