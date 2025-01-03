package mousemaster;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinDef;

import java.util.List;

public interface Magnification extends Library {

    Magnification INSTANCE = Native.load("Magnification", Magnification.class);

    boolean MagInitialize();

    boolean MagSetWindowTransform(WinDef.HWND hwnd, MAGTRANSFORM.ByReference pTransform);

    boolean MagSetWindowSource(WinDef.HWND hwnd, WinDef.RECT rect);

    class MAGTRANSFORM extends Structure {
        public float[] v = new float[9]; // 3x3 transformation matrix

        @Override
        protected List<String> getFieldOrder() {
            return List.of("v");
        }

        public MAGTRANSFORM(float scale) {
            v[0] = scale;
            v[4] = scale;
            v[8] = 1;
        }

        public static class ByReference extends MAGTRANSFORM implements Structure.ByReference {

            public ByReference(float scale) {
                super(scale);
            }
        }

    }

}