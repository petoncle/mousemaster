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

    /** Whether every state this mesh can draw is invisible, so the layer stays empty. */
    public boolean invisible(boolean hasSelectedKeys) {
        if (!defaultStyle.invisible() || !focusedStyle.invisible() ||
            (hasSelectedKeys && !selectedStyle.invisible()))
            return false;
        if (prefixDefaultStyle != null &&
            (!prefixDefaultStyle.invisible() || !prefixFocusedStyle.invisible() ||
             (hasSelectedKeys && !prefixSelectedStyle.invisible())))
            return false;
        return true;
    }

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
