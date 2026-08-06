package mousemaster.platform.macos;

import com.sun.jna.Pointer;
import mousemaster.App;
import mousemaster.platform.ActiveAppFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/** NSWorkspace refreshes from the run loop Qt pumps: unpumped it returns the last app. */
public class MacosActiveAppFinder implements ActiveAppFinder {

    private static final Logger logger =
            LoggerFactory.getLogger(MacosActiveAppFinder.class);

    private static final ObjectiveC objectiveC = ObjectiveC.INSTANCE;
    private static final Pointer runningApplicationWithProcessIdentifier =
            objectiveC.sel_registerName("runningApplicationWithProcessIdentifier:");
    private static final Pointer executableUrl =
            objectiveC.sel_registerName("executableURL");
    private static final Pointer lastPathComponent =
            objectiveC.sel_registerName("lastPathComponent");
    private static final Pointer utf8String = objectiveC.sel_registerName("UTF8String");

    private static final App unknown = new App("");

    private App lastApp;

    @Override
    public App activeApp() {
        // An application coming to the front is frontmost for a poll or two before it resolves
        // to a name, and no alias matches an empty one: the application before it is the better
        // answer than none at all.
        String name = frontmostExecutableName();
        App app = name.isEmpty() && lastApp != null ? lastApp : new App(name);
        if (!Objects.equals(app, lastApp)) {
            logger.debug(lastApp == null ? "Detected active app " + app.executableName() :
                    "Detected active app change from " + lastApp.executableName() +
                    " to " + app.executableName());
            lastApp = app;
        }
        return app;
    }

    private String frontmostExecutableName() {
        int processId = MacosAccessibility.frontmostProcessId();
        if (processId == 0)
            return unknown.executableName();
        // Looked up here, not cached: the class is not registered until AppKit is loaded.
        Pointer application = objectiveC.objc_msgSend(
                objectiveC.objc_getClass("NSRunningApplication"),
                runningApplicationWithProcessIdentifier, processId);
        if (application == null)
            return unknown.executableName();
        Pointer url = objectiveC.objc_msgSend(application, executableUrl);
        if (url == null)
            return unknown.executableName();
        Pointer name = objectiveC.objc_msgSend(url, lastPathComponent);
        Pointer utf8 = objectiveC.objc_msgSend(name, utf8String);
        return utf8 == null ? unknown.executableName() : utf8.getString(0);
    }

}
