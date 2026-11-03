package mousemaster;

public record HintCellSizing(HintCellSizingType type, double cellWidth, double cellHeight) {

    public enum HintCellSizingType {

        FIXED, FIT

    }

}
