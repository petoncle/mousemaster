package mousemaster.platform.macos;

import mousemaster.platform.Console;

/** The terminal belongs to another application, and an app bundle has no console. */
public class MacosConsole implements Console {

    @Override
    public void show() {
    }

    @Override
    public void hide() {
    }

}
