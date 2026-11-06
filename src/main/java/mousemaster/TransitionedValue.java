package mousemaster;

import java.util.ArrayList;
import java.util.List;

public class TransitionedValue {

    private record RunningTransition(double change, Transition transition, double elapsed) {

        double progress() {
            return Math.min(1, elapsed / (transition.duration().toNanos() / 1e9));
        }

    }

    private double end;
    private final List<RunningTransition> runningTransitions = new ArrayList<>();

    public TransitionedValue(double end) {
        this.end = end;
    }

    public void transitionTo(Transition transition, double end) {
        if (!transition.enabled() || transition.duration().isZero()) {
            jumpTo(end);
            return;
        }
        if (end == this.end)
            return;
        runningTransitions.add(new RunningTransition(end - this.end, transition, 0));
        this.end = end;
    }

    public void jumpTo(double end) {
        runningTransitions.clear();
        this.end = end;
    }

    public void follow(double end) {
        this.end = end;
    }

    public void settle() {
        runningTransitions.clear();
    }

    public void update(double delta) {
        runningTransitions.replaceAll(runningTransition -> new RunningTransition(
                runningTransition.change, runningTransition.transition,
                runningTransition.elapsed + delta));
        runningTransitions.removeIf(runningTransition -> runningTransition.progress() == 1);
    }

    public double value() {
        double value = end;
        for (RunningTransition runningTransition : runningTransitions)
            value -= runningTransition.change *
                     (1 - runningTransition.transition.easing()
                                                      .apply(runningTransition.progress()));
        return value;
    }

    public boolean transitioning() {
        return !runningTransitions.isEmpty();
    }

}
