package mousemaster;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class WindowsUiAutomation {

    private static final Logger logger =
            LoggerFactory.getLogger(WindowsUiAutomation.class);

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

    private static final int UIA_ButtonControlTypeId = 50000;

    private static final int TreeScope_Descendants = 4;

    // VARIANT constants
    private static final short VT_BOOL = 0x000B;
    private static final short VT_I4 = 3;
    private static final short VARIANT_TRUE = -1;

    private static Pointer automation;
    private static UIAutomationCondition cachedCondition;
    private static UIAutomationCacheRequest cachedCacheRequest;

    record UiElement(double centerX, double centerY) {
    }

    /**
     * Creates a 16-byte VARIANT(VT_BOOL, VARIANT_TRUE) in native memory.
     * On x64: vt at offset 0 (2 bytes), value at offset 8 (2 bytes).
     * On x64 ABI, structs >8 bytes are passed by hidden pointer,
     * so passing this Memory (a Pointer) to COM works naturally.
     */
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
        variant.setShort(8, (short) 0); // VARIANT_FALSE
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
        // Build condition:
        // IsOffscreen=false AND IsEnabled=true
        // AND (IsKeyboardFocusable=true OR IsInvokePatternAvailable=true
        //      OR ControlType=Button)
        cachedCondition = buildCondition(uia);
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
     * Builds: IsOffscreen=false AND IsEnabled=true
     *     AND (IsKeyboardFocusable OR IsInvokePatternAvailable OR ControlType=Button)
     * Returns null if any step fails.
     */
    private static UIAutomationCondition buildCondition(UIAutomation uia) {
        Memory boolTrue = createBoolVariantTrue();
        Memory boolFalse = createBoolVariantFalse();
        UIAutomationCondition focusable = null, invokable = null,
                button = null, onscreen = null, enabled = null;
        try {
            focusable = uia.createPropertyCondition(
                    UIA_IsKeyboardFocusablePropertyId, boolTrue);
            invokable = uia.createPropertyCondition(
                    UIA_IsInvokePatternAvailablePropertyId, boolTrue);
            button = uia.createPropertyCondition(
                    UIA_ControlTypePropertyId,
                    createIntVariant(UIA_ButtonControlTypeId));
            onscreen = uia.createPropertyCondition(
                    UIA_IsOffscreenPropertyId, boolFalse);
            enabled = uia.createPropertyCondition(
                    UIA_IsEnabledPropertyId, boolTrue);
            if (focusable == null || invokable == null ||
                button == null || onscreen == null || enabled == null)
                return null;
            // focusable OR invokable
            UIAutomationCondition or1 =
                    uia.createOrCondition(focusable, invokable);
            if (or1 == null)
                return null;
            // (focusable OR invokable) OR button
            UIAutomationCondition or2 =
                    uia.createOrCondition(or1, button);
            or1.Release();
            if (or2 == null)
                return null;
            // ... AND onscreen
            UIAutomationCondition and1 =
                    uia.createAndCondition(or2, onscreen);
            or2.Release();
            if (and1 == null)
                return null;
            // ... AND enabled
            UIAutomationCondition result =
                    uia.createAndCondition(and1, enabled);
            and1.Release();
            return result;
        }
        finally {
            if (focusable != null) focusable.Release();
            if (invokable != null) invokable.Release();
            if (button != null) button.Release();
            if (onscreen != null) onscreen.Release();
            if (enabled != null) enabled.Release();
        }
    }

    private static final double MIN_DISTANCE_BETWEEN_HINTS = 40;

    private static boolean tooCloseToExisting(List<UiElement> elements,
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

    static List<UiElement> findInteractiveElements() {
        long t0 = System.nanoTime();
        ensureInitialized();
        HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null)
            return List.of();
        if (cachedCondition == null || cachedCacheRequest == null)
            return List.of();
        WinDef.RECT windowRect = new WinDef.RECT();
        if (!User32.INSTANCE.GetWindowRect(hwnd, windowRect))
            return List.of();
        UIAutomation uia = new UIAutomation(automation);
        UIAutomationElement root = null;
        UIAutomationElementArray array = null;
        try {
            root = uia.elementFromHandle(hwnd);
            if (root == null)
                return List.of();
            long t1 = System.nanoTime();
            array = root.findAllBuildCache(TreeScope_Descendants,
                    cachedCondition, cachedCacheRequest);
            long t2 = System.nanoTime();
            if (array == null) {
                logger.debug("FindAllBuildCache returned null");
                return List.of();
            }
            int length = array.getLength();
            logger.debug("FindAllBuildCache: {}ms ({} elements)",
                    (t2 - t1) / 1_000_000.0, length);
            List<UiElement> elements = new ArrayList<>();
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
                    if (tooCloseToExisting(elements, centerX, centerY))
                        continue;
                    elements.add(new UiElement(centerX, centerY));
                }
                finally {
                    element.Release();
                }
            }
            long t3 = System.nanoTime();
            logger.debug("Element iteration: {}ms ({} unique interactive)",
                    (t3 - t2) / 1_000_000.0, elements.size());
            logger.debug("Total findInteractiveElements: {}ms",
                    (t3 - t0) / 1_000_000.0);
            return elements;
        }
        finally {
            if (array != null)
                array.Release();
            if (root != null)
                root.Release();
        }
    }

    // --- COM wrappers ---

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
