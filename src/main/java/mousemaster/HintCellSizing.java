package mousemaster;

public sealed interface HintCellSizing {

    record FixedCellSize(double cellWidth, double cellHeight) implements HintCellSizing {

    }

    record FitToArea() implements HintCellSizing {

    }

    enum HintCellSizingType {

        FIXED, FIT

    }

}
