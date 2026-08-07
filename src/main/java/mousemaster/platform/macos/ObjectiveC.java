package mousemaster.platform.macos;

import com.sun.jna.Callback;
import com.sun.jna.CallbackReference;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.ArrayList;
import java.util.List;

public interface ObjectiveC extends Library {

    ObjectiveC INSTANCE = Native.load("objc", ObjectiveC.class);

    Pointer objc_getClass(String name);

    Pointer objc_getProtocol(String name);

    Pointer sel_registerName(String name);

    Pointer objc_allocateClassPair(Pointer superclass, String name, long extraBytes);

    void objc_registerClassPair(Pointer cls);

    boolean class_addMethod(Pointer cls, Pointer selector, Pointer implementation,
                            String types);

    boolean class_addProtocol(Pointer cls, Pointer protocol);

    Pointer objc_msgSend(Pointer receiver, Pointer selector);

    Pointer objc_msgSend(Pointer receiver, Pointer selector, long argument);

    Pointer objc_msgSend(Pointer receiver, Pointer selector, Pointer argument);

    Pointer objc_msgSend(Pointer receiver, Pointer selector, Pointer first, Pointer second);

    Pointer objc_msgSend(Pointer receiver, Pointer selector, Pointer first, Pointer second,
                         Pointer third);

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

    /** What a completion handler block is called with, an object and an error either way round. */
    interface BlockHandler extends Callback {
        void callback(Pointer block, Pointer first, Pointer second);
    }

    interface FrameOutput extends Callback {
        void callback(Pointer self, Pointer selector, Pointer stream, Pointer sampleBuffer,
                      long type);
    }

    interface ReturningBoolean extends Library {

        ReturningBoolean INSTANCE = Native.load("objc", ReturningBoolean.class);

        boolean objc_msgSend(Pointer receiver, Pointer selector, Pointer first, long second,
                             Pointer third, Pointer fourth);

    }

    /**
     * The literal a block is: an isa, flags, the function to call and a descriptor holding its
     * size. Global, so the frameworks that copy it leave it where it is.
     */
    static Pointer block(Callback callback) {
        Memory descriptor = new Memory(24);
        descriptor.clear();
        descriptor.setLong(8, 32);
        Memory literal = new Memory(32);
        literal.setPointer(0, NativeLibrary.getInstance("System")
                                           .getGlobalVariableAddress("_NSConcreteGlobalBlock"));
        literal.setInt(8, 1 << 28);
        literal.setInt(12, 0);
        literal.setPointer(16, CallbackReference.getFunctionPointer(callback));
        literal.setPointer(24, descriptor);
        blocks.add(new Object[] {descriptor, literal, callback});
        return literal;
    }

    /** Held: a block or a callback collected while a framework still calls it crashes. */
    List<Object> blocks = new ArrayList<>();

}
