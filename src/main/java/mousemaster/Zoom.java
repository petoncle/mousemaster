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

}
