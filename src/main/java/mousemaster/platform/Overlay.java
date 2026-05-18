package mousemaster.platform;

import mousemaster.Grid;
import mousemaster.Hint;
import mousemaster.HintMesh;
import mousemaster.HintMeshConfiguration;
import mousemaster.Indicator;
import mousemaster.Rectangle;
import mousemaster.Zoom;

import java.util.Set;
import java.time.Duration;

public interface Overlay {

    void update(double delta);

    void flushCache();

    void setTopmost();

    void setMessagePump(Runnable pump);

    void preWarmFontStyles(Set<HintMeshConfiguration> configs);

    void preWarmHintMeshWindows();

    Rectangle activeWindowRectangle(double widthPct, double heightPct,
                                    int topInset, int bottomInset,
                                    int leftInset, int rightInset);

    void setIndicator(Indicator indicator, boolean fadeAnimationEnabled,
                      Duration fadeAnimationDuration, boolean allowFade);

    void hideIndicator(boolean allowFade);

    void setGrid(Grid grid);

    void hideGrid();

    void setHintMesh(HintMesh hintMesh, Zoom zoom);

    void setHintMesh(HintMesh hintMesh, Zoom zoom, boolean hintMatch);

    void hideHintMesh();

    void animateHintMatch(Hint hint);

    void setZoom(Zoom zoom);

    void startScreenshotZoomAnimation(Rectangle screenRect, Zoom beginZoom);

    void updateScreenshotZoom(Zoom zoom);

    void endScreenshotZoomAnimation(Zoom finalZoom);

    boolean waitForZoomBeforeRepainting();

    void setWaitForZoomBeforeRepainting(boolean value);

}
