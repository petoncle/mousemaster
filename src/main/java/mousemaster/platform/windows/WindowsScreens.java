package mousemaster.platform.windows;

import mousemaster.*;

import mousemaster.platform.Screens;

import java.util.Set;

public class WindowsScreens implements Screens {

    @Override
    public Set<Screen> findScreens() {
        return WindowsScreen.findScreens();
    }

}
