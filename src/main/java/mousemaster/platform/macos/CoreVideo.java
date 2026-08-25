package mousemaster.platform.macos;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public interface CoreVideo extends Library {

    CoreVideo INSTANCE = Native.load("CoreVideo", CoreVideo.class);

    int lockReadOnly = 1;

    Pointer CVPixelBufferGetIOSurface(Pointer pixelBuffer);

    int CVPixelBufferLockBaseAddress(Pointer pixelBuffer, long flags);

    int CVPixelBufferUnlockBaseAddress(Pointer pixelBuffer, long flags);

    Pointer CVPixelBufferGetBaseAddress(Pointer pixelBuffer);

    long CVPixelBufferGetBytesPerRow(Pointer pixelBuffer);

    long CVPixelBufferGetWidth(Pointer pixelBuffer);

    long CVPixelBufferGetHeight(Pointer pixelBuffer);

}
