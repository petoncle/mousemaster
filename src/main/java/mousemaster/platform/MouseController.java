package mousemaster.platform;

public interface MouseController {

    void beginMove();

    void endMove();

    void moveBy(boolean xForward, double dx, boolean yForward, double dy);

    void synchronousMoveTo(int x, int y);

    void pressLeft();

    void pressMiddle();

    void pressRight();

    void releaseLeft();

    void releaseMiddle();

    void releaseRight();

    void wheelHorizontallyBy(boolean forward, double delta);

    void wheelVerticallyBy(boolean forward, double delta);

    void showCursor();

    void hideCursor();
}
