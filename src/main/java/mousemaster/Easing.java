package mousemaster;

public sealed interface Easing {

    double apply(double t);

    record Polynomial(double exponent) implements Easing {
        public double apply(double t) { return Math.pow(t, exponent); }
    }

    record Smoothstep() implements Easing {
        public double apply(double t) { return t * t * (3 - 2 * t); }
    }

    record Smootherstep() implements Easing {
        public double apply(double t) { return t * t * t * (t * (6 * t - 15) + 10); }
    }

    record Logarithmic() implements Easing {
        public double apply(double t) { return Math.log(1 + t) / Math.log(2); }
    }

    record Exponential() implements Easing {
        public double apply(double t) { return (Math.exp(t) - 1) / (Math.E - 1); }
    }

}
