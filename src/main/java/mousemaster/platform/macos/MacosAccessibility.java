package mousemaster.platform.macos;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import io.qt.core.QPoint;
import mousemaster.Rectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Needs the Accessibility permission; a child is borrowed from the array holding it. */
public final class MacosAccessibility {

    private static final ObjectiveC objectiveC = ObjectiveC.INSTANCE;
    private static final ApplicationServices applicationServices =
            ApplicationServices.INSTANCE;
    private static final CoreFoundation coreFoundation = CoreFoundation.INSTANCE;

    private static final Pointer sharedWorkspace =
            objectiveC.sel_registerName("sharedWorkspace");
    private static final Pointer frontmostApplication =
            objectiveC.sel_registerName("frontmostApplication");
    private static final Pointer processIdentifier =
            objectiveC.sel_registerName("processIdentifier");

    private static final int ownProcessId = (int) ProcessHandle.current().pid();
    private static int lastForeignProcessId;

    private static final Pointer focusedWindowAttribute = string("AXFocusedWindow");
    private static final Pointer positionAttribute = string("AXPosition");
    private static final Pointer sizeAttribute = string("AXSize");
    private static final Pointer childrenAttribute = string("AXChildren");
    private static final Pointer roleAttribute = string("AXRole");
    private static final Pointer windowsAttribute = string("AXWindows");
    private static final Pointer enhancedUserInterfaceAttribute =
            string("AXEnhancedUserInterface");

    /** These answer no action: clicking a text input focuses it without that being one. */
    private static final Set<String> actionableRoles =
            Set.of("AXTextField", "AXTextArea", "AXComboBox", "AXSecureTextField");

    private static final Set<String> actionableActions = Set.of("AXPress", "AXOpen");
    private static final String scrollAreaRole = "AXScrollArea";
    /** A row answers nothing, so it is a target only where it holds nothing that answers. */
    private static final String rowRole = "AXRow";

    /** An accessibility tree can nest deeply enough to be worth bounding. */
    private static final int maxDepth = 16;

    private MacosAccessibility() {
    }

    private static Pointer string(String value) {
        return coreFoundation.CFStringCreateWithCString(null, value,
                CoreFoundation.utf8Encoding);
    }

    /** The frames of the actionable elements of the focused window. */
    public static List<Rectangle> focusedWindowElementFrames() {
        Pointer window = focusedWindow();
        if (window == null)
            return List.of();
        try {
            List<Rectangle> frames = new ArrayList<>();
            collect(window, 0, null, frames);
            return frames.stream().distinct().toList();
        }
        finally {
            coreFoundation.CFRelease(window);
        }
    }

    /** The frames of the actionable elements of the window of that process at bounds. */
    public static List<Rectangle> windowElementFrames(int processId, Rectangle bounds) {
        Pointer axApplication =
                applicationServices.AXUIElementCreateApplication(processId);
        if (axApplication == null)
            return List.of();
        try {
            Pointer windows = copy(axApplication, windowsAttribute);
            if (windows == null)
                return List.of();
            try {
                List<Rectangle> frames = new ArrayList<>();
                long count = coreFoundation.CFArrayGetCount(windows);
                for (long index = 0; index < count; index++) {
                    Pointer window = coreFoundation.CFArrayGetValueAtIndex(windows, index);
                    if (bounds.equals(frame(window)))
                        collect(window, 0, null, frames);
                }
                return frames.stream().distinct().toList();
            }
            finally {
                coreFoundation.CFRelease(windows);
            }
        }
        finally {
            coreFoundation.CFRelease(axApplication);
        }
    }

    public static Rectangle focusedWindowFrame() {
        Pointer window = focusedWindow();
        if (window == null)
            return null;
        try {
            return frame(window);
        }
        finally {
            coreFoundation.CFRelease(window);
        }
    }

    /** The window, then whatever scrolls inside it: a row scrolled out of view is still reported. */
    private static void collect(Pointer element, int depth, Rectangle clip,
                                List<Rectangle> frames) {
        if (depth > maxDepth)
            return;
        String role = role(element);
        if (depth == 0 || scrollAreaRole.equals(role)) {
            Rectangle container = frame(element);
            if (container != null)
                clip = clip == null ? container : clip.intersection(container);
        }
        boolean actionable = depth > 0 && actionable(element, role);
        boolean row = depth > 0 && !actionable && rowRole.equals(role);
        int rowFrame = -1;
        if (actionable || row) {
            Rectangle frame = frame(element);
            if (frame != null) {
                Rectangle visible = clip == null ? frame : frame.intersection(clip);
                if (visible.width() > 0 && visible.height() > 0) {
                    if (row)
                        rowFrame = frames.size();
                    frames.add(visible);
                }
            }
        }
        Pointer children = copy(element, childrenAttribute);
        if (children != null) {
            try {
                long count = coreFoundation.CFArrayGetCount(children);
                for (long index = 0; index < count; index++)
                    collect(coreFoundation.CFArrayGetValueAtIndex(children, index), depth + 1,
                            clip, frames);
            }
            finally {
                coreFoundation.CFRelease(children);
            }
        }
        if (rowFrame != -1 && frames.size() > rowFrame + 1)
            frames.remove(rowFrame);
    }

    private static boolean actionable(Pointer element, String role) {
        if (actionableRoles.contains(role))
            return true;
        PointerByReference names = new PointerByReference();
        if (applicationServices.AXUIElementCopyActionNames(element, names) != 0 ||
            names.getValue() == null)
            return false;
        try {
            long count = coreFoundation.CFArrayGetCount(names.getValue());
            for (long index = 0; index < count; index++) {
                if (actionableActions.contains(text(coreFoundation.CFArrayGetValueAtIndex(
                        names.getValue(), index))))
                    return true;
            }
            return false;
        }
        finally {
            coreFoundation.CFRelease(names.getValue());
        }
    }

    /**
     * mousemaster itself is frontmost while an overlay is up, so the application asked about
     * is the last one that was not mousemaster. Zero until one has been seen.
     */
    public static int frontmostProcessId() {
        Pointer workspace = objectiveC.objc_msgSend(
                objectiveC.objc_getClass("NSWorkspace"), sharedWorkspace);
        Pointer application = objectiveC.objc_msgSend(workspace, frontmostApplication);
        if (application != null) {
            int processId = ObjectiveC.ReturningInt.INSTANCE.objc_msgSend(application,
                    processIdentifier);
            if (processId != ownProcessId) {
                if (processId != lastForeignProcessId)
                    enhanceUserInterface(processId);
                lastForeignProcessId = processId;
            }
        }
        return lastForeignProcessId;
    }

    /** A Chromium browser answers only its toolbar until asked for the page tree, which takes
     *  a moment to build. Off the polling thread because a busy application answers slowly. */
    private static void enhanceUserInterface(int processId) {
        Thread thread = new Thread(() -> {
            Pointer axApplication =
                    applicationServices.AXUIElementCreateApplication(processId);
            if (axApplication == null)
                return;
            try {
                applicationServices.AXUIElementSetAttributeValue(axApplication,
                        enhancedUserInterfaceAttribute, CoreFoundation.booleanTrue);
            }
            finally {
                coreFoundation.CFRelease(axApplication);
            }
        }, "ax-enhance");
        thread.setDaemon(true);
        thread.start();
    }

    /** Null when no window is focused. */
    private static Pointer focusedWindow() {
        int processId = frontmostProcessId();
        if (processId == 0)
            return null;
        Pointer axApplication =
                applicationServices.AXUIElementCreateApplication(processId);
        if (axApplication == null)
            return null;
        try {
            return copy(axApplication, focusedWindowAttribute);
        }
        finally {
            coreFoundation.CFRelease(axApplication);
        }
    }

    /** The Accessibility API answers in points, and the hints it feeds are in pixels. */
    private static Rectangle frame(Pointer element) {
        double[] position = value(element, positionAttribute,
                ApplicationServices.cgPointType);
        double[] size = value(element, sizeAttribute, ApplicationServices.cgSizeType);
        if (position == null || size == null)
            return null;
        QPoint topLeft = MacosScreens.pixelPoint(
                new QPoint((int) position[0], (int) position[1]));
        double scale = MacosScreens.findActiveScreen(topLeft).scale();
        return new Rectangle(topLeft.x(), topLeft.y(),
                (int) Math.round(size[0] * scale), (int) Math.round(size[1] * scale));
    }

    private static String role(Pointer element) {
        Pointer role = copy(element, roleAttribute);
        if (role == null)
            return "";
        try {
            return text(role);
        }
        finally {
            coreFoundation.CFRelease(role);
        }
    }

    private static String text(Pointer string) {
        byte[] buffer = new byte[64];
        return coreFoundation.CFStringGetCString(string, buffer, buffer.length,
                CoreFoundation.utf8Encoding) ? Native.toString(buffer) : "";
    }

    private static Pointer copy(Pointer element, Pointer attribute) {
        PointerByReference value = new PointerByReference();
        return applicationServices.AXUIElementCopyAttributeValue(element, attribute,
                value) == 0 ? value.getValue() : null;
    }

    private static double[] value(Pointer element, Pointer attribute, int type) {
        Pointer value = copy(element, attribute);
        if (value == null)
            return null;
        try {
            double[] components = new double[2];
            return applicationServices.AXValueGetValue(value, type, components) ?
                    components : null;
        }
        finally {
            coreFoundation.CFRelease(value);
        }
    }

}
