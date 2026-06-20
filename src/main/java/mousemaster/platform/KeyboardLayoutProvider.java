package mousemaster.platform;

import mousemaster.KeyboardLayout;

import java.util.Set;

public interface KeyboardLayoutProvider {

    KeyboardLayout activeKeyboardLayout();

    KeyboardLayout byShortName(String shortName);

    KeyboardLayout byIdentifier(String identifier);

    Set<String> shortNames();
}
