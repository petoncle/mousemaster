package mousemaster.platform.macos;

import com.sun.jna.Callback;
import com.sun.jna.CallbackReference;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import io.qt.core.Qt;
import io.qt.widgets.QWidget;
import mousemaster.Rectangle;
import mousemaster.Zoom;

import java.util.concurrent.atomic.AtomicReference;

/** Below the other overlays, which the stream leaves out by excluding mousemaster itself. */
public final class MacosZoomRenderer {

    private static final ObjectiveC objectiveC = ObjectiveC.INSTANCE;
    private static final Pointer alloc = objectiveC.sel_registerName("alloc");
    private static final Pointer init = objectiveC.sel_registerName("init");
    private static final Pointer layer = objectiveC.sel_registerName("layer");
    private static final Pointer setWantsLayer =
            objectiveC.sel_registerName("setWantsLayer:");
    private static final Pointer addSublayer =
            objectiveC.sel_registerName("addSublayer:");
    private static final Pointer setFrame = objectiveC.sel_registerName("setFrame:");
    private static final Pointer setContents = objectiveC.sel_registerName("setContents:");
    private static final Pointer setContentsRect =
            objectiveC.sel_registerName("setContentsRect:");
    private static final Pointer count = objectiveC.sel_registerName("count");
    private static final Pointer objectAtIndex =
            objectiveC.sel_registerName("objectAtIndex:");
    private static final Pointer processId =
            objectiveC.sel_registerName("processID");

    private static final int ownProcessId = (int) ProcessHandle.current().pid();

    private final QWidget widget = new QWidget();

    private final AtomicReference<Pointer> captured = new AtomicReference<>();

    private Pointer contentLayer;
    private Rectangle windowRectangle;
    private boolean showing;
    private boolean streaming;

    /** The stream hands its frames to an instance of a class built here, so it needs one. */
    private final ObjectiveC.FrameOutput frameOutput = (self, selector, stream, sampleBuffer,
                                                        type) -> {
        Pointer pixelBuffer = CoreMedia.INSTANCE.CMSampleBufferGetImageBuffer(sampleBuffer);
        if (pixelBuffer == null)
            return;
        Pointer dropped =
                captured.getAndSet(CoreFoundation.INSTANCE.CFRetain(pixelBuffer));
        if (dropped != null)
            CoreFoundation.INSTANCE.CFRelease(dropped);
    };

    private final ObjectiveC.BlockHandler contentHandler = (block, content, error) -> {
        if (content != null)
            startStream(content);
    };

    private final ObjectiveC.BlockHandler startedHandler = (block, error, unused) -> {
    };

    public MacosZoomRenderer() {
        widget.setWindowFlags(Qt.WindowType.FramelessWindowHint);
        // Showing the window before it holds a frame would flash whatever it last held.
        widget.setWindowOpacity(0);
    }

    public void render(Zoom zoom) {
        Rectangle screen = zoom.screenRectangle();
        if (!screen.equals(windowRectangle)) {
            hide();
            widget.setGeometry(screen.x(), screen.y(), screen.width(), screen.height());
            widget.show();
            createContentLayer(screen);
            windowRectangle = screen;
        }
        requestStream();
        objectiveC.objc_msgSend(contentLayer, setContentsRect, magnifiedPart(zoom, screen));
        Pointer pixelBuffer = captured.getAndSet(null);
        if (pixelBuffer == null)
            return;
        objectiveC.objc_msgSend(contentLayer, setContents,
                CoreVideo.INSTANCE.CVPixelBufferGetIOSurface(pixelBuffer));
        CoreFoundation.INSTANCE.CFRelease(pixelBuffer);
        if (!showing) {
            showing = true;
            widget.setWindowOpacity(1);
        }
    }

    /** The whole display is streamed, so magnifying is showing the part of it under the zoom. */
    private static CoreGraphics.CGRect.ByValue magnifiedPart(Zoom zoom, Rectangle screen) {
        return new CoreGraphics.CGRect.ByValue(
                (zoom.unzoomedX(screen.x()) - screen.x()) / screen.width(),
                (zoom.unzoomedY(screen.y()) - screen.y()) / screen.height(),
                1 / zoom.percent(), 1 / zoom.percent());
    }

    /**
     * One stream, asked for once: it is authorized once, where a capture per frame is a request
     * per frame and macOS asks about every one of them.
     */
    private void requestStream() {
        if (streaming)
            return;
        streaming = true;
        NativeLibrary.getInstance(
                "/System/Library/Frameworks/ScreenCaptureKit.framework/ScreenCaptureKit");
        objectiveC.objc_msgSend(objectiveC.objc_getClass("SCShareableContent"),
                objectiveC.sel_registerName("getShareableContentWithCompletionHandler:"),
                ObjectiveC.block(contentHandler));
    }

    private void startStream(Pointer content) {
        Pointer display = first(objectiveC.objc_msgSend(content,
                objectiveC.sel_registerName("displays")));
        if (display == null)
            return;
        Pointer filter = objectiveC.objc_msgSend(
                objectiveC.objc_msgSend(objectiveC.objc_getClass("SCContentFilter"), alloc),
                objectiveC.sel_registerName(
                        "initWithDisplay:excludingApplications:exceptingWindows:"),
                display, ownApplication(content),
                objectiveC.objc_msgSend(objectiveC.objc_getClass("NSArray"),
                        objectiveC.sel_registerName("array")));
        Pointer configuration = objectiveC.objc_msgSend(objectiveC.objc_msgSend(
                objectiveC.objc_getClass("SCStreamConfiguration"), alloc), init);
        objectiveC.objc_msgSend(configuration, objectiveC.sel_registerName("setWidth:"),
                windowRectangle.width());
        objectiveC.objc_msgSend(configuration, objectiveC.sel_registerName("setHeight:"),
                windowRectangle.height());
        Pointer stream = objectiveC.objc_msgSend(
                objectiveC.objc_msgSend(objectiveC.objc_getClass("SCStream"), alloc),
                objectiveC.sel_registerName("initWithFilter:configuration:delegate:"), filter,
                configuration, null);
        if (stream == null)
            return;
        ObjectiveC.ReturningBoolean.INSTANCE.objc_msgSend(stream,
                objectiveC.sel_registerName("addStreamOutput:type:sampleHandlerQueue:error:"),
                frameOutput(), 0,
                Libc.INSTANCE.dispatch_queue_create("mousemaster.zoom", null), null);
        objectiveC.objc_msgSend(stream,
                objectiveC.sel_registerName("startCaptureWithCompletionHandler:"),
                ObjectiveC.block(startedHandler));
    }

    /** Excluded from the stream, so the overlays drawn above the zoom stay out of it. */
    private Pointer ownApplication(Pointer content) {
        Pointer applications = objectiveC.objc_msgSend(content,
                objectiveC.sel_registerName("applications"));
        long applicationCount = Pointer.nativeValue(
                objectiveC.objc_msgSend(applications, count));
        for (long index = 0; index < applicationCount; index++) {
            Pointer application = objectiveC.objc_msgSend(applications, objectAtIndex, index);
            if (ObjectiveC.ReturningInt.INSTANCE.objc_msgSend(application,
                    processId) == ownProcessId)
                return objectiveC.objc_msgSend(objectiveC.objc_getClass("NSArray"),
                        objectiveC.sel_registerName("arrayWithObject:"), application);
        }
        return objectiveC.objc_msgSend(objectiveC.objc_getClass("NSArray"),
                objectiveC.sel_registerName("array"));
    }

    private static Pointer first(Pointer array) {
        return Pointer.nativeValue(objectiveC.objc_msgSend(array, count)) == 0 ? null :
                objectiveC.objc_msgSend(array, objectAtIndex, 0);
    }

    private Pointer streamOutput;

    private Pointer frameOutput() {
        if (streamOutput != null)
            return streamOutput;
        Pointer outputClass = objectiveC.objc_allocateClassPair(
                objectiveC.objc_getClass("NSObject"), "MousemasterStreamOutput", 0);
        objectiveC.class_addMethod(outputClass,
                objectiveC.sel_registerName("stream:didOutputSampleBuffer:ofType:"),
                CallbackReference.getFunctionPointer(frameOutput), "v@:@@q");
        objectiveC.class_addProtocol(outputClass,
                objectiveC.objc_getProtocol("SCStreamOutput"));
        objectiveC.objc_registerClassPair(outputClass);
        ObjectiveC.blocks.add(frameOutput);
        return streamOutput =
                objectiveC.objc_msgSend(objectiveC.objc_msgSend(outputClass, alloc), init);
    }

    /** Kept shown but clear, the way the layered zoom window keeps its place on Windows. */
    public void hide() {
        showing = false;
        widget.setWindowOpacity(0);
    }

    private void createContentLayer(Rectangle screen) {
        Pointer view = new Pointer(widget.winId());
        objectiveC.objc_msgSend(view, setWantsLayer, 1);
        if (contentLayer == null) {
            contentLayer = objectiveC.objc_msgSend(objectiveC.objc_msgSend(
                    objectiveC.objc_getClass("CALayer"), alloc), init);
            objectiveC.objc_msgSend(objectiveC.objc_msgSend(view, layer), addSublayer,
                    contentLayer);
            MacosWindow.applyOverlayProperties(widget, MacosWindow.belowOverlaysLevel);
        }
        objectiveC.objc_msgSend(contentLayer, setFrame,
                new CoreGraphics.CGRect.ByValue(0, 0, screen.width(), screen.height()));
    }

}
