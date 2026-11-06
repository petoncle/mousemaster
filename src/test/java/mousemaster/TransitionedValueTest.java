package mousemaster;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TransitionedValueTest {

    private static final Transition linear =
            new Transition(true, Duration.ofMillis(100), new Easing.Polynomial(1));
    private static final Transition smoothstep =
            new Transition(true, Duration.ofMillis(100), new Easing.Smoothstep());

    @Test
    void aValueTransitionsToItsEndOverTheDuration() {
        TransitionedValue value = new TransitionedValue(0);
        value.transitionTo(linear, 10);
        assertEquals(0, value.value(), 1e-9);
        value.update(0.05);
        assertEquals(5, value.value(), 1e-9);
        value.update(0.05);
        assertEquals(10, value.value(), 1e-9);
        assertFalse(value.transitioning());
    }

    @Test
    void anInterruptingTransitionKeepsThePositionAndTheSpeed() {
        TransitionedValue value = new TransitionedValue(0);
        value.transitionTo(smoothstep, 10);
        value.update(0.049);
        double before = value.value();
        value.update(0.001);
        double speedBefore = value.value() - before;
        double position = value.value();
        value.transitionTo(smoothstep, 0);
        assertEquals(position, value.value(), 1e-9);
        value.update(0.001);
        assertEquals(speedBefore, value.value() - position, 0.01);
        value.update(0.1);
        assertEquals(0, value.value(), 1e-9);
        assertFalse(value.transitioning());
    }

    @Test
    void aDisabledTransitionJumps() {
        TransitionedValue value = new TransitionedValue(0);
        value.transitionTo(smoothstep, 10);
        value.update(0.05);
        value.transitionTo(new Transition(false, Duration.ofMillis(100), new Easing.Smoothstep()),
                20);
        assertEquals(20, value.value(), 1e-9);
        assertFalse(value.transitioning());
    }

}
