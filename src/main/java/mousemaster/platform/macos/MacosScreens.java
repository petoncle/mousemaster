package mousemaster.platform.macos;

import mousemaster.Screen;
import mousemaster.platform.Screens;

import java.util.Set;

public class MacosScreens implements Screens {

    @Override
    public Set<Screen> findScreens() {
        throw new UnsupportedOperationException("macOS display enumeration is not implemented yet");
    }
}
