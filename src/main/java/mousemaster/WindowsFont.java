package mousemaster;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WindowsFont {

    public enum WindowsFontStyle {
        REGULAR("Regular"),
        BOLD("Bold"),
        ITALIC("Italic"),
        BOLD_ITALIC("Bold Italic"),
        ;

        private final String nameInFont;

        WindowsFontStyle(String nameInFont) {
            this.nameInFont = nameInFont;
        }

        public String nameInFont() {
            return nameInFont;
        }

    }

    public record WindowsFontFamilyAndStyle(String fontFamily, WindowsFontStyle style) {

    }

    private static final Map<String, WindowsFontFamilyAndStyle> fontFamilyAndStyleByName = new HashMap<>();

    /**
     * - Cascadia Code Light -> Cascadia Code Light, style Regular
     * - Arial Bold -> Arial, style Bold
     * - Arial Rounded MT Bold -> Arial Rounded MT Bold, style Regular
     */
    public static void setFontPool(Set<String> hintFontNames) {
        fontFamilyAndStyleByName.clear();
        // Order (Bold Italic before Bold and Italic) matters.
        List<WindowsFontStyle> styles =
                List.of(WindowsFontStyle.BOLD_ITALIC, WindowsFontStyle.BOLD,
                        WindowsFontStyle.ITALIC, WindowsFontStyle.REGULAR);
        for (String hintFontName : hintFontNames) {
            if (doesFontExist(hintFontName)) {
                fontFamilyAndStyleByName.put(hintFontName,
                        new WindowsFontFamilyAndStyle(hintFontName,
                                WindowsFontStyle.REGULAR));
                continue;
            }
            WindowsFontStyle style = styles.stream()
                                           .filter(style1 -> hintFontName.endsWith(
                                                   style1.nameInFont()))
                                           .findFirst()
                                           .orElse(null);
            if (style != null) {
                String fontNameWithoutStyle = hintFontName.substring(0,
                        hintFontName.length() - style.nameInFont().length() -
                        1); // - 1 for the space
                if (doesFontExist(fontNameWithoutStyle)) {
                    fontFamilyAndStyleByName.put(hintFontName,
                            new WindowsFontFamilyAndStyle(fontNameWithoutStyle,
                                    style));
                    continue;
                }
            }
            throw new IllegalStateException("Unable to find hint font: " + hintFontName);
        }
    }

    public static WindowsFontFamilyAndStyle fontFamilyAndStyle(String hintFontName) {
        return fontFamilyAndStyleByName.get(hintFontName);
    }

    private static boolean doesFontExist(String fontName) {
        ExtendedGDI32.EnumFontFamExProc fontEnumProc = new ExtendedGDI32.EnumFontFamExProc() {
            public int callback(ExtendedGDI32.LOGFONT lpelfe, ExtendedGDI32.TEXTMETRIC lpntme, WinDef.DWORD FontType, WinDef.LPARAM lParam) {
                int nameLength = 0;
                for (int i = 0; i < lpelfe.lfFaceName.length; i++) {
                    if (lpelfe.lfFaceName[i] == 0) {
                        nameLength = i;
                        break;
                    }
                }
                String faceName = new String(lpelfe.lfFaceName, 0, nameLength);
                if (fontName.equalsIgnoreCase(faceName)) {
                    // Font found
                    return 0; // Return 0 to stop enumeration
                }
                return 1; // Continue enumeration
            }
        };
        WinDef.HDC hdc = User32.INSTANCE.GetDC(null);
        ExtendedGDI32.LOGFONT logfont = new ExtendedGDI32.LOGFONT();
        logfont.lfCharSet = ExtendedGDI32.DEFAULT_CHARSET;
        byte[] fontBytes = fontName.getBytes();
        System.arraycopy(fontBytes, 0, logfont.lfFaceName, 0,
                Math.min(fontBytes.length, logfont.lfFaceName.length - 1));
        // lfFaceName[this.lfFaceName.length - 1] is 0, it is the null-terminator.
        boolean fontExists =
                ExtendedGDI32.INSTANCE.EnumFontFamiliesExA(hdc, logfont, fontEnumProc,
                        new WinDef.LPARAM(0), new WinDef.DWORD(0)) == 0;
        User32.INSTANCE.ReleaseDC(null, hdc);
        return fontExists;
    }

}
