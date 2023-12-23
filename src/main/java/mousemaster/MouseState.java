package mousemaster;

public class MouseState {

    private final MouseController mouseController;

    public MouseState(MouseController mouseController) {
        this.mouseController = mouseController;
    }

    public boolean moving() {
        return mouseController.moving();
    }

    public boolean pressing() {
        return mouseController.pressing();
    }

    public boolean wheeling() {
        return mouseController.wheeling();
    }

}
