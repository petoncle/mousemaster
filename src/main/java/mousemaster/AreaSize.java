package mousemaster;

public record AreaSize(AreaSizeSource source,
                       double widthPercent, double heightPercent, double topInset,
                       double bottomInset, double leftInset, double rightInset) {
}
