package mousemaster;

import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class WindowsUiAutomation {

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

    private static final int TreeScope_Descendants = 4;

    // VARIANT constants
    private static final short VT_BOOL = 0x000B;
    private static final short VT_I4 = 3;
    private static final short VARIANT_TRUE = -1;
    private static final short VARIANT_FALSE = 0;

    private static Pointer automation;
    private static UIAutomationCondition cachedCondition;
    private static UIAutomationCacheRequest cachedCacheRequest;

    private static final ExecutorService queryExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "uia-query");
                t.setDaemon(true);
                return t;
            });
    private static volatile boolean backgroundComInitialized;

    record UiElement(double centerX, double centerY) {
    }

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

    private static final double MIN_DISTANCE_BETWEEN_HINTS = 40;

    private static boolean isTooCloseToExistingUiElements(List<UiElement> elements,
                                                          double x, double y) {
        for (UiElement e : elements) {
            double dx = e.centerX() - x;
            double dy = e.centerY() - y;
            if (dx * dx + dy * dy <
                MIN_DISTANCE_BETWEEN_HINTS * MIN_DISTANCE_BETWEEN_HINTS)
                return true;
        }
        return false;
    }

    // Queries UI elements from the given window and all visible windows on the same thread
    // (e.g. popup menus are separate windows on the same thread).
    private static List<UiElement> queryUiElementsOfWindowAndChildren(HWND foregroundHwnd) {
        int threadId = User32.INSTANCE.GetWindowThreadProcessId(foregroundHwnd, null);
        List<HWND> windows = new ArrayList<>();
        windows.add(foregroundHwnd);
        long foregroundKey = Pointer.nativeValue(foregroundHwnd.getPointer());
        ExtendedUser32.INSTANCE.EnumThreadWindows(threadId, (hwnd, data) -> {
            if (Pointer.nativeValue(hwnd.getPointer()) != foregroundKey &&
                User32.INSTANCE.IsWindowVisible(hwnd))
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

    private static void queryUiElementsOfWindow(HWND hwnd,
                                                List<UiElement> uiElements) {
        WinDef.RECT windowRect = new WinDef.RECT();
        if (!User32.INSTANCE.GetWindowRect(hwnd, windowRect))
            return;
        if (User32.INSTANCE.MonitorFromRect(windowRect,
                WinUser.MONITOR_DEFAULTTONULL) == null)
            // The window is not within a screen.
            return;
        UIAutomation uia = new UIAutomation(automation);
        UIAutomationElement root = null;
        UIAutomationElementArray array = null;
        try {
            root = uia.elementFromHandle(hwnd);
            if (root == null)
                return;
            long beforeQuery = System.nanoTime();
            array = root.findAllBuildCache(TreeScope_Descendants,
                    cachedCondition, cachedCacheRequest);
            if (array != null)
                collectElements(array, windowRect, uiElements);
            logger.trace("Found {} UI elements in HWND {} in {}ms",
                    uiElements.size(),
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

    private static void collectElements(UIAutomationElementArray array,
                                        WinDef.RECT windowRect,
                                        List<UiElement> uiElements) {
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
                if (centerX < windowRect.left ||
                    centerX > windowRect.right ||
                    centerY < windowRect.top ||
                    centerY > windowRect.bottom)
                    continue;
                if (isTooCloseToExistingUiElements(uiElements,
                        centerX, centerY))
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
    public static Future<List<UiElement>> startFindInteractiveUiElements() {
        ensureInitialized();
        HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        long hwndKey = hwnd != null ? Pointer.nativeValue(hwnd.getPointer()) : 0;
        return queryExecutor.submit(() -> {
            if (hwndKey == 0 || cachedCondition == null ||
                cachedCacheRequest == null)
                return List.of();
            if (!backgroundComInitialized) {
                Ole32.INSTANCE.CoInitializeEx(Pointer.NULL,
                        Ole32.COINIT_MULTITHREADED);
                backgroundComInitialized = true;
            }
            return queryUiElementsOfWindowAndChildren(
                    new HWND(new Pointer(hwndKey)));
        });
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
