package mousemaster.platform.macos;

import com.sun.jna.CallbackReference;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import io.qt.core.QPoint;
import mousemaster.Rectangle;
import mousemaster.platform.DesktopCapture;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/** One stream per display covering it whole: the frame it delivered last is that display
 *  until it delivers another. */
final class MacosDesktopCapture {

    private static final ObjectiveC objectiveC = ObjectiveC.INSTANCE;
    private static final Pointer alloc = objectiveC.sel_registerName("alloc");
    private static final Pointer init = objectiveC.sel_registerName("init");
    private static final Pointer count = objectiveC.sel_registerName("count");
    private static final Pointer objectAtIndex =
            objectiveC.sel_registerName("objectAtIndex:");
    private static final Pointer frame = objectiveC.sel_registerName("frame");
    private static final long waitMillis = 5000;
    private static final long bgraPixelFormat = 0x42475241;

    private static final AtomicReference<CompletableFuture<Pointer>> completion =
            new AtomicReference<>();
    /** The stream hands a frame to its own output instance, which names the display. */
    private static final Map<Pointer, MacosDesktopCapture> capturesByOutput =
            new ConcurrentHashMap<>();
    private static Pointer outputClass;

    private final Rectangle bounds;
    private final AtomicReference<Pointer> latest = new AtomicReference<>();
    private Pointer stream;
    private Pointer streamOutput;
    private Rectangle displayBounds;

    MacosDesktopCapture(Rectangle bounds) {
        this.bounds = bounds;
    }

    /** Always serves the bounds it was made for: the display it settled on may be another. */
    boolean covers(Rectangle bounds) {
        return this.bounds.equals(bounds)
               || displayBounds != null && displayBounds.contains(bounds);
    }

    /** ScreenCaptureKit reports the display in points, the desktop is in pixels. */
    private static Rectangle pixels(CoreGraphics.CGRect.ByValue frame, double scale) {
        return new Rectangle((int) Math.round(frame.x * scale),
                (int) Math.round(frame.y * scale),
                (int) Math.round(frame.width * scale),
                (int) Math.round(frame.height * scale));
    }

    private static final ObjectiveC.BlockHandler handler = (block, first, second) -> {
        if (first != null)
            CoreFoundation.INSTANCE.CFRetain(first);
        CompletableFuture<Pointer> answer = completion.get();
        if (answer != null)
            answer.complete(first);
    };
    private static final Pointer handlerBlock = ObjectiveC.block(handler);

    private static final ObjectiveC.FrameOutput frameOutput =
            (self, selector, ofStream, sampleBuffer, type) -> {
                Pointer pixelBuffer =
                        CoreMedia.INSTANCE.CMSampleBufferGetImageBuffer(sampleBuffer);
                if (pixelBuffer == null)
                    return;
                MacosDesktopCapture capture = capturesByOutput.get(self);
                if (capture == null)
                    return;
                Pointer dropped = capture.latest.getAndSet(
                        CoreFoundation.INSTANCE.CFRetain(pixelBuffer));
                if (dropped != null)
                    CoreFoundation.INSTANCE.CFRelease(dropped);
            };

    byte[] capture(Rectangle bounds, int scaledWidth, int scaledHeight) {
        startStreaming();
        Pointer pixelBuffer = awaitFrame();
        if (pixelBuffer == null)
            throw new IllegalStateException("no frame of " + bounds);
        try {
            return rgb(pixelBuffer, bounds, scaledWidth, scaledHeight);
        }
        finally {
            CoreFoundation.INSTANCE.CFRelease(pixelBuffer);
        }
    }

    private void startStreaming() {
        if (stream != null)
            return;
        NativeLibrary.getInstance(
                "/System/Library/Frameworks/ScreenCaptureKit.framework/ScreenCaptureKit");
        Pointer content = awaitCompletion(objectiveC.objc_getClass("SCShareableContent"),
                objectiveC.sel_registerName("getShareableContentWithCompletionHandler:"));
        if (content == null)
            throw new IllegalStateException("no shareable content");
        try {
            Pointer display = displayContaining(content, bounds);
            if (display == null)
                throw new IllegalStateException("no display contains " + bounds);
            displayBounds = pixels(
                    ObjectiveC.ReturningRect.INSTANCE.objc_msgSend(display, frame),
                    MacosScreens.scaleAt(new QPoint(bounds.x(), bounds.y())));
            Pointer filter = objectiveC.objc_msgSend(
                    objectiveC.objc_msgSend(objectiveC.objc_getClass("SCContentFilter"),
                            alloc),
                    objectiveC.sel_registerName(
                            "initWithDisplay:excludingApplications:exceptingWindows:"),
                    display, ownApplication(content),
                    objectiveC.objc_msgSend(objectiveC.objc_getClass("NSArray"),
                            objectiveC.sel_registerName("array")));
            Pointer configuration = objectiveC.objc_msgSend(objectiveC.objc_msgSend(
                    objectiveC.objc_getClass("SCStreamConfiguration"), alloc), init);
            objectiveC.objc_msgSend(configuration,
                    objectiveC.sel_registerName("setWidth:"), (long) displayBounds.width());
            objectiveC.objc_msgSend(configuration,
                    objectiveC.sel_registerName("setHeight:"), (long) displayBounds.height());
            // Left alone the frames come planar, which only a layer can take.
            objectiveC.objc_msgSend(configuration,
                    objectiveC.sel_registerName("setPixelFormat:"), bgraPixelFormat);
            stream = objectiveC.objc_msgSend(
                    objectiveC.objc_msgSend(objectiveC.objc_getClass("SCStream"), alloc),
                    objectiveC.sel_registerName("initWithFilter:configuration:delegate:"),
                    filter, configuration, null);
            if (stream == null)
                throw new IllegalStateException("no stream of " + bounds);
            ObjectiveC.ReturningBoolean.INSTANCE.objc_msgSend(stream,
                    objectiveC.sel_registerName(
                            "addStreamOutput:type:sampleHandlerQueue:error:"),
                    streamOutput(), 0,
                    Libc.INSTANCE.dispatch_queue_create("mousemaster.capture", null),
                    null);
            objectiveC.objc_msgSend(stream,
                    objectiveC.sel_registerName("startCaptureWithCompletionHandler:"),
                    handlerBlock);
        }
        finally {
            CoreFoundation.INSTANCE.CFRelease(content);
        }
    }

    /** The stream hands its frames to an instance of a class registered once. */
    private Pointer streamOutput() {
        if (streamOutput != null)
            return streamOutput;
        if (outputClass == null) {
            outputClass = objectiveC.objc_allocateClassPair(
                    objectiveC.objc_getClass("NSObject"), "MousemasterCaptureOutput", 0);
            objectiveC.class_addMethod(outputClass,
                    objectiveC.sel_registerName("stream:didOutputSampleBuffer:ofType:"),
                    CallbackReference.getFunctionPointer(frameOutput), "v@:@@q");
            objectiveC.class_addProtocol(outputClass,
                    objectiveC.objc_getProtocol("SCStreamOutput"));
            objectiveC.objc_registerClassPair(outputClass);
            ObjectiveC.blocks.add(frameOutput);
        }
        streamOutput =
                objectiveC.objc_msgSend(objectiveC.objc_msgSend(outputClass, alloc), init);
        capturesByOutput.put(streamOutput, this);
        return streamOutput;
    }

    private Pointer awaitFrame() {
        long deadline = System.currentTimeMillis() + waitMillis;
        Pointer pixelBuffer;
        while ((pixelBuffer = latest.get()) == null) {
            if (System.currentTimeMillis() > deadline)
                return null;
            try {
                Thread.sleep(5);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return CoreFoundation.INSTANCE.CFRetain(pixelBuffer);
    }

    private static Pointer awaitCompletion(Pointer receiver, Pointer selector) {
        CompletableFuture<Pointer> answer = new CompletableFuture<>();
        completion.set(answer);
        objectiveC.objc_msgSend(receiver, selector, handlerBlock);
        try {
            return answer.get(waitMillis, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        catch (ExecutionException | TimeoutException e) {
            return null;
        }
    }

    /** Excluded from the stream, so the hints already on screen are not detected again. */
    private static Pointer ownApplication(Pointer content) {
        Pointer applications = objectiveC.objc_msgSend(content,
                objectiveC.sel_registerName("applications"));
        long applicationCount =
                Pointer.nativeValue(objectiveC.objc_msgSend(applications, count));
        Pointer processId = objectiveC.sel_registerName("processID");
        int ownProcessId = (int) ProcessHandle.current().pid();
        for (long index = 0; index < applicationCount; index++) {
            Pointer application =
                    objectiveC.objc_msgSend(applications, objectAtIndex, index);
            if (ObjectiveC.ReturningInt.INSTANCE.objc_msgSend(application, processId) ==
                ownProcessId)
                return objectiveC.objc_msgSend(objectiveC.objc_getClass("NSArray"),
                        objectiveC.sel_registerName("arrayWithObject:"), application);
        }
        return objectiveC.objc_msgSend(objectiveC.objc_getClass("NSArray"),
                objectiveC.sel_registerName("array"));
    }

    private static Pointer displayContaining(Pointer content, Rectangle bounds) {
        Pointer all = objectiveC.objc_msgSend(content,
                objectiveC.sel_registerName("displays"));
        long displayCount = Pointer.nativeValue(objectiveC.objc_msgSend(all, count));
        Pointer first = null;
        double scale = MacosScreens.scaleAt(new QPoint(bounds.x(), bounds.y()));
        for (long index = 0; index < displayCount; index++) {
            Pointer display = objectiveC.objc_msgSend(all, objectAtIndex, index);
            if (first == null)
                first = display;
            if (pixels(ObjectiveC.ReturningRect.INSTANCE.objc_msgSend(display, frame),
                    scale).contains(bounds.x(), bounds.y()))
                return display;
        }
        return first;
    }

    private byte[] rgb(Pointer pixelBuffer, Rectangle bounds, int scaledWidth,
                       int scaledHeight) {
        CoreVideo coreVideo = CoreVideo.INSTANCE;
        coreVideo.CVPixelBufferLockBaseAddress(pixelBuffer, CoreVideo.lockReadOnly);
        try {
            int rowPitch = (int) coreVideo.CVPixelBufferGetBytesPerRow(pixelBuffer);
            int height = (int) coreVideo.CVPixelBufferGetHeight(pixelBuffer);
            byte[] bgra = coreVideo.CVPixelBufferGetBaseAddress(pixelBuffer)
                                   .getByteArray(0, rowPitch * height);
            return DesktopCapture.boxDownscaledRgb(bgra, rowPitch,
                    new Rectangle(bounds.x() - displayBounds.x(),
                            bounds.y() - displayBounds.y(), bounds.width(),
                            bounds.height()), scaledWidth, scaledHeight);
        }
        finally {
            coreVideo.CVPixelBufferUnlockBaseAddress(pixelBuffer, CoreVideo.lockReadOnly);
        }
    }

}
