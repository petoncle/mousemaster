package mousemaster.qt;

import mousemaster.Transition;
import mousemaster.TransitionedValue;

import java.util.function.Consumer;

public class FadeAnimator {

    private final Consumer<Double> applyOpacity;
    private final Runnable onFadeOutComplete;
    private final TransitionedValue opacity = new TransitionedValue(0);
    private boolean fadingOut;

    public FadeAnimator(Consumer<Double> applyOpacity, Runnable onFadeOutComplete) {
        this.applyOpacity = applyOpacity;
        this.onFadeOutComplete = onFadeOutComplete;
    }

    public boolean isFadingOut() {
        return fadingOut;
    }

    public boolean transitioning() {
        return opacity.transitioning();
    }

    public void fadeIn(Transition fade) {
        fadingOut = false;
        fadeTo(1, fade);
    }

    public void fadeOut(Transition fade) {
        if (fadingOut)
            return;
        fadingOut = true;
        fadeTo(0, fade);
    }

    public void cancel() {
        opacity.settle();
        fadingOut = false;
    }

    public void update(double delta) {
        if (!opacity.transitioning())
            return;
        opacity.update(delta);
        applyOpacity.accept(opacity.value());
        if (!opacity.transitioning())
            finish();
    }

    private void fadeTo(double endOpacity, Transition fade) {
        opacity.transitionTo(fade, endOpacity);
        applyOpacity.accept(opacity.value());
        if (!opacity.transitioning())
            finish();
    }

    private void finish() {
        if (fadingOut)
            onFadeOutComplete.run();
        else
            applyOpacity.accept(1.0);
    }

}
