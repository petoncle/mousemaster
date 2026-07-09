package mousemaster.qt;

import io.qt.core.Qt;
import io.qt.gui.*;
import mousemaster.Hint;
import mousemaster.HintMesh;
import mousemaster.Key;

import java.util.List;

/**
 * Platform-agnostic Qt window for displaying hint mesh overlay.
 * Renders letter labels at hint positions.
 *
 * NOTE: This is a shared, platform-agnostic implementation currently used by Linux.
 * Windows has its own more sophisticated implementation using QLabel widgets, pixmap caching,
 * and animations (see WindowsOverlay.HintMeshWindow record and ClearBackgroundQLabel class).
 * TODO: Consider whether to:
 *       1. Enhance this shared version with Windows' advanced features (animations, caching)
 *          and migrate Windows to use it, OR
 *       2. Keep this as a simple version for basic platforms and maintain Windows' advanced version
 *       The former would reduce code duplication but requires careful refactoring of Windows' complex
 *       hint rendering pipeline.
 */
public class HintMeshWindow extends TransparentWindow {

    private HintMesh hintMesh;

    public HintMeshWindow() {
        super();
    }

    public void setHintMesh(HintMesh hintMesh) {
        this.hintMesh = hintMesh;

        // Make window fullscreen to cover all hints
        setGeometry(0, 0, 1920, 1080);

        // Make window visible
        show();

        // Request repaint
        update();
    }

    public void clearHints() {
        this.hintMesh = null;
        hide();
    }

    @Override
    protected void paintEvent(QPaintEvent event) {
        super.paintEvent(event);

        if (hintMesh == null || !hintMesh.visible()) {
            return;
        }

        QPainter painter = new QPainter(this);

        // Set up font for hint labels
        QFont font = new QFont("Arial", 24);
        font.setBold(true);
        painter.setFont(font);

        // Draw each hint
        for (Hint hint : hintMesh.hints()) {
            // Get the label text from the key sequence
            String label = getHintLabel(hint.keySequence());

            // Calculate position (hint has centerX/centerY, we need top-left for text)
            int x = (int) (hint.centerX() - hint.cellWidth() / 2);
            int y = (int) (hint.centerY() - hint.cellHeight() / 2);
            int width = (int) hint.cellWidth();
            int height = (int) hint.cellHeight();

            // Draw background box
            QPen boxPen = new QPen(new QColor(255, 255, 255, 200));
            boxPen.setWidth(2);
            painter.setPen(boxPen);
            painter.setBrush(new QBrush(new QColor(0, 0, 0, 150)));
            painter.drawRoundedRect(x, y, width, height, 5, 5);

            // Draw label text
            QPen textPen = new QPen(new QColor(255, 255, 255));
            painter.setPen(textPen);
            painter.drawText(x, y, width, height,
                           Qt.AlignmentFlag.AlignCenter.value(), label);

            boxPen.dispose();
            textPen.dispose();
        }

        painter.end();
        painter.dispose();
        font.dispose();
    }

    private String getHintLabel(List<Key> keySequence) {
        if (keySequence.isEmpty()) {
            return "";
        }

        StringBuilder label = new StringBuilder();
        for (Key key : keySequence) {
            if (key.character() != null) {
                label.append(key.character());
            } else if (key.staticSingleCharacterName() != null) {
                label.append(key.staticSingleCharacterName());
            } else if (key.staticName() != null) {
                label.append(key.staticName());
            }
        }
        return label.toString();
    }
}
