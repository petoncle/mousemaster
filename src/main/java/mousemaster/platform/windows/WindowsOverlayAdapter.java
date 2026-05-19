package mousemaster.platform.windows;

import mousemaster.*;

import mousemaster.platform.Overlay;

import java.time.Duration;
import java.util.Set;

public class WindowsOverlayAdapter implements Overlay {

    @Override
    public void update(double delta) {
        WindowsOverlay.update(delta);
    }

    @Override
    public void flushCache() {
        WindowsOverlay.flushCache();
    }

    @Override
    public void setTopmost() {
        WindowsOverlay.setTopmost();
    }

    @Override
    public void setMessagePump(Runnable pump) {
        WindowsOverlay.setMessagePump(pump);
    }

    @Override
    public void preWarmFontStyles(Set<HintMeshConfiguration> configs) {
        WindowsOverlay.preWarmFontStyles(configs);
    }

    @Override
    public void preWarmHintMeshWindows() {
        WindowsOverlay.preWarmHintMeshWindows();
    }

    @Override
    public Rectangle activeWindowRectangle(double widthPct, double heightPct,
                                           int topInset, int bottomInset,
                                           int leftInset, int rightInset) {
        return WindowsOverlay.activeWindowRectangle(widthPct, heightPct,
                topInset, bottomInset, leftInset, rightInset);
    }

    @Override
    public void setIndicator(Indicator indicator, boolean fadeAnimationEnabled,
                             Duration fadeAnimationDuration, boolean allowFade) {
        WindowsOverlay.setIndicator(indicator, fadeAnimationEnabled,
                fadeAnimationDuration, allowFade);
    }

    @Override
    public void hideIndicator(boolean allowFade) {
        WindowsOverlay.hideIndicator(allowFade);
    }

    @Override
    public void setGrid(Grid grid) {
        WindowsOverlay.setGrid(grid);
    }

    @Override
    public void hideGrid() {
        WindowsOverlay.hideGrid();
    }

    @Override
    public void setHintMesh(HintMesh hintMesh, Zoom zoom) {
        WindowsOverlay.setHintMesh(hintMesh, zoom);
    }

    @Override
    public void setHintMesh(HintMesh hintMesh, Zoom zoom, boolean hintMatch) {
        WindowsOverlay.setHintMesh(hintMesh, zoom, hintMatch);
    }

    @Override
    public void hideHintMesh() {
        WindowsOverlay.hideHintMesh();
    }

    @Override
    public void animateHintMatch(Hint hint) {
        WindowsOverlay.animateHintMatch(hint);
    }

    @Override
    public void setZoom(Zoom zoom) {
        WindowsOverlay.setZoom(zoom);
    }

    @Override
    public void startScreenshotZoomAnimation(Rectangle screenRect, Zoom beginZoom) {
        WindowsOverlay.startScreenshotZoomAnimation(screenRect, beginZoom);
    }

    @Override
    public void updateScreenshotZoom(Zoom zoom) {
        WindowsOverlay.updateScreenshotZoom(zoom);
    }

    @Override
    public void endScreenshotZoomAnimation(Zoom finalZoom) {
        WindowsOverlay.endScreenshotZoomAnimation(finalZoom);
    }

    @Override
    public boolean waitForZoomBeforeRepainting() {
        return WindowsOverlay.waitForZoomBeforeRepainting;
    }

    @Override
    public void setWaitForZoomBeforeRepainting(boolean value) {
        WindowsOverlay.waitForZoomBeforeRepainting = value;
    }

}
