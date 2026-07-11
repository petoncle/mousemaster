package mousemaster.platform.macos;

import mousemaster.App;
import mousemaster.platform.ActiveAppFinder;

public class MacosActiveAppFinder implements ActiveAppFinder {

    @Override
    public App activeApp() {
        return new App("unknown.macos.app");
    }
}
