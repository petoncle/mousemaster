package mousemaster.platform.macos;

import com.sun.jna.Pointer;
import io.qt.core.Qt;
import io.qt.widgets.QWidget;
import mousemaster.Rectangle;
import mousemaster.Zoom;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Below the other overlays, so capturing what is below leaves out both it and them. */
public final class MacosZoomRenderer {

    private static final ObjectiveC objectiveC = ObjectiveC.INSTANCE;
    private static final CoreGraphics coreGraphics = CoreGraphics.INSTANCE;
    private static final Pointer alloc = objectiveC.sel_registerName("alloc");
    private static final Pointer init = objectiveC.sel_registerName("init");
    private static final Pointer layer = objectiveC.sel_registerName("layer");
    private static final Pointer setWantsLayer =
            objectiveC.sel_registerName("setWantsLayer:");
    private static final Pointer addSublayer =
            objectiveC.sel_registerName("addSublayer:");
    private static final Pointer setFrame = objectiveC.sel_registerName("setFrame:");
    private static final Pointer setContents = objectiveC.sel_registerName("setContents:");
    private static final Pointer windowNumber =
            objectiveC.sel_registerName("windowNumber");

    private final QWidget widget = new QWidget();

    private final AtomicReference<Pointer> captured = new AtomicReference<>();

    private Pointer contentLayer;
    private int widgetWindowNumber;
    private Rectangle windowRectangle;
    private boolean showing;
    private volatile boolean capturing;

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
        startCapture(zoom, screen);
        Pointer image = captured.getAndSet(null);
        if (image == null)
            return;
        // The layer stretches whatever it holds over its frame, magnifying on the gpu.
        objectiveC.objc_msgSend(contentLayer, setContents, image);
        CoreFoundation.INSTANCE.CFRelease(image);
        if (!showing) {
            showing = true;
            widget.setWindowOpacity(1);
        }
    }

    /**
         * Screencapturekit waits on a reply the main thread must run, so capturing from it
         * deadlocks. One capture is in flight, and whichever render call finds the frame draws it.
         */
    private void startCapture(Zoom zoom, Rectangle screen) {
        if (capturing)
            return;
        capturing = true;
        CoreGraphics.CGRect.ByValue source =
                new CoreGraphics.CGRect.ByValue(zoom.unzoomedX(screen.x()),
                        zoom.unzoomedY(screen.y()), screen.width() / zoom.percent(),
                        screen.height() / zoom.percent());
        int window = widgetWindowNumber;
        CompletableFuture.runAsync(() -> {
            Pointer image = coreGraphics.CGWindowListCreateImage(source,
                    CoreGraphics.windowListOnScreenOnly |
                    CoreGraphics.windowListOnScreenBelowWindow, window,
                    CoreGraphics.windowImageDefault);
            Pointer dropped = captured.getAndSet(image);
            if (dropped != null)
                CoreFoundation.INSTANCE.CFRelease(dropped);
            capturing = false;
        });
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
            widgetWindowNumber = ObjectiveC.ReturningInt.INSTANCE.objc_msgSend(
                    MacosWindow.nsWindow(widget), windowNumber);
        }
        objectiveC.objc_msgSend(contentLayer, setFrame,
                new CoreGraphics.CGRect.ByValue(0, 0, screen.width(), screen.height()));
    }

}
