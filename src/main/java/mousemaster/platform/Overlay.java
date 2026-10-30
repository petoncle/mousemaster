package mousemaster.platform;

import mousemaster.Hint;
import mousemaster.HintGridArea;
import mousemaster.HintMesh;
import mousemaster.HintMeshConfiguration;
import mousemaster.HintRectangles;
import mousemaster.IndicatorConfiguration;
import mousemaster.IndicatorPosition;
import mousemaster.Point;
import mousemaster.Rectangle;

import mousemaster.Zoom;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Overlay {

    void update(double delta);

    void flushCache();

    void setTopmost();

    void setMessagePump(Runnable pump);

    void preWarmFontsAndWindows(Set<HintMeshConfiguration> hintMeshConfigurations);

    void preWarmHintMesh(HintMesh hintMesh, Zoom zoom);

    Rectangle activeWindowRectangle();

    void setIndicator(IndicatorConfiguration indicator, Set<String> layerNamesToAnchor,
                      Map<IndicatorPosition, List<Point>> pointsByPosition,
                      Map<HintGridArea, Rectangle> rectangleByArea,
                      Map<Point, HintRectangles> hintRectanglesByPoint, boolean animating,
                      boolean includeOriginalCursor);

    /** The rectangle of the box drawn for the hint, in screen pixels, null when none is drawn. */
    Rectangle hintBoxRectangle(Hint hint);

    /** Captures the desktop inside bounds, excluding mousemaster's own windows. */
    DesktopCapture captureDesktop(Rectangle bounds, int scaledWidth, int scaledHeight);

    /** Builds a hint mesh that update() would otherwise only build on the next frame. */
    void runPendingHintMeshWork();

    void hideIndicator();

    void setHintMesh(HintMesh hintMesh, Zoom zoom);

    void setHintMesh(HintMesh hintMesh, Zoom zoom, boolean hintMatch);

    void restoreHintMesh(HintMesh hintMesh, Zoom zoom);

    void hideHintMesh();

    boolean hintTransitionAnimating();

    boolean hintMeshBuildPending();

    void animateHintMatch(Hint hint);

    boolean canZoom(Rectangle screenRectangle);

    void setZoom(Zoom zoom);

    boolean waitForZoomBeforeRepainting();

    void setWaitForZoomBeforeRepainting(boolean value);
}
