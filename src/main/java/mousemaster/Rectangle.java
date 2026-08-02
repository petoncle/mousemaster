package mousemaster;

import java.util.Collection;

public record Rectangle(int x, int y, int width, int height) {
    public static boolean rectangleContains(int rectX, int rectY, int rectWidth,
                                            int rectHeight, double pointX, double pointY) {
        return pointX >= rectX && pointX <= rectX + rectWidth && pointY >= rectY &&
               pointY <= rectY + rectHeight;
    }

    public static double rectangleEdgeDistanceTo(int rectX, int rectY, int rectWidth,
                                                 int rectHeight, double pointX, double pointY) {
        double closestX = Math.max(rectX, Math.min(pointX, rectX + rectWidth));
        double closestY = Math.max(rectY, Math.min(pointY, rectY + rectHeight));
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

    public boolean contains(Rectangle other) {
        return contains(other.x, other.y) &&
               contains(other.x + other.width, other.y + other.height);
    }

    public boolean isEmpty() {
        return width == 0 || height == 0;
    }

    public Rectangle intersection(Rectangle other) {
        int left = Math.max(this.x, other.x);
        int top = Math.max(this.y, other.y);
        int right = Math.min(this.x + this.width, other.x + other.width);
        int bottom = Math.min(this.y + this.height, other.y + other.height);
        return new Rectangle(left, top, Math.max(0, right - left),
                Math.max(0, bottom - top));
    }

    public static Rectangle union(Collection<Rectangle> rectangles) {
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        for (Rectangle rectangle : rectangles) {
            left = Math.min(left, rectangle.x);
            top = Math.min(top, rectangle.y);
            right = Math.max(right, rectangle.x + rectangle.width);
            bottom = Math.max(bottom, rectangle.y + rectangle.height);
        }
        return new Rectangle(left, top, right - left, bottom - top);
    }

    public double overlapRatio(Rectangle other) {
        Rectangle intersection = intersection(other);
        if (intersection.isEmpty())
            return 0;
        long largerArea = Math.max((long) this.width * this.height,
                                   (long) other.width * other.height);
        if (largerArea == 0)
            return 0;
        return (double) ((long) intersection.width * intersection.height) / largerArea;
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
