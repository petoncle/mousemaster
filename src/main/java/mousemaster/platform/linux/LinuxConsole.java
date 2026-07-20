package mousemaster.platform.linux;

import mousemaster.platform.Console;

public class LinuxConsole implements Console {

    @Override
    public void show() {
        // No-op on Linux - app runs in terminal or as daemon
    }

    @Override
    public void hide() {
        // No-op on Linux - app runs in terminal or as daemon
    }

}
