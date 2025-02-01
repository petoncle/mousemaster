package mousemaster;

public record Zoom(double percent, Point center, Rectangle screenRectangle) {

    public double zoomedX(double x) {
        return screenRectangle.width() / 2d + (x - center.x()) * percent;
    }

    public double zoomedY(double y) {
        return screenRectangle.height() / 2d + (y - center.y()) * percent;
    }

    public double unzoomedX(double zoomedX) {
        return (zoomedX - screenRectangle.width() / 2d) / percent + center.x();
    }

    public double unzoomedY(double zoomedY) {
        return (zoomedY - screenRectangle.height() / 2d) / percent + center.y();
    }

}
