package jmouseable.jmouseable;

public class MouseState {

    private final MouseManager mouseManager;

    public MouseState(MouseManager mouseManager) {
        this.mouseManager = mouseManager;
    }

    public boolean moving() {
        return mouseManager.moving();
    }

    public boolean pressing() {
        return mouseManager.pressing();
    }

    public boolean wheeling() {
        return mouseManager.wheeling();
    }

}
