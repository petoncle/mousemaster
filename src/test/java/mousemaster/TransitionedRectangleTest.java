package mousemaster;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TransitionedRectangleTest {

    @Test
    void aRectangleTransitionsEachOfItsEdges() {
        TransitionedRectangle rectangle = new TransitionedRectangle(new Rectangle(0, 0, 10, 10));
        rectangle.transitionTo(
                new Transition(true, Duration.ofMillis(100), new Easing.Polynomial(1)),
                new Rectangle(10, 0, 20, 10));
        rectangle.update(0.05);
        assertEquals(new Rectangle(5, 0, 15, 10), rectangle.rectangle());
        rectangle.update(0.05);
        assertEquals(new Rectangle(10, 0, 20, 10), rectangle.rectangle());
        assertFalse(rectangle.transitioning());
    }

}
