package jmouseable.jmouseable;

public record Rectangle(int x, int y, int width, int height) {
    public static boolean rectangleContains(int rectX, int rectY, int rectWidth,
                                            int rectHeight, int pointX, int pointY) {
        return pointX >= rectX && pointX <= rectX + rectWidth && pointY >= rectY &&
               pointY <= rectY + rectHeight;
    }

    public static double rectangleEdgeDistanceTo(int rectX, int rectY, int rectWidth,
                                                 int rectHeight, int pointX, int pointY) {
        int closestX = Math.max(rectX, Math.min(pointX, rectX + rectWidth));
        int closestY = Math.max(rectY, Math.min(pointY, rectY + rectHeight));
        return Math.hypot(closestX - pointX, closestY - pointY);
    }
}
