package mousemaster;

import java.util.List;

public record TransitionedRectangle(TransitionedValue left, TransitionedValue top,
                                    TransitionedValue right, TransitionedValue bottom) {

    public TransitionedRectangle(Rectangle rectangle) {
        this(new TransitionedValue(rectangle.x()), new TransitionedValue(rectangle.y()),
                new TransitionedValue(rectangle.x() + rectangle.width()),
                new TransitionedValue(rectangle.y() + rectangle.height()));
    }

    public void transitionTo(Transition transition, Rectangle rectangle) {
        left.transitionTo(transition, rectangle.x());
        top.transitionTo(transition, rectangle.y());
        right.transitionTo(transition, rectangle.x() + rectangle.width());
        bottom.transitionTo(transition, rectangle.y() + rectangle.height());
    }

    public void update(double delta) {
        edges().forEach(edge -> edge.update(delta));
    }

    public Rectangle rectangle() {
        int x = (int) Math.round(left.value());
        int y = (int) Math.round(top.value());
        return new Rectangle(x, y, (int) Math.round(right.value()) - x,
                (int) Math.round(bottom.value()) - y);
    }

    public boolean transitioning() {
        return edges().stream().anyMatch(TransitionedValue::transitioning);
    }

    public void settle() {
        edges().forEach(TransitionedValue::settle);
    }

    private List<TransitionedValue> edges() {
        return List.of(left, top, right, bottom);
    }

}
