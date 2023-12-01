package jmouseable.jmouseable;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

public interface ExtendedGDI32 extends GDI32 {
    ExtendedGDI32 INSTANCE = Native.load("gdi32", ExtendedGDI32.class);

    WinDef.HBRUSH CreateSolidBrush(int color);

    boolean PolyPolyline(WinDef.HDC hdc, WinDef.POINT[] ppt, int[] pc, int cPoly);

    WinDef.HPEN CreatePen(int fnPenStyle, int nWidth, int crColor);

    int PS_SOLID = 0;

    int LOGPIXELSY = 90;
    int FW_NORMAL = 400;
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


}