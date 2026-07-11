package mousemaster.qt;

import io.qt.core.Qt;
import io.qt.gui.*;
import mousemaster.Hint;
import mousemaster.HintMesh;
import mousemaster.Key;

import java.util.List;

/**
 * Cross-platform Qt hint mesh overlay. Currently used by Linux; Windows has a more
 * sophisticated implementation with per-screen windows, pixmap caching, and animations.
 * Planned to consolidate in a future PR once this class is feature-complete.
 */
public class HintMeshWindow extends TransparentWindow {

    private HintMesh hintMesh;

    public HintMeshWindow() {
        super();
    }

    public void setHintMesh(HintMesh hintMesh) {
        this.hintMesh = hintMesh;
        setGeometry(primaryScreenGeometry());
        show();
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
