package jmouseable.jmouseable;

public sealed interface HintMeshType {

    record ActiveScreen(double screenWidthPercent, double screenHeightPercent,
                        int rowCount, int columnCount) implements HintMeshType {
    }

    record ActiveWindow(double windowWidthPercent, double windowHeightPercent,
                        int rowCount, int columnCount) implements HintMeshType {
    }

    record AllScreens(double screenWidthPercent, double screenHeightPercent,
                      int rowCount, int columnCount) implements HintMeshType {

    }

    // TODO MousePositionHistory

}
