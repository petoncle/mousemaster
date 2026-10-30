package mousemaster;

public record Zoom(double percent, Point center, Rectangle screenRectangle) {

    public double zoomedX(double x) {
        return screenRectangle.center().x() + (x - center.x()) * percent;
    }

    public double zoomedY(double y) {
        return screenRectangle.center().y() + (y - center.y()) * percent;
    }

    public double unzoomedX(double zoomedX) {
        return (zoomedX - screenRectangle.center().x()) / percent + center.x();
    }

    public double unzoomedY(double zoomedY) {
        return (zoomedY - screenRectangle.center().y()) / percent + center.y();
    }

    /** A desktop region as it appears on screen. */
    public Rectangle zoomedRectangle(Rectangle rectangle) {
        int left = (int) Math.round(zoomedX(rectangle.x()));
        int top = (int) Math.round(zoomedY(rectangle.y()));
        return new Rectangle(left, top,
                (int) Math.round(zoomedX(rectangle.x() + rectangle.width())) - left,
                (int) Math.round(zoomedY(rectangle.y() + rectangle.height())) - top);
    }

    public Rectangle unzoomedRectangle(Rectangle rectangle) {
        int left = (int) Math.round(unzoomedX(rectangle.x()));
        int top = (int) Math.round(unzoomedY(rectangle.y()));
        return new Rectangle(left, top,
                (int) Math.round(unzoomedX(rectangle.x() + rectangle.width())) - left,
                (int) Math.round(unzoomedY(rectangle.y() + rectangle.height())) - top);
    }

}
