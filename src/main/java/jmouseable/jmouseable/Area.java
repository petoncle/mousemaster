package jmouseable.jmouseable;

public sealed interface Area {

    record ActiveScreen(double screenWidthPercent, double screenHeightPercent)
            implements Area {
    }

    record ActiveWindow(double windowWidthPercent, double windowHeightPercent)
            implements Area {
    }

}
