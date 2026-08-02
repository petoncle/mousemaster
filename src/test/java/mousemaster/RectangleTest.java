package mousemaster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RectangleTest {

    @Test
    void unionContainsEveryRectangle() {
        assertEquals(new Rectangle(-10, 0, 30, 40),
                Rectangle.union(List.of(new Rectangle(0, 0, 20, 10),
                        new Rectangle(-10, 20, 5, 20))));
    }

    @Test
    void intersectionIsEmptyWhenApartOrEdgeToEdge() {
        assertEquals(new Rectangle(5, 5, 5, 5),
                new Rectangle(0, 0, 10, 10).intersection(
                        new Rectangle(5, 5, 10, 10)));
        assertTrue(new Rectangle(0, 0, 10, 10).intersection(new Rectangle(20, 0, 10, 10))
                                              .isEmpty());
        assertTrue(new Rectangle(0, 0, 10, 10).intersection(new Rectangle(10, 0, 10, 10))
                                              .isEmpty());
    }

    /** The ratio the hint selection is carried over on. */
    @Test
    void overlapRatioIsTheIntersectionOverTheLargerArea() {
        assertEquals(1, new Rectangle(0, 0, 10, 10).overlapRatio(
                new Rectangle(0, 0, 10, 10)));
        assertEquals(0.25, new Rectangle(0, 0, 10, 10).overlapRatio(
                new Rectangle(5, 5, 10, 10)));
        assertEquals(0, new Rectangle(0, 0, 10, 10).overlapRatio(
                new Rectangle(10, 10, 10, 10)));
    }

}
