package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.util.Arrays;
import java.util.List;

public interface Gdiplus extends StdCallLibrary {
    Gdiplus INSTANCE = Native.load("gdiplus", Gdiplus.class);

    int GdiplusStartup(PointerByReference token, GdiplusStartupInput input, Pointer output);

    int GdipCreateFromHDC(WinDef.HDC hdc, PointerByReference graphicsRef);

    Pointer GdipDeleteGraphics(Pointer graphics);

    int GdipNewInstalledFontCollection(PointerByReference fontCollection);
    int GdipGetFontCollectionFamilyCount(Pointer fontCollection, IntByReference count);
    int GdipGetFontCollectionFamilyList(Pointer fontCollection, int count, PointerByReference[] fontFamilies, IntByReference actualCount);
    int GdipGetFamilyName(Pointer fontFamily, char[] name, int language);

    int GdipCreateFontFamilyFromName(WString name, Pointer graphics, PointerByReference fontFamily);

    int GdipDeleteFontFamily(Pointer fontFamily);

    int GdipCreateFont(Pointer family, float size, int style, int unit, PointerByReference font);
    int GdipMeasureString(Pointer graphics, WString string, int length, Pointer font, GdiplusRectF layoutRect, Pointer stringFormat, GdiplusRectF boundingBox, IntByReference codepointsFitted, IntByReference linesFilled);

    int GdipCreateStringFormat(int format, Pointer lang, PointerByReference stringFormat);
    int GdipSetStringFormatFlags(Pointer stringFormat, int flags);
    int GdipSetStringFormatAlign(Pointer stringFormat, int alignment);
    int GdipSetStringFormatLineAlign(Pointer stringFormat, int alignment);

    int GdipSetStringFormatMeasurableCharacterRanges(Pointer stringFormat, int rangeCount, CharacterRange[] ranges);

    int GdipGetRegionBounds(Pointer region, Pointer graphics, GdiplusRectF rect);

    int GdipCreateRegion(PointerByReference region);

    int GdipMeasureCharacterRanges(Pointer graphics, WString string, int length,
                                   Pointer font, GdiplusRectF layoutRect,
                                   Pointer stringFormat, int regionCount,
                                   Pointer[] regions);

    int GdipSetCompositingMode(Pointer graphics, int mode);
    int GdipSetCompositingQuality(Pointer graphics, int quality);
    int GdipSetTextRenderingHint(Pointer graphics, int hint);

    int GdipSetSmoothingMode(Pointer graphics, int smoothingMode); // https://learn.microsoft.com/en-us/windows/win32/api/gdiplusenums/ne-gdiplusenums-smoothingmode
    int GdipSetInterpolationMode(Pointer graphics, int interpolationMode); // https://learn.microsoft.com/en-us/windows/win32/api/gdiplusenums/ne-gdiplusenums-interpolationmode

    int GdipCreatePath(int brushMode, PointerByReference path);
    int GdipDrawPath(Pointer graphics, Pointer pen, Pointer path);
    int GdipFillPath(Pointer graphics, Pointer brush, Pointer path);
    int GdipSetPenLineJoin(Pointer pen, int lineJoin);

    int GdipGraphicsClear(Pointer graphics, int argb);

    int GdipCreateSolidFill(int color, PointerByReference brush);
    int GdipDrawString(Pointer graphics, WString text, int length, Pointer font, Pointer layoutRect, Pointer format, Pointer brush);
    int GdipDeleteFont(Pointer font);
    int GdipDeleteBrush(Pointer brush);
    int GdipAddPathString(Pointer path, WString text, int length, Pointer font, int fontStyle, float fontSize, Gdiplus.GdiplusRectF layoutRect, Pointer format);
    int GdipCreatePen1(int argb, float width, int unit, PointerByReference pen);
    int GdipDeletePen(Pointer pen);
    int GdipDeletePath(Pointer path);

    class GdiplusStartupInput extends Structure {
        public int GdiplusVersion;       // GDI+ version, must be 1
        public Pointer DebugEventCallback; // Debug callback (use null for default)
        public boolean SuppressBackgroundThread; // Whether to suppress the background thread
        public boolean SuppressExternalCodecs;   // Whether to suppress external image codecs

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("GdiplusVersion", "DebugEventCallback",
                    "SuppressBackgroundThread", "SuppressExternalCodecs");
        }

        // Default constructor for simple initialization
        public GdiplusStartupInput() {
            this.GdiplusVersion = 1; // Default version 1.0
            this.DebugEventCallback = null; // No debug callback
            this.SuppressBackgroundThread = false; // Use background thread
            this.SuppressExternalCodecs = false;   // Allow external codecs
        }
    }

    class GdiplusRectF extends Structure {
        public float x, y, width, height;

        public GdiplusRectF() {
            this(0, 0, 0, 0);
        }

        public GdiplusRectF(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("x", "y", "width", "height");
        }

        public static class ByReference extends Gdiplus.GdiplusRectF
                implements Structure.ByReference {

            public ByReference() {
                super(0, 0, 0, 0);
            }
        }

    }

    class CharacterRange extends Structure {
        public int first, length;

        public CharacterRange(int first, int length) {
            this.first = first;
            this.length = length;
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("first", "length");
        }

        public static class ByReference extends CharacterRange
                implements Structure.ByReference {

            public ByReference() {
                super(0, 0);
            }
        }

    }


}
