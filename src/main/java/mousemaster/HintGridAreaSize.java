package mousemaster;

public record HintGridAreaSize(HintGridAreaSizeSource source,
                               double widthPercent, double heightPercent, int topInset,
                               int bottomInset, int leftInset, int rightInset) {
}
