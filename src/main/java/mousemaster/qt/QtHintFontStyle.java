package mousemaster.qt;

public record QtHintFontStyle(QtFontStyle defaultStyle,
                              QtFontStyle selectedStyle,
                              QtFontStyle focusedStyle,
                              QtFontStyle prefixDefaultStyle,
                              QtFontStyle prefixSelectedStyle,
                              QtFontStyle prefixFocusedStyle,
                              boolean perKeyFont,
                              boolean perKeyShadow,
                              double fontSpacingPercent) {

    public boolean hasTransparency(boolean hasSelectedKeys) {
        if (defaultStyle.hasTransparency() ||
            (hasSelectedKeys && selectedStyle.hasTransparency()) ||
            focusedStyle.hasTransparency())
            return true;
        if (prefixDefaultStyle != null) {
            if (prefixDefaultStyle.hasTransparency() ||
                (hasSelectedKeys && prefixSelectedStyle.hasTransparency()) ||
                prefixFocusedStyle.hasTransparency())
                return true;
        }
        return false;
    }
}
