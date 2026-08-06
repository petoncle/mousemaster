package mousemaster;

public final class Os {

    public static final boolean macos = System.getProperty("os.name").startsWith("Mac");
    public static final boolean windows =
            System.getProperty("os.name").startsWith("Windows");

    private Os() {
    }

}
