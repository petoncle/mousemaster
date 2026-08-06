package mousemaster.platform.macos;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

/** The accessibility API, which needs the Accessibility permission. */
public interface ApplicationServices extends Library {

    ApplicationServices INSTANCE =
            Native.load("ApplicationServices", ApplicationServices.class);

    int cgPointType = 1;
    int cgSizeType = 2;

    Pointer AXUIElementCreateApplication(int processId);

    /** 0 on success. */
    int AXUIElementCopyAttributeValue(Pointer element, Pointer attribute,
                                      PointerByReference value);

    /** Writes the two components of a point or a size, avoiding a struct return. */
    boolean AXValueGetValue(Pointer value, int type, double[] components);

    /** 0 on success. */
    int AXUIElementCopyActionNames(Pointer element, PointerByReference names);

}
