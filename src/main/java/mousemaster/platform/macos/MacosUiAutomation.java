package mousemaster.platform.macos;

import io.qt.core.QPoint;
import mousemaster.Rectangle;
import mousemaster.platform.UiAutomation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class MacosUiAutomation implements UiAutomation {

    @Override
    public Future<List<UiElement>> startFindActiveWindowUiElements() {
        return CompletableFuture.supplyAsync(
                () -> elements(MacosAccessibility.focusedWindowElementFrames(), null));
    }

    /**
     * A window is matched to its accessibility element by frame, and an application that
     * reports none leaves the whole list empty, so the focused window answers instead.
     */
    @Override
    public Future<List<UiElement>> startFindUiElementsInArea(Rectangle area) {
        return CompletableFuture.supplyAsync(() -> {
            List<UiElement> elements = areaElements(area);
            return elements.isEmpty() ?
                    elements(MacosAccessibility.focusedWindowElementFrames(), area) :
                    elements;
        });
    }

    /**
     * The windows come front to back, so the bounds of the ones already walked are what
     * covers an element of a window further back.
     */
    private static List<UiElement> areaElements(Rectangle area) {
        List<UiElement> elements = new ArrayList<>();
        List<Rectangle> covering = new ArrayList<>();
        for (MacosWindowList.OnScreenWindow window : MacosWindowList.onScreenWindows()) {
            if (!window.bounds().intersection(area).isEmpty()) {
                for (Rectangle frame : MacosAccessibility.windowElementFrames(
                        window.processId(), window.bounds())) {
                    Rectangle cropped = frame.intersection(area);
                    if (cropped.width() <= 0 || cropped.height() <= 0 ||
                        covered(covering, cropped))
                        continue;
                    UiElement element = center(cropped);
                    if (tooClose(elements, element))
                        continue;
                    elements.add(element);
                }
            }
            covering.add(window.bounds());
        }
        return elements;
    }

    private static boolean covered(List<Rectangle> covering, Rectangle element) {
        for (Rectangle window : covering) {
            if (window.intersection(element).equals(element))
                return true;
        }
        return false;
    }

    /** An element is cropped to the area, so its center stays inside it. */
    private static List<UiElement> elements(List<Rectangle> frames, Rectangle area) {
        List<UiElement> elements = new ArrayList<>();
        for (Rectangle frame : frames) {
            Rectangle cropped = area == null ? frame : frame.intersection(area);
            if (cropped.width() <= 0 || cropped.height() <= 0)
                continue;
            UiElement element = center(cropped);
            if (tooClose(elements, element))
                continue;
            elements.add(element);
        }
        return elements;
    }

    private static boolean tooClose(List<UiElement> elements, UiElement element) {
        return UiAutomation.isTooCloseToExistingUiElements(elements, element.centerX(),
                element.centerY(), MacosScreens.scaleAt(
                        new QPoint((int) element.centerX(),
                                (int) element.centerY())));
    }

    private static UiElement center(Rectangle frame) {
        return new UiElement(frame.x() + frame.width() / 2d,
                frame.y() + frame.height() / 2d);
    }

}
