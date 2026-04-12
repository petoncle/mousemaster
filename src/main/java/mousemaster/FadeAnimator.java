package mousemaster;

import io.qt.core.QEasingCurve;
import io.qt.core.QMetaObject;
import io.qt.core.QVariantAnimation;

import java.time.Duration;
import java.util.function.Consumer;

class FadeAnimator {

    private static final Easing FADE_EASING = new Easing.Smootherstep();

    private final Consumer<Double> applyOpacity;
    private final Runnable onFadeOutComplete;
    private final boolean enabled;
    private final Duration duration;

    private QVariantAnimation animation;
    private boolean fadingOut;

    // Store callback instances as fields to prevent GC.
    private ValueChanged valueChangedCallback;
    private Finished finishedCallback;

    FadeAnimator(Consumer<Double> applyOpacity, Runnable onFadeOutComplete,
                 boolean enabled, Duration duration) {
        this.applyOpacity = applyOpacity;
        this.onFadeOutComplete = onFadeOutComplete;
        this.enabled = enabled;
        this.duration = duration;
    }

    boolean isEnabled() {
        return enabled;
    }

    boolean isFadingOut() {
        return fadingOut;
    }

    void startFadeIn() {
        disposeAnimation();
        animation = createAnimation(false);
        animation.start();
    }

    /**
     * Starts fade-out if enabled and not already fading out.
     * Returns true if the caller should defer the hide (fade-out started or already in progress).
     */
    boolean shouldDeferHide() {
        if (!enabled)
            return false;
        if (fadingOut)
            return true;
        // Cancel any in-progress fade-in.
        disposeAnimation();
        fadingOut = true;
        animation = createAnimation(true);
        animation.start();
        return true;
    }

    void cancel() {
        disposeAnimation();
        fadingOut = false;
    }

    void cancelAndResetOpacity() {
        cancel();
        applyOpacity.accept(1.0);
    }

    private QVariantAnimation createAnimation(boolean fadeOut) {
        QVariantAnimation anim = new QVariantAnimation();
        anim.setStartValue(0.0);
        anim.setEndValue(1.0);
        anim.setDuration((int) duration.toMillis());
        anim.setEasingCurve(QEasingCurve.Type.Linear);
        valueChangedCallback = new ValueChanged(fadeOut);
        anim.valueChanged.connect(valueChangedCallback);
        finishedCallback = new Finished(fadeOut);
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

        private final boolean fadeOut;

        ValueChanged(boolean fadeOut) {
            this.fadeOut = fadeOut;
        }

        @Override
        public void invoke(Object arg) {
            double t = FADE_EASING.apply((Double) arg);
            double opacity = fadeOut ? 1.0 - t : t;
            applyOpacity.accept(opacity);
        }
    }

    private class Finished implements QMetaObject.Slot0 {

        private final boolean fadeOut;

        Finished(boolean fadeOut) {
            this.fadeOut = fadeOut;
        }

        @Override
        public void invoke() {
            if (fadeOut) {
                onFadeOutComplete.run();
            }
            else {
                applyOpacity.accept(1.0);
            }
        }
    }

}
