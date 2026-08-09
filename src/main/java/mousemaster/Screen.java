package mousemaster;

public record Screen(Rectangle rectangle, int dpi, double scale) {

    public Viewport viewport() {
        return new Viewport(rectangle.width(), rectangle.height(), scale);
    }

}
