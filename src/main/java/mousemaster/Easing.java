package mousemaster;

public sealed interface Easing {

    record Polynomial(double exponent) implements Easing {
    }

    record Smoothstep() implements Easing {
    }

    record Smootherstep() implements Easing {
    }

    record Logarithmic() implements Easing {
    }

    record Exponential() implements Easing {
    }

}
