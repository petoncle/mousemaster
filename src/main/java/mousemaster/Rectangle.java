package mousemaster;

public record Rectangle(int x, int y, int width, int height) {
    public static boolean rectangleContains(int rectX, int rectY, int rectWidth,
                                            int rectHeight, double pointX, double pointY) {
        return pointX >= rectX && pointX <= rectX + rectWidth && pointY >= rectY &&
               pointY <= rectY + rectHeight;
    }

    public static double rectangleEdgeDistanceTo(int rectX, int rectY, int rectWidth,
                                                 int rectHeight, int pointX, int pointY) {
        int closestX = Math.max(rectX, Math.min(pointX, rectX + rectWidth));
        int closestY = Math.max(rectY, Math.min(pointY, rectY + rectHeight));
        return Math.hypot(closestX - pointX, closestY - pointY);
    }

    public Point center() {
        int centerX;
        int centerY;
        centerX = x() + width() / 2;
        centerY = y() + height() / 2;
        return new Point(centerX, centerY);
    }

    public boolean contains(double pointX, double pointY) {
        return rectangleContains(x, y, width, height, pointX, pointY);
    }

    public boolean sharesEdgeWith(Rectangle other) {
        // Check for shared top/bottom edge.
        if ((this.y == other.y + other.height) || (this.y + this.height == other.y)) {
            if ((this.x < other.x + other.width) && (other.x < this.x + this.width)) {
                return true;
            }
        }
        // Check for shared left/right edge.
        if ((this.x == other.x + other.width) || (this.x + this.width == other.x)) {
            if ((this.y < other.y + other.height) && (other.y < this.y + this.height)) {
                return true;
            }
        }
        // Check if the right edges align.
        if (this.x + this.width == other.x + other.width) {
            if ((this.y < other.y + other.height) && (other.y < this.y + this.height)) {
                return true;
            }
        }
        // Check if the bottom edges align.
        if (this.y + this.height == other.y + other.height) {
            if ((this.x < other.x + other.width) && (other.x < this.x + this.width)) {
                return true;
            }
        }
        return false;
    }

}
