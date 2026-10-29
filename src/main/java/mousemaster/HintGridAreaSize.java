package mousemaster;

public record HintGridAreaSize(HintGridAreaSizeSource source,
                               double widthPercent, double heightPercent, double topInset,
                               double bottomInset, double leftInset, double rightInset) {
}
