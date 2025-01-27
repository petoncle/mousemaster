package mousemaster;

public class MouseState {

    private final MouseController mouseController;

    public MouseState(MouseController mouseController) {
        this.mouseController = mouseController;
    }

    public boolean moving() {
        return mouseController.moving();
    }

    public boolean leftPressing() {
        return mouseController.leftPressing();
    }

    public boolean middlePressing() {
        return mouseController.middlePressing();
    }

    public boolean rightPressing() {
        return mouseController.rightPressing();
    }

    public boolean wheeling() {
        return mouseController.wheeling();
    }

}
