package mousemaster.qt;

import io.qt.gui.QColor;
import io.qt.gui.QFont;
import io.qt.gui.QFontMetrics;

public record QtFontStyle(QFont font, QFontMetrics metrics,
                          QColor color,
                          QColor outlineColor, int outlineThickness,
                          QColor shadowColor, int shadowStackCount,
                          double shadowBlurRadius,
                          double shadowHorizontalOffset, double shadowVerticalOffset) {

    public boolean hasTransparency() {
        if (outlineThickness != 0 &&
            outlineColor.alpha() < 255 &&
            // 0 means outline will not be rendered.
            outlineColor.alpha() != 0)
            return true;
        return color.alpha() < 255 && color.alpha() != 0;
    }
}
