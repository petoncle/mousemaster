package mousemaster.platform.macos;

import mousemaster.ApplicationLauncher;

import java.io.IOException;

public class MacosMain {

    public static void main(String[] args) throws InterruptedException, IOException {
        ApplicationLauncher.run(args, options -> new MacosPlatform(options.multipleInstancesAllowed(),
                options.keyRegurgitationEnabled()));
    }
}
