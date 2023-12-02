package jmouseable.jmouseable;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.W32APITypeMapper;

import java.util.List;

public interface ExtendedGDI32 extends GDI32 {
    ExtendedGDI32 INSTANCE = Native.load("gdi32", ExtendedGDI32.class);

    WinDef.HBRUSH CreateSolidBrush(int color);

    boolean PolyPolyline(WinDef.HDC hdc, WinDef.POINT[] ppt, int[] pc, int cPoly);

    WinDef.HPEN CreatePen(int fnPenStyle, int nWidth, int crColor);

    int PS_SOLID = 0;

    int LOGPIXELSY = 90;
    int FW_NORMAL = 400;
    int FW_BOLD = 700;
    int ANSI_CHARSET = 0;
    int OUT_DEFAULT_PRECIS = 0;
    int CLIP_DEFAULT_PRECIS = 0;
    int DEFAULT_QUALITY = 0;
    int DEFAULT_PITCH = 0;
    int FF_SWISS = 0;

    // Text drawing flags
    int DT_SINGLELINE = 0x20;
    int DT_LEFT = 0x000;
    int DT_TOP = 0x000;
    int DT_CENTER = 0x0001;
    int DT_VCENTER = 0x0004;
    int DT_NOPREFIX = 0x800;
    int TRANSPARENT = 1;

    WinDef.HFONT CreateFontA(int cHeight, int cWidth, int cEscapement, int cOrientation,
                             int cWeight, WinDef.DWORD bItalic,
                             WinDef.DWORD bUnderline, WinDef.DWORD bStrikeOut,
                             WinDef.DWORD iCharSet, WinDef.DWORD iOutPrecision,
                             WinDef.DWORD iClipPrecision, WinDef.DWORD iQuality,
                             WinDef.DWORD iPitchAndFamily, String pszFaceName);
    boolean GetTextExtentPoint32A(WinDef.HDC hdc, String lpString, int cbString, WinUser.SIZE lpSize);
    boolean SetTextColor(WinDef.HDC hdc, int crColor);
    boolean SetBkMode(WinDef.HDC hdc, int iBkMode);

    interface EnumFontFamExProc extends StdCallCallback {
        int callback(LOGFONT lpelfe, TEXTMETRIC lpntme, WinDef.DWORD FontType, WinDef.LPARAM lParam);
    }

    int EnumFontFamiliesExA(WinDef.HDC hdc, LOGFONT lpLogfont,
                            EnumFontFamExProc lpEnumFontFamExProc, WinDef.LPARAM lParam,
                            WinDef.DWORD dwFlags);

    public class TEXTMETRIC extends Structure {
        public WinDef.LONG tmHeight;
        public WinDef.LONG tmAscent;
        public WinDef.LONG tmDescent;
        public WinDef.LONG tmInternalLeading;
        public WinDef.LONG tmExternalLeading;
        public WinDef.LONG tmAveCharWidth;
        public WinDef.LONG tmMaxCharWidth;
        public WinDef.LONG tmWeight;
        public WinDef.LONG tmOverhang;
        public WinDef.LONG tmDigitizedAspectX;
        public WinDef.LONG tmDigitizedAspectY;
        public byte tmFirstChar;
        public byte tmLastChar;
        public byte tmDefaultChar;
        public byte tmBreakChar;
        public byte tmItalic;
        public byte tmUnderlined;
        public byte tmStruckOut;
        public byte tmPitchAndFamily;
        public byte tmCharSet;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("tmHeight", "tmAscent", "tmDescent", "tmInternalLeading",
                    "tmExternalLeading", "tmAveCharWidth", "tmMaxCharWidth", "tmWeight",
                    "tmOverhang", "tmDigitizedAspectX", "tmDigitizedAspectY", "tmFirstChar",
                    "tmLastChar", "tmDefaultChar", "tmBreakChar", "tmItalic", "tmUnderlined",
                    "tmStruckOut", "tmPitchAndFamily", "tmCharSet");
        }

        public TEXTMETRIC() {
            super();
        }
    }

    public class LOGFONT extends Structure {
        public WinDef.LONG lfHeight;
        public WinDef.LONG lfWidth;
        public WinDef.LONG lfEscapement;
        public WinDef.LONG lfOrientation;
        public WinDef.LONG lfWeight;
        public byte lfItalic;
        public byte lfUnderline;
        public byte lfStrikeOut;
        public byte lfCharSet;
        public byte lfOutPrecision;
        public byte lfClipPrecision;
        public byte lfQuality;
        public byte lfPitchAndFamily;
        public byte[] lfFaceName = new byte[32];

        @Override
        protected List<String> getFieldOrder() {
            return List.of("lfHeight", "lfWidth", "lfEscapement", "lfOrientation", "lfWeight",
                    "lfItalic", "lfUnderline", "lfStrikeOut", "lfCharSet", "lfOutPrecision",
                    "lfClipPrecision", "lfQuality", "lfPitchAndFamily", "lfFaceName");
        }

        public LOGFONT() {
            super(W32APITypeMapper.DEFAULT);
        }
    }

    byte DEFAULT_CHARSET = 1;


}