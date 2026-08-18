package mousemaster.platform;

import mousemaster.Grid;
import mousemaster.Hint;
import mousemaster.HintMesh;
import mousemaster.HintMeshConfiguration;
import mousemaster.IndicatorConfiguration;
import mousemaster.Rectangle;
import mousemaster.Zoom;

import java.util.Set;

public interface Overlay {

    void update(double delta);

    void flushCache();

    void setTopmost();

    void setMessagePump(Runnable pump);

    void preWarmFontsAndWindows(Set<HintMeshConfiguration> hintMeshConfigurations);

    void preWarmHintMesh(HintMesh hintMesh, Zoom zoom);

    Rectangle activeWindowRectangle(double widthPct, double heightPct,
                                    int topInset, int bottomInset,
                                    int leftInset, int rightInset);

    /** {@code indicator} is what to draw now, {@code transitionTo} what it is animating to:
     *  the window is made big enough for both and never shrinks, so it is never resized. */
    void setIndicator(IndicatorConfiguration indicator, IndicatorConfiguration transitionTo,
                      boolean allowFade, boolean includeCursorGlyph);

    void hideIndicator(boolean allowFade);

    void setGrid(Grid grid);

    void hideGrid();

    void setHintMesh(HintMesh hintMesh, Zoom zoom);

    void setHintMesh(HintMesh hintMesh, Zoom zoom, boolean hintMatch);

    void restoreHintMesh(HintMesh hintMesh, Zoom zoom);

    void hideHintMesh();

    boolean hintTransitionAnimating();

    void animateHintMatch(Hint hint);

    void setZoom(Zoom zoom);

    boolean waitForZoomBeforeRepainting();

    void setWaitForZoomBeforeRepainting(boolean value);
}
