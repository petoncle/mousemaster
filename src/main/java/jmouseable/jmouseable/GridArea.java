package jmouseable.jmouseable;

public sealed interface GridArea {

    record ActiveScreen(double screenWidthPercent, double screenHeightPercent)
            implements GridArea {
    }

    record ActiveWindow(double windowWidthPercent, double windowHeightPercent)
            implements GridArea {
    }

}
