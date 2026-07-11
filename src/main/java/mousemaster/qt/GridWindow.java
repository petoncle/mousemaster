package mousemaster.qt;

import io.qt.core.Qt;
import io.qt.gui.QColor;
import io.qt.gui.QPaintEvent;
import io.qt.gui.QPainter;
import io.qt.gui.QPen;
import mousemaster.Grid;

/**
 * Cross-platform Qt grid overlay. Currently used by Linux; Windows has its own Win32-based
 * implementation. Planned to consolidate in a future PR.
 */
public class GridWindow extends TransparentWindow {

    private Grid grid;

    public GridWindow() {
        super();
    }

    public void setGrid(Grid grid) {
        this.grid = grid;

        // Position and size the window
        setGeometry(grid.x(), grid.y(), grid.width(), grid.height());

        // Make window visible
        show();

        // Request repaint
        update();
    }

    public void clearGrid() {
        this.grid = null;
        hide();
    }

    @Override
    protected void paintEvent(QPaintEvent event) {
        super.paintEvent(event);

        if (grid == null || !grid.lineVisible()) {
            return;
        }

        QPainter painter = new QPainter(this);

        // Parse hex color (format: "#RRGGBB" or "RRGGBB")
        String hexColor = grid.lineHexColor();
        if (hexColor.startsWith("#")) {
            hexColor = hexColor.substring(1);
        }

        int rgb = Integer.parseInt(hexColor, 16);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // Set up pen for drawing lines
        QPen pen = new QPen(new QColor(r, g, b));
        pen.setWidth((int) Math.round(grid.lineThickness()));
        pen.setStyle(Qt.PenStyle.SolidLine);
        painter.setPen(pen);

        int rowCount = grid.rowCount();
        int columnCount = grid.columnCount();
        int cellWidth = grid.width() / columnCount;
        int cellHeight = grid.height() / rowCount;

        // Draw vertical lines
        for (int col = 0; col <= columnCount; col++) {
            int x = (col == columnCount) ? grid.width() - 1 : col * cellWidth;
            painter.drawLine(x, 0, x, grid.height());
        }

        // Draw horizontal lines
        for (int row = 0; row <= rowCount; row++) {
            int y = (row == rowCount) ? grid.height() - 1 : row * cellHeight;
            painter.drawLine(0, y, grid.width(), y);
        }

        painter.end();
        painter.dispose();
        pen.dispose();
    }
}
