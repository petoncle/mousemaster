package mousemaster.platform.macos;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public interface CoreVideo extends Library {

    CoreVideo INSTANCE = Native.load("CoreVideo", CoreVideo.class);

    Pointer CVPixelBufferGetIOSurface(Pointer pixelBuffer);

}
