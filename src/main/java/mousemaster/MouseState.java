package mousemaster;

public class MouseState {

    private final MouseManager mouseManager;

    public MouseState(MouseManager mouseManager) {
        this.mouseManager = mouseManager;
    }

    public boolean moving() {
        return mouseManager.moving();
    }

    public boolean leftPressing() {
        return mouseManager.leftPressing();
    }

    public boolean middlePressing() {
        return mouseManager.middlePressing();
    }

    public boolean rightPressing() {
        return mouseManager.rightPressing();
    }

    public boolean wheeling() {
        return mouseManager.wheeling();
    }

    public boolean pressing() {
        return leftPressing() || middlePressing() || rightPressing();
    }

    public boolean idling() {
        return !moving() && !wheeling() && !pressing();
    }

}
