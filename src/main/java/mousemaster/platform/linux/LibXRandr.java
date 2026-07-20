package mousemaster.platform.linux;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/**
 * JNA bindings for XRandR extension (libXrandr.so)
 */
public interface LibXRandr extends Library {

    LibXRandr INSTANCE = Native.load("Xrandr", LibXRandr.class);

    // Screen resources
    Pointer XRRGetScreenResources(Pointer display, long window);
    void XRRFreeScreenResources(Pointer resources);

    // Monitor information (XRandR 1.5+)
    Pointer XRRGetMonitors(Pointer display, long window, boolean getActive, IntByReference nmonitors);
    void XRRFreeMonitors(Pointer monitors);

    // CRTC information
    Pointer XRRGetCrtcInfo(Pointer display, Pointer resources, long crtc);
    void XRRFreeCrtcInfo(Pointer crtcInfo);

    // Output information
    Pointer XRRGetOutputInfo(Pointer display, Pointer resources, long output);
    void XRRFreeOutputInfo(Pointer outputInfo);

    /**
     * Helper class for reading int by reference
     */
    class IntByReference extends com.sun.jna.ptr.IntByReference {
        public IntByReference() {
            super();
        }
        public IntByReference(int value) {
            super(value);
        }
    }

    /**
     * XRRMonitorInfo structure (XRandR 1.5+)
     */
    @Structure.FieldOrder({"name", "primary", "automatic", "noutput", "x", "y", "width", "height",
                          "mwidth", "mheight", "outputs"})
    class XRRMonitorInfo extends Structure {
        public long name;          // Atom
        public boolean primary;
        public boolean automatic;
        public int noutput;
        public int x, y;
        public int width, height;  // pixels
        public int mwidth, mheight; // millimeters
        public Pointer outputs;    // RROutput*

        public XRRMonitorInfo() {
            super();
        }

        public XRRMonitorInfo(Pointer p) {
            super(p);
            read();
        }

        public static class ByReference extends XRRMonitorInfo implements Structure.ByReference {}
    }

    /**
     * XRRScreenResources structure
     */
    @Structure.FieldOrder({"timestamp", "configTimestamp", "ncrtc", "crtcs", "noutput", "outputs",
                          "nmode", "modes"})
    class XRRScreenResources extends Structure {
        public long timestamp;
        public long configTimestamp;
        public int ncrtc;
        public Pointer crtcs;    // RRCrtc*
        public int noutput;
        public Pointer outputs;  // RROutput*
        public int nmode;
        public Pointer modes;    // XRRModeInfo*

        public static class ByReference extends XRRScreenResources implements Structure.ByReference {}
    }

    /**
     * XRRCrtcInfo structure
     */
    @Structure.FieldOrder({"timestamp", "x", "y", "width", "height", "mode", "rotation",
                          "noutput", "outputs", "rotations", "npossible", "possible"})
    class XRRCrtcInfo extends Structure {
        public long timestamp;
        public int x, y;
        public int width, height;
        public long mode;        // RRMode
        public short rotation;
        public int noutput;
        public Pointer outputs;  // RROutput*
        public short rotations;
        public int npossible;
        public Pointer possible; // RROutput*

        public static class ByReference extends XRRCrtcInfo implements Structure.ByReference {}
    }

    /**
     * XRROutputInfo structure
     */
    @Structure.FieldOrder({"timestamp", "crtc", "name", "nameLen", "mmWidth", "mmHeight",
                          "connection", "subpixelOrder", "ncrtc", "crtcs", "nclone", "clones",
                          "nmode", "npreferred", "modes"})
    class XRROutputInfo extends Structure {
        public long timestamp;
        public long crtc;        // RRCrtc
        public Pointer name;     // char*
        public int nameLen;
        public long mmWidth, mmHeight; // millimeters
        public byte connection;
        public byte subpixelOrder;
        public int ncrtc;
        public Pointer crtcs;    // RRCrtc*
        public int nclone;
        public Pointer clones;   // RROutput*
        public int nmode;
        public int npreferred;
        public Pointer modes;    // RRMode*

        public static class ByReference extends XRROutputInfo implements Structure.ByReference {}
    }

}
