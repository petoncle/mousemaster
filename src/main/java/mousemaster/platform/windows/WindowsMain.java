package mousemaster.platform.windows;

import mousemaster.ApplicationOptions;
import mousemaster.ApplicationLauncher;

import java.io.IOException;

public class WindowsMain {

    public static void main(String[] args) throws InterruptedException, IOException {
        ApplicationLauncher.run(args, WindowsMain::createPlatform);
    }

    private static WindowsPlatform createPlatform(ApplicationOptions options) {
        return new WindowsPlatform(options.multipleInstancesAllowed(),
                options.keyRegurgitationEnabled());
    }

}
