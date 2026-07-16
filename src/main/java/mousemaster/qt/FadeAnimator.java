package mousemaster.qt;

import io.qt.core.QEasingCurve;
import io.qt.core.QMetaObject;
import io.qt.core.QVariantAnimation;

import java.time.Duration;
import java.util.function.Consumer;

public class FadeAnimator {

    private final Consumer<Double> applyOpacity;
    private final Runnable onFadeOutComplete;
    private final boolean enabled;
    private final Duration duration;

    private QVariantAnimation animation;
    private boolean fadingOut;
    private double currentOpacity = 1.0; // Tracked so an interrupted fade continues from here.

    // Store callback instances as fields to prevent GC.
    private ValueChanged valueChangedCallback;
    private Finished finishedCallback;

    public FadeAnimator(Consumer<Double> applyOpacity, Runnable onFadeOutComplete,
                 boolean enabled, Duration duration) {
        this.applyOpacity = applyOpacity;
        this.onFadeOutComplete = onFadeOutComplete;
        this.enabled = enabled;
        this.duration = duration;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isFadingOut() {
        return fadingOut;
    }

    public void startFadeIn() {
        disposeAnimation();
        currentOpacity = 0.0;
        animation = createAnimation(0.0, 1.0);
        animation.start();
    }

    /**
     * Starts fade-out if enabled and not already fading out.
     * Returns true if the caller should defer the hide (fade-out started or already in progress).
     */
    public boolean shouldDeferHide() {
        if (!enabled)
            return false;
        if (fadingOut)
            return true;
        // Cancel any in-progress fade-in and fade out from wherever it got to.
        disposeAnimation();
        fadingOut = true;
        animation = createAnimation(currentOpacity, 0.0);
        animation.start();
        return true;
    }

    public void cancel() {
        disposeAnimation();
        fadingOut = false;
    }

    public void cancelAndResetOpacity() {
        cancel();
        currentOpacity = 1.0;
        applyOpacity.accept(1.0);
    }

    private QVariantAnimation createAnimation(double from, double to) {
        QVariantAnimation anim = new QVariantAnimation();
        anim.setStartValue(from);
        anim.setEndValue(to);
        anim.setDuration((int) Math.round(duration.toMillis() * Math.abs(to - from)));
        anim.setEasingCurve(QEasingCurve.Type.InOutQuad);
        valueChangedCallback = new ValueChanged();
        anim.valueChanged.connect(valueChangedCallback);
        finishedCallback = new Finished(to);
        anim.finished.connect(finishedCallback);
        return anim;
    }

    private void disposeAnimation() {
        if (animation != null) {
            animation.stop();
            animation.disposeLater();
            animation = null;
        }
    }

    private class ValueChanged implements QMetaObject.Slot1<Object> {

        @Override
        public void invoke(Object arg) {
            currentOpacity = (Double) arg;
            applyOpacity.accept(currentOpacity);
        }
    }

    private class Finished implements QMetaObject.Slot0 {

        private final double to;

        Finished(double to) {
            this.to = to;
        }

        @Override
        public void invoke() {
            currentOpacity = to;
            if (to == 0.0)
                onFadeOutComplete.run();
            else
                applyOpacity.accept(1.0);
        }
    }

}
