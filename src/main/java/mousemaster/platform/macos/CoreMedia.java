package mousemaster.platform.macos;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public interface CoreMedia extends Library {

    CoreMedia INSTANCE = Native.load("CoreMedia", CoreMedia.class);

    Pointer CMSampleBufferGetImageBuffer(Pointer sampleBuffer);

}
