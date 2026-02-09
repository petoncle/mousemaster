package mousemaster;

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
import java.util.Set;

public class WindowsUiAutomation {

    private static final Logger logger =
            LoggerFactory.getLogger(WindowsUiAutomation.class);

    private static final Guid.CLSID CLSID_CUIAutomation =
            new Guid.CLSID("FF48DBA4-60EF-4201-AA87-54103EEF594E");
    private static final Guid.IID IID_IUIAutomation =
            new Guid.IID("30CBE57D-D9D0-452A-AB13-7AC5AC4825EE");

    private static final Set<Integer> INTERACTIVE_CONTROL_TYPES = Set.of(
            50000, // Button
            50002, // CheckBox
            50003, // ComboBox
            50004, // Edit
            50005, // Hyperlink
            50007, // ListItem
            50011, // MenuItem
            50013, // RadioButton
            50019, // TabItem
            50024, // TreeItem
            50031  // SplitButton
    );

    private static final int TreeScope_Children = 2;
    private static final int TreeScope_Descendants = 4;

    private static Pointer automation;

    record UiElement(double centerX, double centerY) {
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
    }

    static List<UiElement> findInteractiveElements() {
        ensureInitialized();
        HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null)
            return List.of();
        char[] windowTitle = new char[256];
        User32.INSTANCE.GetWindowText(hwnd, windowTitle, 256);
        logger.debug("Foreground HWND={}, title='{}'",
                Pointer.nativeValue(hwnd.getPointer()),
                com.sun.jna.Native.toString(windowTitle));
        UIAutomation uia = new UIAutomation(automation);
        UIAutomationElement root = null;
        UIAutomationCondition condition = null;
        UIAutomationElementArray array = null;
        try {
            root = uia.elementFromHandle(hwnd);
            if (root == null) {
                logger.debug("elementFromHandle returned null");
                return List.of();
            }
            condition = uia.createTrueCondition();
            if (condition == null) {
                logger.debug("createTrueCondition returned null");
                return List.of();
            }
            array = root.findAll(TreeScope_Descendants, condition);
            if (array == null) {
                logger.debug("FindAll returned null");
                return List.of();
            }
            int length = array.getLength();
            logger.debug("UI Automation FindAll found {} total elements", length);
            java.util.Map<Integer, Integer> controlTypeCounts = new java.util.TreeMap<>();
            List<UiElement> elements = new ArrayList<>();
            for (int i = 0; i < length; i++) {
                UIAutomationElement element = array.getElement(i);
                if (element == null)
                    continue;
                try {
                    int controlType = element.getCurrentControlType();
                    controlTypeCounts.merge(controlType, 1, Integer::sum);
                    if (!INTERACTIVE_CONTROL_TYPES.contains(controlType))
                        continue;
                    if (element.isCurrentOffscreen())
                        continue;
                    WinDef.RECT rect = element.getCurrentBoundingRectangle();
                    if (rect == null)
                        continue;
                    int width = rect.right - rect.left;
                    int height = rect.bottom - rect.top;
                    if (width <= 0 || height <= 0)
                        continue;
                    double centerX = rect.left + width / 2.0;
                    double centerY = rect.top + height / 2.0;
                    elements.add(new UiElement(centerX, centerY));
                }
                finally {
                    element.Release();
                }
            }
            logger.debug("Control type distribution: {}", controlTypeCounts);
            logger.debug("Found {} interactive UI elements", elements.size());
            return elements;
        }
        finally {
            if (array != null)
                array.Release();
            if (condition != null)
                condition.Release();
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

        // IUIAutomation::GetRootElement — vtable index 5
        UIAutomationElement getRootElement() {
            PointerByReference ppElement = new PointerByReference();
            WinNT.HRESULT hr = (WinNT.HRESULT) _invokeNativeObject(5,
                    new Object[]{getPointer(), ppElement},
                    WinNT.HRESULT.class);
            if (W32Errors.FAILED(hr) || ppElement.getValue() == null)
                return null;
            return new UIAutomationElement(ppElement.getValue());
        }

        // IUIAutomation::get_RawViewCondition — vtable index 16
        UIAutomationCondition getRawViewCondition() {
            PointerByReference ppCondition = new PointerByReference();
            WinNT.HRESULT hr = (WinNT.HRESULT) _invokeNativeObject(16,
                    new Object[]{getPointer(), ppCondition},
                    WinNT.HRESULT.class);
            logger.debug("getRawViewCondition hr=0x{}",
                    Integer.toHexString(hr.intValue()));
            if (W32Errors.FAILED(hr) || ppCondition.getValue() == null)
                return null;
            return new UIAutomationCondition(ppCondition.getValue());
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
    }

    private static class UIAutomationElement extends Unknown {

        UIAutomationElement(Pointer p) {
            super(p);
        }

        // IUIAutomationElement::FindAll — vtable index 6
        UIAutomationElementArray findAll(
                int scope, UIAutomationCondition condition) {
            PointerByReference ppArray = new PointerByReference();
            WinNT.HRESULT hr = (WinNT.HRESULT) _invokeNativeObject(6,
                    new Object[]{getPointer(), scope,
                            condition.getPointer(), ppArray},
                    WinNT.HRESULT.class);
            logger.debug("FindAll hr=0x{}, ppArray={}",
                    Integer.toHexString(hr.intValue()),
                    ppArray.getValue());
            if (W32Errors.FAILED(hr) || ppArray.getValue() == null)
                return null;
            return new UIAutomationElementArray(ppArray.getValue());
        }

        // IUIAutomationElement::get_CurrentControlType — vtable index 21
        int getCurrentControlType() {
            IntByReference pRetVal = new IntByReference();
            WinNT.HRESULT hr = (WinNT.HRESULT) _invokeNativeObject(21,
                    new Object[]{getPointer(), pRetVal},
                    WinNT.HRESULT.class);
            if (W32Errors.FAILED(hr))
                return -1;
            return pRetVal.getValue();
        }

        // IUIAutomationElement::get_CurrentIsOffscreen — vtable index 38
        boolean isCurrentOffscreen() {
            IntByReference pRetVal = new IntByReference();
            WinNT.HRESULT hr = (WinNT.HRESULT) _invokeNativeObject(38,
                    new Object[]{getPointer(), pRetVal},
                    WinNT.HRESULT.class);
            if (W32Errors.FAILED(hr))
                return true;
            return pRetVal.getValue() != 0;
        }

        // IUIAutomationElement::get_CurrentBoundingRectangle — vtable index 43
        WinDef.RECT getCurrentBoundingRectangle() {
            WinDef.RECT rect = new WinDef.RECT();
            WinNT.HRESULT hr = (WinNT.HRESULT) _invokeNativeObject(43,
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

}
