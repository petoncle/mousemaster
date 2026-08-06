package mousemaster.platform.macos;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

public interface ObjectiveC extends Library {

    ObjectiveC INSTANCE = Native.load("objc", ObjectiveC.class);

    Pointer objc_getClass(String name);

    Pointer sel_registerName(String name);

    Pointer objc_msgSend(Pointer receiver, Pointer selector);

    Pointer objc_msgSend(Pointer receiver, Pointer selector, long argument);

    Pointer objc_msgSend(Pointer receiver, Pointer selector, Pointer argument);

    Pointer objc_msgSend(Pointer receiver, Pointer selector,
                         CoreGraphics.CGRect.ByValue argument);

    @Structure.FieldOrder({"width", "height"})
    class CGSize extends Structure {
        public double width;
        public double height;

        public static class ByValue extends CGSize implements Structure.ByValue {
        }
    }

    /** Java cannot overload on the return type, so an int returning selector needs its own. */
    interface ReturningInt extends Library {

        ReturningInt INSTANCE = Native.load("objc", ReturningInt.class);

        int objc_msgSend(Pointer receiver, Pointer selector);

    }

    /** A selector returning a size or a point, two doubles returned by value. */
    interface ReturningSize extends Library {

        ReturningSize INSTANCE = Native.load("objc", ReturningSize.class);

        CGSize.ByValue objc_msgSend(Pointer receiver, Pointer selector);

    }

}
