package mousemaster.platform.windows;

import mousemaster.*;

import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import mousemaster.platform.UiAutomation;
import mousemaster.platform.UiAutomation.UiElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class WindowsUiAutomation implements UiAutomation {

    private static final Logger logger = LoggerFactory.getLogger(WindowsUiAutomation.class);

    private static final Guid.CLSID CLSID_CUIAutomation =
            new Guid.CLSID("FF48DBA4-60EF-4201-AA87-54103EEF594E");
    private static final Guid.IID IID_IUIAutomation =
            new Guid.IID("30CBE57D-D9D0-452A-AB13-7AC5AC4825EE");

    private static final int UIA_BoundingRectanglePropertyId = 30001;
    private static final int UIA_ControlTypePropertyId = 30003;
    private static final int UIA_IsOffscreenPropertyId = 30022;
    private static final int UIA_IsEnabledPropertyId = 30010;
    private static final int UIA_IsKeyboardFocusablePropertyId = 30009;
    private static final int UIA_IsInvokePatternAvailablePropertyId = 30031;
    private static final int UIA_IsExpandCollapsePatternAvailablePropertyId = 30028;
    private static final int UIA_IsTogglePatternAvailablePropertyId = 30041;
    private static final int UIA_IsSelectionItemPatternAvailablePropertyId = 30036;

    private static final int UIA_ButtonControlTypeId = 50000;

    private static final int TreeScope_Children = 2;
    private static final int TreeScope_Descendants = 4;

    // VARIANT constants
    private static final short VT_BOOL = 0x000B;
    private static final short VT_I4 = 3;
    private static final short VARIANT_TRUE = -1;
    private static final short VARIANT_FALSE = 0;

    private static Pointer automation;
    private static UIAutomationCondition cachedCondition;
    private static UIAutomationCacheRequest cachedCacheRequest;
    private static final Set<Integer> preWarmedProcessIds = new HashSet<>();

    private static final ExecutorService queryExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "uia-query");
                t.setDaemon(true);
                return t;
            });
    private static volatile boolean backgroundComInitialized;

    private static Memory createBoolVariantTrue() {
        Memory variant = new Memory(16);
        variant.clear();
        variant.setShort(0, VT_BOOL);
        variant.setShort(8, VARIANT_TRUE);
        return variant;
    }

    private static Memory createBoolVariantFalse() {
        Memory variant = new Memory(16);
        variant.clear();
        variant.setShort(0, VT_BOOL);
        variant.setShort(8, VARIANT_FALSE);
        return variant;
    }

    private static Memory createIntVariant(int value) {
        Memory variant = new Memory(16);
        variant.clear();
        variant.setShort(0, VT_I4);
        variant.setInt(8, value);
        return variant;
    }

    private static void ensureInitialized() {
        if (automation != null)
            return;
        // COM should already be initialized by Qt (STA).
        // Call defensively; tolerate S_FALSE (already initialized).
        WinNT.HRESULT hr = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL,
                Ole32.COINIT_APARTMENTTHREADED);
        int hrCode = hr.intValue();
        if (hrCode != W32Errors.S_OK.intValue() &&
            hrCode != W32Errors.S_FALSE.intValue()) {
            logger.warn("CoInitializeEx failed: 0x{}", Integer.toHexString(hrCode));
        }
        PointerByReference pAutomation = new PointerByReference();
        hr = Ole32.INSTANCE.CoCreateInstance(CLSID_CUIAutomation, null,
                WTypes.CLSCTX_INPROC_SERVER, IID_IUIAutomation, pAutomation);
        if (W32Errors.FAILED(hr))
            throw new RuntimeException(
                    "Failed to create IUIAutomation: 0x" +
                    Integer.toHexString(hr.intValue()));
        automation = pAutomation.getValue();
        UIAutomation uia = new UIAutomation(automation);
        cachedCondition = buildUiAutomationCondition(uia);
        if (cachedCondition == null) {
            logger.warn("Failed to create conditions, " +
                         "falling back to TrueCondition");
            cachedCondition = uia.createTrueCondition();
        }
        cachedCacheRequest = uia.createCacheRequest();
        if (cachedCacheRequest != null) {
            cachedCacheRequest.addProperty(UIA_BoundingRectanglePropertyId);
        }
    }

    /**
     * IsOffscreen=false AND IsEnabled=true
     * AND (IsKeyboardFocusable OR IsInvokePatternAvailable OR ControlType=Button
     *      OR IsExpandCollapsePatternAvailable OR IsTogglePatternAvailable
     *      OR IsSelectionItemPatternAvailable)
     */
    private static UIAutomationCondition buildUiAutomationCondition(UIAutomation uia) {
        Memory boolTrue = createBoolVariantTrue();
        Memory boolFalse = createBoolVariantFalse();
        UIAutomationCondition focusable = null, invokable = null,
                button = null, expandCollapse = null, toggle = null,
                selectionItem = null, onscreen = null, enabled = null;
        try {
            focusable = uia.createPropertyCondition(
                    UIA_IsKeyboardFocusablePropertyId, boolTrue);
            invokable = uia.createPropertyCondition(
                    UIA_IsInvokePatternAvailablePropertyId, boolTrue);
            button = uia.createPropertyCondition(
                    UIA_ControlTypePropertyId,
                    createIntVariant(UIA_ButtonControlTypeId));
            expandCollapse = uia.createPropertyCondition(
                    UIA_IsExpandCollapsePatternAvailablePropertyId, boolTrue);
            toggle = uia.createPropertyCondition(
                    UIA_IsTogglePatternAvailablePropertyId, boolTrue);
            selectionItem = uia.createPropertyCondition(
                    UIA_IsSelectionItemPatternAvailablePropertyId, boolTrue);
            onscreen = uia.createPropertyCondition(
                    UIA_IsOffscreenPropertyId, boolFalse);
            enabled = uia.createPropertyCondition(
                    UIA_IsEnabledPropertyId, boolTrue);
            if (focusable == null || invokable == null ||
                button == null || expandCollapse == null ||
                toggle == null || selectionItem == null ||
                onscreen == null || enabled == null)
                return null;
            UIAutomationCondition or1 =
                    uia.createOrCondition(focusable, invokable);
            if (or1 == null)
                return null;
            UIAutomationCondition or2 =
                    uia.createOrCondition(or1, button);
            or1.Release();
            if (or2 == null)
                return null;
            UIAutomationCondition or3 =
                    uia.createOrCondition(or2, expandCollapse);
            or2.Release();
            if (or3 == null)
                return null;
            UIAutomationCondition or4 =
                    uia.createOrCondition(or3, toggle);
            or3.Release();
            if (or4 == null)
                return null;
            UIAutomationCondition or5 =
                    uia.createOrCondition(or4, selectionItem);
            or4.Release();
            if (or5 == null)
                return null;
            UIAutomationCondition and1 =
                    uia.createAndCondition(or5, onscreen);
            or5.Release();
            if (and1 == null)
                return null;
            UIAutomationCondition result =
                    uia.createAndCondition(and1, enabled);
            and1.Release();
            return result;
        } finally {
            if (focusable != null)
                focusable.Release();
            if (invokable != null)
                invokable.Release();
            if (button != null)
                button.Release();
            if (expandCollapse != null)
                expandCollapse.Release();
            if (toggle != null)
                toggle.Release();
            if (selectionItem != null)
                selectionItem.Release();
            if (onscreen != null)
                onscreen.Release();
            if (enabled != null)
                enabled.Release();
        }
    }

    // UIA returns bounding rectangles in zoomed (physical) pixels, so the
    // threshold is multiplied by the window's scale at filter time.
    // 13 unzoomed px = 13 physical px at 100% scale, 40 physical px at 300%.
    private static final double MIN_DISTANCE_BETWEEN_HINTS_UNZOOMED = 13;

    private static boolean isTooCloseToExistingUiElements(List<UiElement> elements,
                                                          double x, double y,
                                                          double thresholdSquared) {
        for (UiElement e : elements) {
            double dx = e.centerX() - x;
            double dy = e.centerY() - y;
            if (dx * dx + dy * dy < thresholdSquared)
                return true;
        }
        return false;
    }

    private static Rectangle rectangle(WinDef.RECT rect) {
        return new Rectangle(rect.left, rect.top, rect.right - rect.left,
                rect.bottom - rect.top);
    }

    /**
     * Queries UI elements from the given window and all visible windows on the same thread
     * (e.g. popup menus are separate windows on the same thread).
     * Thread-windows that aren't owned by the foreground window and aren't a popup on a
     * monitor the foreground window covers are skipped. Without this filter,
     * Chromium- and Gecko-based browsers which share a UI thread across multiple top-level
     * browser windows, would surface elements from unrelated browser windows on other
     * monitors and scatter hints onto the wrong screen.
     */
    private static List<UiElement> queryUiElementsOfWindowAndChildren(HWND foregroundHwnd) {
        int threadId = User32.INSTANCE.GetWindowThreadProcessId(foregroundHwnd, null);
        long foregroundKey = Pointer.nativeValue(foregroundHwnd.getPointer());
        Set<Long> foregroundMonitors = monitorsIntersectingWindow(foregroundHwnd);
        List<HWND> windows = new ArrayList<>();
        windows.add(foregroundHwnd);
        ExtendedUser32.INSTANCE.EnumThreadWindows(threadId, (hwnd, data) -> {
            if (Pointer.nativeValue(hwnd.getPointer()) != foregroundKey &&
                User32.INSTANCE.IsWindowVisible(hwnd) &&
                shouldIncludeThreadWindow(hwnd, foregroundKey, foregroundMonitors))
                windows.add(hwnd);
            return true;
        }, null);
        List<UiElement> uiElements = new ArrayList<>();
        long before = System.nanoTime();
        for (HWND window : windows) {
            queryUiElementsOfWindow(window, uiElements);
        }
        logger.debug("Found {} UI elements in HWND {} with {} windows in {}ms",
                uiElements.size(), foregroundKey, windows.size(),
                (long) ((System.nanoTime() - before) / 1e6));
        return uiElements;
    }

    private static Set<Long> monitorsIntersectingWindow(HWND hwnd) {
        // GetWindowRect includes the invisible resize border, which for a maximized window
        // hangs over the neighboring monitors.
        WinDef.RECT windowRect = WindowsOverlay.windowRectExcludingShadow(hwnd);
        Set<Long> monitors = new HashSet<>();
        User32.INSTANCE.EnumDisplayMonitors(null, windowRect,
                (hMonitor, hdcMonitor, lprcMonitor, dwData) -> {
                    monitors.add(Pointer.nativeValue(hMonitor.getPointer()));
                    return 1;
                }, null);
        return monitors;
    }

    private static boolean shouldIncludeThreadWindow(HWND hwnd, long foregroundKey,
                                                     Set<Long> foregroundMonitors) {
        // Another browser window is not a popup.
        if ((User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE) &
             WinUser.WS_POPUP) == 0)
            return false;
        // Owned popups (menus, dropdowns, dialogs anchored to the focused window)
        // are kept regardless of which monitor they land on.
        HWND owner = User32.INSTANCE.GetWindow(hwnd, new WinDef.DWORD(User32.GW_OWNER));
        if (owner != null &&
            Pointer.nativeValue(owner.getPointer()) == foregroundKey)
            return true;
        WinDef.RECT rect = new WinDef.RECT();
        if (!User32.INSTANCE.GetWindowRect(hwnd, rect))
            return false;
        WinUser.HMONITOR monitor = User32.INSTANCE.MonitorFromRect(rect,
                WinUser.MONITOR_DEFAULTTONULL);
        if (monitor == null)
            return false;
        return foregroundMonitors.contains(Pointer.nativeValue(monitor.getPointer()));
    }

    /**
     * Queries UI elements of every visible window that intersects the area, front to back.
     * Elements covered by a window that is drawn over theirs are left out: a hint is
     * only kept where the click it performs would reach the element.
     */
    private static List<UiElement> queryUiElementsOfWindowsInArea(Rectangle area) {
        int currentProcessId = Kernel32.INSTANCE.GetCurrentProcessId();
        IntByReference processId = new IntByReference();
        List<HWND> windows = new ArrayList<>();
        List<Rectangle> windowRectanglesInArea = new ArrayList<>();
        // EnumWindows walks top-level windows front to back.
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            if (!User32.INSTANCE.IsWindowVisible(hwnd) ||
                ExtendedUser32.INSTANCE.IsIconic(hwnd) ||
                isCloaked(hwnd) ||
                // A click-through window does not receive the clicks the hints perform,
                // and does not hide what is behind it.
                (User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE) &
                 ExtendedUser32.WS_EX_TRANSPARENT) != 0)
                return true;
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, processId);
            if (processId.getValue() == currentProcessId)
                return true;
            Rectangle windowRectangleInArea =
                    rectangle(WindowsOverlay.windowRectExcludingShadow(hwnd))
                            .intersection(area);
            if (windowRectangleInArea.isEmpty())
                return true;
            windows.add(hwnd);
            windowRectanglesInArea.add(windowRectangleInArea);
            return true;
        }, null);
        List<UiElement> uiElements = new ArrayList<>();
        long before = System.nanoTime();
        int walkedWindows = 0;
        for (int windowIndex = 0; windowIndex < windows.size(); windowIndex++) {
            Rectangle windowRectangleInArea = windowRectanglesInArea.get(windowIndex);
            HWND window = windows.get(windowIndex);
            Point center = windowRectangleInArea.center();
            // Walking a window that holds nothing a hint could reach is where the query
            // spends its time. Its center says whether it is drawn over at all, each
            // element being kept on the click it is there for anyway.
            if (windowRectanglesInArea.subList(0, windowIndex).stream()
                                      .anyMatch(rectangle -> rectangle.contains(
                                              windowRectangleInArea)) &&
                !clickReaches(window, center.x(), center.y()))
                continue;
            walkedWindows++;
            queryUiElementsOfWindow(window, windowRectangleInArea, uiElements);
        }
        logger.debug("Found {} UI elements in {} of {} windows of area {} in {}ms",
                uiElements.size(), walkedWindows, windows.size(), area,
                (long) ((System.nanoTime() - before) / 1e6));
        return uiElements;
    }

    /** Windows of another virtual desktop, and suspended UWP apps, are cloaked. */
    private static boolean isCloaked(HWND hwnd) {
        IntByReference cloaked = new IntByReference();
        Dwmapi.INSTANCE.DwmGetWindowAttribute(hwnd, Dwmapi.DWMWA_CLOAKED, cloaked, 4);
        return cloaked.getValue() != 0;
    }

    private static void queryUiElementsOfWindow(HWND hwnd,
                                                List<UiElement> uiElements) {
        WinDef.RECT windowRect = new WinDef.RECT();
        if (!User32.INSTANCE.GetWindowRect(hwnd, windowRect))
            return;
        if (User32.INSTANCE.MonitorFromRect(windowRect,
                WinUser.MONITOR_DEFAULTTONULL) == null)
            // The window is not withi n a screen.
            return;
        queryUiElementsOfWindow(hwnd, rectangle(windowRect), uiElements);
    }

    /** A Chromium browser exposes the page trees of all its windows once the children of one
     *  content window are asked for, and building them takes a moment. */
    private static void preWarmUiElements(HWND hwnd) {
        IntByReference processId = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, processId);
        if (!preWarmedProcessIds.add(processId.getValue()))
            return;
        UIAutomation uia = new UIAutomation(automation);
        List<HWND> windows = new ArrayList<>();
        windows.add(hwnd);
        for (HWND child = User32.INSTANCE.GetWindow(hwnd,
                new WinDef.DWORD(User32.GW_CHILD)); child != null;
             child = User32.INSTANCE.GetWindow(child,
                     new WinDef.DWORD(User32.GW_HWNDNEXT)))
            windows.add(child);
        long before = System.nanoTime();
        for (HWND window : windows) {
            UIAutomationElement element = uia.elementFromHandle(window);
            if (element == null)
                continue;
            UIAutomationElementArray children = element.findAllBuildCache(
                    TreeScope_Children, cachedCondition, cachedCacheRequest);
            if (children != null)
                children.Release();
            element.Release();
        }
        logger.debug("Pre-warmed the UI elements of HWND {} with {} windows in {}ms",
                Pointer.nativeValue(hwnd.getPointer()), windows.size(),
                (long) ((System.nanoTime() - before) / 1e6));
    }

    private static void queryUiElementsOfWindow(HWND hwnd, Rectangle elementBounds,
                                                List<UiElement> uiElements) {
        preWarmUiElements(hwnd);
        double scale = WindowsScreen.findActiveScreen(new WinDef.POINT(
                elementBounds.x() + elementBounds.width() / 2,
                elementBounds.y() + elementBounds.height() / 2)).scale();
        UIAutomation uia = new UIAutomation(automation);
        UIAutomationElement root = null;
        UIAutomationElementArray array = null;
        try {
            root = uia.elementFromHandle(hwnd);
            if (root == null)
                return;
            long beforeQuery = System.nanoTime();
            int elementCountBeforeQuery = uiElements.size();
            array = root.findAllBuildCache(TreeScope_Descendants,
                    cachedCondition, cachedCacheRequest);
            if (array != null)
                collectElements(array, hwnd, elementBounds, scale, uiElements);
            logger.trace("Found {} UI elements in HWND {} in {}ms",
                    uiElements.size() - elementCountBeforeQuery,
                    Pointer.nativeValue(hwnd.getPointer()),
                    (long) ((System.nanoTime() - beforeQuery) / 1e6));
        }
        finally {
            if (array != null)
                array.Release();
            if (root != null)
                root.Release();
        }
    }

    /**
     * Whether a click at that point lands on that window, rather than on one drawn over it.
     * A rectangle cannot answer this: the window the Start menu opens spans the work area
     * but shows the windows behind it everywhere but its own panel.
     */
    private static boolean clickReaches(HWND hwnd, double x, double y) {
        WinDef.POINT.ByValue point = new WinDef.POINT.ByValue();
        point.x = (int) Math.round(x);
        point.y = (int) Math.round(y);
        HWND clicked = ExtendedUser32.INSTANCE.WindowFromPoint(point);
        if (clicked == null)
            return false;
        HWND root = User32.INSTANCE.GetAncestor(clicked, WinUser.GA_ROOT);
        if (root == null)
            root = clicked;
        if (hwnd.equals(root))
            return true;
        // Our own overlays are drawn over everything and hidden before a hint is clicked.
        IntByReference processId = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(root, processId);
        return processId.getValue() == Kernel32.INSTANCE.GetCurrentProcessId();
    }

    private static void collectElements(UIAutomationElementArray array, HWND hwnd,
                                        Rectangle elementBounds,
                                        double scale,
                                        List<UiElement> uiElements) {
        double threshold = MIN_DISTANCE_BETWEEN_HINTS_UNZOOMED * scale;
        double thresholdSquared = threshold * threshold;
        int length = array.getLength();
        for (int i = 0; i < length; i++) {
            UIAutomationElement element = array.getElement(i);
            if (element == null)
                continue;
            try {
                WinDef.RECT rect = element.getCachedBoundingRectangle();
                if (rect == null)
                    continue;
                int width = rect.right - rect.left;
                int height = rect.bottom - rect.top;
                if (width <= 0 || height <= 0)
                    continue;
                double centerX = rect.left + width / 2.0;
                double centerY = rect.top + height / 2.0;
                if (!elementBounds.contains(centerX, centerY))
                    continue;
                if (!clickReaches(hwnd, centerX, centerY))
                    continue;
                if (isTooCloseToExistingUiElements(uiElements,
                        centerX, centerY, thresholdSquared))
                    continue;
                uiElements.add(new UiElement(centerX, centerY));
            }
            finally {
                element.Release();
            }
        }
    }

    /**
     * Starts an asynchronous UI element query on a background thread.
     */
    @Override
    public Future<List<UiElement>> startFindActiveWindowUiElements() {
        ensureInitialized();
        HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        long hwndKey = hwnd != null ? Pointer.nativeValue(hwnd.getPointer()) : 0;
        return queryExecutor.submit(() -> {
            if (hwndKey == 0 || cachedCondition == null ||
                cachedCacheRequest == null)
                return List.of();
            initializeBackgroundCom();
            return queryUiElementsOfWindowAndChildren(
                    new HWND(new Pointer(hwndKey)));
        });
    }

    @Override
    public Future<List<UiElement>> startFindUiElementsInArea(Rectangle area) {
        ensureInitialized();
        return queryExecutor.submit(() -> {
            if (cachedCondition == null || cachedCacheRequest == null)
                return List.of();
            initializeBackgroundCom();
            return queryUiElementsOfWindowsInArea(area);
        });
    }

    private static void initializeBackgroundCom() {
        if (backgroundComInitialized)
            return;
        Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED);
        backgroundComInitialized = true;
    }

    // COM wrappers

    private static class UIAutomation extends Unknown {

        UIAutomation(Pointer p) {
            super(p);
        }

        // IUIAutomation::ElementFromHandle — vtable index 6
        UIAutomationElement elementFromHandle(HWND hwnd) {
            PointerByReference ppElement = new PointerByReference();
            WinNT.HRESULT hr = (WinNT.HRESULT) _invokeNativeObject(6,
                    new Object[]{getPointer(), hwnd.getPointer(), ppElement},
                    WinNT.HRESULT.class);
            if (W32Errors.FAILED(hr) || ppElement.getValue() == null)
                return null;
            return new UIAutomationElement(ppElement.getValue());
        }

        // IUIAutomation::CreateCacheRequest — vtable index 20
        UIAutomationCacheRequest createCacheRequest() {
            PointerByReference ppRequest = new PointerByReference();
            WinNT.HRESULT hr = (WinNT.HRESULT) _invokeNativeObject(20,
                    new Object[]{getPointer(), ppRequest},
                    WinNT.HRESULT.class);
            if (W32Errors.FAILED(hr) || ppRequest.getValue() == null)
                return null;
            return new UIAutomationCacheRequest(ppRequest.getValue());
        }

        // IUIAutomation::CreateTrueCondition — vtable index 21
        UIAutomationCondition createTrueCondition() {
            PointerByReference ppCondition = new PointerByReference();
            WinNT.HRESULT hr = (WinNT.HRESULT) _invokeNativeObject(21,
                    new Object[]{getPointer(), ppCondition},
                    WinNT.HRESULT.class);
            if (W32Errors.FAILED(hr) || ppCondition.getValue() == null)
                return null;
            return new UIAutomationCondition(ppCondition.getValue());
        }

        // IUIAutomation::CreatePropertyCondition — vtable index 23
        // HRESULT CreatePropertyCondition(PROPERTYID, VARIANT, IUIAutomationCondition**)
        // On x64, VARIANT (16 bytes) is passed by hidden pointer.
        UIAutomationCondition createPropertyCondition(int propertyId,
                                                       Memory variant) {
            PointerByReference ppCondition = new PointerByReference();
            WinNT.HRESULT hr = (WinNT.HRESULT) _invokeNativeObject(23,
                    new Object[]{getPointer(), propertyId, variant,
                            ppCondition},
                    WinNT.HRESULT.class);
            if (W32Errors.FAILED(hr) || ppCondition.getValue() == null) {
                logger.warn("CreatePropertyCondition({}) failed: 0x{}",
                        propertyId,
                        Integer.toHexString(hr.intValue()));
                return null;
            }
            return new UIAutomationCondition(ppCondition.getValue());
        }

        // IUIAutomation::CreateAndCondition — vtable index 25
        UIAutomationCondition createAndCondition(UIAutomationCondition c1,
                                                  UIAutomationCondition c2) {
            PointerByReference ppCondition = new PointerByReference();
            WinNT.HRESULT hr = (WinNT.HRESULT) _invokeNativeObject(25,
                    new Object[]{getPointer(), c1.getPointer(),
                            c2.getPointer(), ppCondition},
                    WinNT.HRESULT.class);
            if (W32Errors.FAILED(hr) || ppCondition.getValue() == null) {
                logger.warn("CreateAndCondition failed: 0x{}",
                        Integer.toHexString(hr.intValue()));
                return null;
            }
            return new UIAutomationCondition(ppCondition.getValue());
        }

        // IUIAutomation::CreateOrCondition — vtable index 28
        // (25=CreateAndCondition, 26=..FromArray, 27=..FromNativeArray, 28=CreateOrCondition)
        UIAutomationCondition createOrCondition(UIAutomationCondition c1,
                                                 UIAutomationCondition c2) {
            PointerByReference ppCondition = new PointerByReference();
            WinNT.HRESULT hr = (WinNT.HRESULT) _invokeNativeObject(28,
                    new Object[]{getPointer(), c1.getPointer(),
                            c2.getPointer(), ppCondition},
                    WinNT.HRESULT.class);
            if (W32Errors.FAILED(hr) || ppCondition.getValue() == null) {
                logger.warn("CreateOrCondition failed: 0x{}",
                        Integer.toHexString(hr.intValue()));
                return null;
            }
            return new UIAutomationCondition(ppCondition.getValue());
        }

    }

    private static class UIAutomationElement extends Unknown {

        UIAutomationElement(Pointer p) {
            super(p);
        }

        // IUIAutomationElement::FindAllBuildCache — vtable index 8
        UIAutomationElementArray findAllBuildCache(
                int scope, UIAutomationCondition condition,
                UIAutomationCacheRequest cacheRequest) {
            PointerByReference ppArray = new PointerByReference();
            WinNT.HRESULT hr = (WinNT.HRESULT) _invokeNativeObject(8,
                    new Object[]{getPointer(), scope,
                            condition.getPointer(),
                            cacheRequest.getPointer(), ppArray},
                    WinNT.HRESULT.class);
            if (W32Errors.FAILED(hr) || ppArray.getValue() == null)
                return null;
            return new UIAutomationElementArray(ppArray.getValue());
        }

        // IUIAutomationElement::get_CachedBoundingRectangle — vtable index 75
        WinDef.RECT getCachedBoundingRectangle() {
            WinDef.RECT rect = new WinDef.RECT();
            WinNT.HRESULT hr = (WinNT.HRESULT) _invokeNativeObject(75,
                    new Object[]{getPointer(), rect},
                    WinNT.HRESULT.class);
            if (W32Errors.FAILED(hr))
                return null;
            return rect;
        }
    }

    private static class UIAutomationElementArray extends Unknown {

        UIAutomationElementArray(Pointer p) {
            super(p);
        }

        // IUIAutomationElementArray::get_Length — vtable index 3
        int getLength() {
            IntByReference pRetVal = new IntByReference();
            WinNT.HRESULT hr = (WinNT.HRESULT) _invokeNativeObject(3,
                    new Object[]{getPointer(), pRetVal},
                    WinNT.HRESULT.class);
            if (W32Errors.FAILED(hr))
                return 0;
            return pRetVal.getValue();
        }

        // IUIAutomationElementArray::GetElement — vtable index 4
        UIAutomationElement getElement(int index) {
            PointerByReference ppElement = new PointerByReference();
            WinNT.HRESULT hr = (WinNT.HRESULT) _invokeNativeObject(4,
                    new Object[]{getPointer(), index, ppElement},
                    WinNT.HRESULT.class);
            if (W32Errors.FAILED(hr) || ppElement.getValue() == null)
                return null;
            return new UIAutomationElement(ppElement.getValue());
        }
    }

    private static class UIAutomationCondition extends Unknown {

        UIAutomationCondition(Pointer p) {
            super(p);
        }
    }

    private static class UIAutomationCacheRequest extends Unknown {

        UIAutomationCacheRequest(Pointer p) {
            super(p);
        }

        // IUIAutomationCacheRequest::AddProperty — vtable index 3
        void addProperty(int propertyId) {
            WinNT.HRESULT hr = (WinNT.HRESULT) _invokeNativeObject(3,
                    new Object[]{getPointer(), propertyId},
                    WinNT.HRESULT.class);
            if (W32Errors.FAILED(hr))
                logger.warn("AddProperty({}) failed: 0x{}",
                        propertyId, Integer.toHexString(hr.intValue()));
        }
    }

}
