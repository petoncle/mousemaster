package jmouseable.jmouseable;

import jmouseable.jmouseable.HintMesh.HintMeshBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class HintManager implements ModeListener, MousePositionListener {

    private static final Logger logger = LoggerFactory.getLogger(HintManager.class);

    private final ScreenManager screenManager;
    private final MouseController mouseController;
    private ModeController modeController;
    private HintMesh hintMesh;
    private Set<Key> selectionKeySubset;
    private int mouseX, mouseY;
    private Mode currentMode;
    private final List<Point> mousePositionHistory = new ArrayList<>();
    private final int maxMousePositionHistorySize;
    /**
     * Used for deterministic hint key sequences.
     */
    private int mousePositionIdCount = 0;
    private final Map<Point, Integer> idByMousePosition = new HashMap<>();

    public HintManager(int maxMousePositionHistorySize, ScreenManager screenManager,
                       MouseController mouseController) {
        this.maxMousePositionHistorySize = maxMousePositionHistorySize;
        this.screenManager = screenManager;
        this.mouseController = mouseController;
    }

    public void setModeController(ModeController modeController) {
        this.modeController = modeController;
    }

    @Override
    public void mouseMoved(int x, int y) {
        this.mouseX = x;
        this.mouseY = y;
    }

    @Override
    public void modeChanged(Mode newMode) {
        HintMeshConfiguration hintMeshConfiguration = newMode.hintMesh();
        if (!hintMeshConfiguration.enabled()) {
            currentMode = newMode;
            WindowsOverlay.hideHintMesh();
            return;
        }
        HintMesh newHintMesh = buildHintMesh(hintMeshConfiguration);
        if (currentMode != null && newMode.hintMesh().equals(currentMode.hintMesh()) &&
            newHintMesh.equals(hintMesh))
            return;
        selectionKeySubset = newHintMesh.hints()
                                        .stream()
                                        .map(Hint::keySequence)
                                        .flatMap(Collection::stream)
                                        .collect(Collectors.toSet());
        currentMode = newMode;
        hintMesh = newHintMesh;
        WindowsOverlay.setHintMesh(hintMesh);
    }

    private HintMesh buildHintMesh(HintMeshConfiguration hintMeshConfiguration) {
        HintMeshBuilder hintMesh = new HintMeshBuilder();
        hintMesh.type(hintMeshConfiguration.type())
                .fontName(hintMeshConfiguration.fontName())
                .fontSize(hintMeshConfiguration.fontSize())
                .fontHexColor(hintMeshConfiguration.fontHexColor())
                .selectedPrefixFontHexColor(
                        hintMeshConfiguration.selectedPrefixFontHexColor())
                .boxHexColor(hintMeshConfiguration.boxHexColor());
        HintMeshType type = hintMeshConfiguration.type();
        if (type instanceof HintMeshType.ActiveScreen ||
            type instanceof HintMeshType.ActiveWindow ||
            type instanceof HintMeshType.AllScreens) {
            if (currentMode != null) {
                HintMeshConfiguration oldHintMeshConfiguration = currentMode.hintMesh();
                if (oldHintMeshConfiguration.enabled() &&
                    oldHintMeshConfiguration.type().equals(hintMeshConfiguration.type()) &&
                    oldHintMeshConfiguration.selectionKeys()
                                            .equals(hintMeshConfiguration.selectionKeys())) {
                    // Keep the old focusedKeySequence.
                    // This is useful for hint-then-click-mode that extends hint-mode.
                    // Note: changes to hint mesh center are ignored here.
                    hintMesh.hints(this.hintMesh.hints())
                            .focusedKeySequence(this.hintMesh.focusedKeySequence());
                    return hintMesh.build();
                }
            }
            List<FixedSizeHintGrid> fixedSizeHintGrids = new ArrayList<>();
            Point hintMeshCenter = hintMeshCenter(screenManager.activeScreen(),
                    hintMeshConfiguration.center());
            if (type instanceof HintMeshType.ActiveScreen activeScreen) {
                fixedSizeHintGrids.add(screenFixedSizeHintGrid(activeScreen,
                        screenManager.activeScreen(), hintMeshCenter));
            }
            else if (type instanceof HintMeshType.AllScreens allScreens) {
                for (Screen screen : screenManager.screens())
                    fixedSizeHintGrids.add(
                            screenFixedSizeHintGrid(allScreens, screen, hintMeshCenter));
            }
            else if (type instanceof HintMeshType.ActiveWindow activeWindow) {
                Rectangle activeWindowRectangle = WindowsOverlay.activeWindowRectangle(
                        activeWindow.windowWidthPercent(),
                        activeWindow.windowHeightPercent());
                int hintMeshX, hintMeshY, hintMeshWidth, hintMeshHeight, rowCount, columnCount;
                hintMeshWidth = activeWindowRectangle.width();
                hintMeshHeight = activeWindowRectangle.height();
                hintMeshX = hintMeshCenter.x() - hintMeshWidth / 2;
                hintMeshY = hintMeshCenter.y() - hintMeshHeight / 2;
                Screen activeScreen = screenManager.activeScreen();
                rowCount = dpiDescaled(activeWindow.rowCount(), activeScreen.dpi());
                columnCount = dpiDescaled(activeWindow.columnCount(), activeScreen.dpi());
                fixedSizeHintGrids.add(
                        new FixedSizeHintGrid(hintMeshX, hintMeshY, hintMeshWidth,
                                hintMeshHeight, rowCount, columnCount));
            }
            else
                throw new IllegalStateException();
            List<Key> selectionKeySubset =
                    gridSelectionKeySubset(hintMeshConfiguration.selectionKeys(),
                            fixedSizeHintGrids.getFirst().rowCount *
                            fixedSizeHintGrids.size(),
                            fixedSizeHintGrids.getFirst().columnCount *
                            fixedSizeHintGrids.size());
            int hintCount = fixedSizeHintGrids.getFirst().rowCount *
                            fixedSizeHintGrids.getFirst().columnCount *
                            fixedSizeHintGrids.size();
            // Find hintLength such that hintKeyCount^hintLength >= rowCount*columnCount
            int hintLength = hintCount == 1 ? 1 : (int) Math.ceil(
                    Math.log(hintCount) / Math.log(selectionKeySubset.size()));
            List<Hint> hints = new ArrayList<>();
            for (FixedSizeHintGrid fixedSizeHintGrid : fixedSizeHintGrids) {
                int beginHintIndex = hints.size();
                hints.addAll(buildHints(fixedSizeHintGrid, selectionKeySubset, hintLength,
                        beginHintIndex));
            }
            hintMesh.hints(hints);
        }
        else {
            int hintCount = mousePositionHistory.size();
            List<Hint> hints = new ArrayList<>(hintCount);
            List<Key> selectionKeySubset = maxMousePositionHistorySize >=
                                           hintMeshConfiguration.selectionKeys().size() ?
                    hintMeshConfiguration.selectionKeys() :
                    hintMeshConfiguration.selectionKeys()
                                         .subList(0, maxMousePositionHistorySize);
            int hintLength = (int) Math.ceil(Math.log(maxMousePositionHistorySize) /
                                             Math.log(selectionKeySubset.size()));
            for (Point point : mousePositionHistory) {
                List<Key> keySequence = hintKeySequence(selectionKeySubset, hintLength,
                        idByMousePosition.get(point) % maxMousePositionHistorySize);
                hints.add(new Hint(point.x(), point.y(), keySequence));
            }
            hintMesh.hints(hints);
        }
        return hintMesh.build();
    }

    private static List<Hint> buildHints(FixedSizeHintGrid fixedSizeHintGrid,
                                         List<Key> selectionKeySubset, int hintLength,
                                         int beginHintIndex) {
        int rowCount = fixedSizeHintGrid.rowCount();
        int columnCount = fixedSizeHintGrid.columnCount();
        int hintMeshWidth = fixedSizeHintGrid.hintMeshWidth();
        int hintMeshHeight = fixedSizeHintGrid.hintMeshHeight();
        int hintMeshX = fixedSizeHintGrid.hintMeshX();
        int hintMeshY = fixedSizeHintGrid.hintMeshY();
        int cellWidth = hintMeshWidth / columnCount;
        int cellHeight = hintMeshHeight / rowCount;
        int gridHintCount = rowCount * columnCount;
        List<Hint> hints = new ArrayList<>(gridHintCount);
        int hintIndex = beginHintIndex;
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                List<Key> keySequence = hintKeySequence(selectionKeySubset, hintLength, hintIndex);
                hintIndex++;
                int hintCenterX = hintMeshX + columnIndex * cellWidth + cellWidth / 2;
                int hintCenterY = hintMeshY + rowIndex * cellHeight + cellHeight / 2;
                hints.add(new Hint(hintCenterX, hintCenterY, keySequence));
            }
        }
        return hints;
    }

    private static List<Key> hintKeySequence(List<Key> selectionKeySubset, int hintLength,
                                             int hintIndex) {
        List<Key> keySequence = new ArrayList<>();
        // We want the hints to look like this:
        // aa, ba, ..., za
        // ab, bb, ..., zb
        // az, bz, ..., zz
        // The ideal situation is when rowCount = columnCount = hintKeys.size().
        for (int i = 0; i < hintLength; i++) {
            keySequence.add(selectionKeySubset.get(
                    (int) (hintIndex / Math.pow(selectionKeySubset.size(), i) %
                           selectionKeySubset.size())));
        }
        return keySequence;
    }

    private FixedSizeHintGrid screenFixedSizeHintGrid(HintMeshType type, Screen screen,
                                                      Point hintMeshCenter) {
        if (!(type instanceof HintMeshType.ActiveScreen) &&
            !(type instanceof HintMeshType.AllScreens))
            throw new IllegalArgumentException();
        int hintMeshX, hintMeshY, hintMeshWidth, hintMeshHeight, rowCount, columnCount;
        double screenWidthPercent, screenHeightPercent;
        int dpi = screen.dpi();
        if (type instanceof HintMeshType.ActiveScreen activeScreen) {
            screenWidthPercent = activeScreen.screenWidthPercent();
            screenHeightPercent = activeScreen.screenHeightPercent();
            rowCount = dpiDescaled(activeScreen.rowCount(), dpi);
            columnCount = dpiDescaled(activeScreen.columnCount(), dpi);
        }
        else if (type instanceof HintMeshType.AllScreens allScreens) {
            screenWidthPercent = allScreens.screenWidthPercent();
            screenHeightPercent = allScreens.screenHeightPercent();
            rowCount = dpiDescaled(allScreens.rowCount(), dpi);
            columnCount = dpiDescaled(allScreens.columnCount(), dpi);
        }
        else
            throw new IllegalStateException();
        hintMeshWidth = (int) (screen.rectangle().width() * screenWidthPercent);
        hintMeshHeight = (int) (screen.rectangle().height() * screenHeightPercent);
        hintMeshX = hintMeshCenter.x() - hintMeshWidth / 2;
        hintMeshY = hintMeshCenter.y() - hintMeshHeight / 2;
        return new FixedSizeHintGrid(hintMeshX, hintMeshY, hintMeshWidth, hintMeshHeight,
                rowCount, columnCount);
    }

    /**
     * 26 rows, scale 150% (== dpi 144) -> 18 rows.
     */
    private static int dpiDescaled(int value, int dpi) {
        return (int) Math.ceil((double) value * 96 / dpi);
    }

    private Point hintMeshCenter(Screen activeScreen, HintMeshCenter center) {
        int centerX = 0, centerY = 0;
        switch (center) {
            case ACTIVE_SCREEN -> {
                centerX = activeScreen.rectangle().x() +
                          activeScreen.rectangle().width() / 2;
                centerY = activeScreen.rectangle().y() +
                          activeScreen.rectangle().height() / 2;
            }
            case ACTIVE_WINDOW -> {
                Rectangle activeWindowRectangle =
                        WindowsOverlay.activeWindowRectangle(1, 1);
                centerX = activeWindowRectangle.x() + activeWindowRectangle.width() / 2;
                centerY = activeWindowRectangle.y() + activeWindowRectangle.height() / 2;
            }
            case MOUSE -> {
                centerX = mouseX;
                centerY = mouseY;
            }
        }
        return new Point(centerX, centerY);
    }

    private record Point(int x, int y) {
    }

    private record FixedSizeHintGrid(int hintMeshX, int hintMeshY, int hintMeshWidth,
                                     int hintMeshHeight, int rowCount, int columnCount) {

    }

    private static List<Key> gridSelectionKeySubset(List<Key> keys, int rowCount,
                                                    int columnCount) {
        int hintCount = rowCount * columnCount;
        if (hintCount < keys.size())
            // Will be single-key hints.
            return keys.subList(0, hintCount);
        return rowCount == columnCount && rowCount < keys.size() ?
                keys.subList(0, rowCount) : keys;
    }

    @Override
    public void modeTimedOut() {
        // No op.
    }

    public boolean keyPressed(Key key) {
        HintMeshConfiguration hintMeshConfiguration = currentMode.hintMesh();
        if (!hintMeshConfiguration.enabled())
            return false;
        if (key.equals(hintMeshConfiguration.undoKey())) {
            if (!hintMesh.focusedKeySequence().isEmpty()) {
                hintMesh = hintMesh.builder().focusedKeySequence(List.of()).build();
                WindowsOverlay.setHintMesh(hintMesh);
                return true;
            }
            return false; // ComboWatcher can have a go at it.
        }
        if (!selectionKeySubset.contains(key))
            return false;
        List<Key> newFocusedKeySequence = new ArrayList<>(hintMesh.focusedKeySequence());
        newFocusedKeySequence.add(key);
        Hint exactMatchHint = null;
        boolean atLeastOneHintIsStartsWithNewFocusedHintKeySequence = false;
        for (Hint hint : hintMesh.hints()) {
            if (hint.keySequence().size() < newFocusedKeySequence.size())
                continue;
            if (!hint.startsWith(newFocusedKeySequence))
                continue;
            atLeastOneHintIsStartsWithNewFocusedHintKeySequence = true;
            if (hint.keySequence().size() == newFocusedKeySequence.size()) {
                exactMatchHint = hint;
                break;
            }
        }
        if (!atLeastOneHintIsStartsWithNewFocusedHintKeySequence)
            return false;
        if (exactMatchHint != null) {
            mouseController.moveTo(exactMatchHint.centerX(), exactMatchHint.centerY());
            // Do not wait for the asynchronous mouse moved callback.
            // The next mode may be hint stage 2 mode that needs the new mouse
            // position before the mouse moved callback is called.
            mouseX = exactMatchHint.centerX();
            mouseY = exactMatchHint.centerY();
            if (hintMeshConfiguration.saveMousePositionAfterSelection())
                saveMousePosition();
            if (hintMeshConfiguration.clickButtonAfterSelection() != null) {
                switch (hintMeshConfiguration.clickButtonAfterSelection()) {
                    case Button.LEFT_BUTTON -> mouseController.clickLeft();
                    case Button.MIDDLE_BUTTON -> mouseController.clickMiddle();
                    case Button.RIGHT_BUTTON -> mouseController.clickRight();
                }
            }
            if (hintMeshConfiguration.nextModeAfterSelection() != null)
                modeController.switchMode(hintMeshConfiguration.nextModeAfterSelection());
            else {
                hintMesh =
                        hintMesh.builder().focusedKeySequence(List.of()).build();
                WindowsOverlay.setHintMesh(hintMesh);
            }
        }
        else {
            hintMesh =
                    hintMesh.builder().focusedKeySequence(newFocusedKeySequence).build();
            WindowsOverlay.setHintMesh(hintMesh);
        }
        return true;
    }

    public void saveMousePosition() {
        Point point = new Point(mouseX, mouseY);
        if (mousePositionHistory.contains(point))
            return;
        idByMousePosition.put(point, mousePositionIdCount);
        if (mousePositionIdCount == Integer.MAX_VALUE)
            mousePositionIdCount = 0;
        else
            mousePositionIdCount++;
        if (mousePositionHistory.size() == maxMousePositionHistorySize)
            mousePositionHistory.removeFirst();
        mousePositionHistory.add(point);
        logger.debug(
                "Saved mouse position " + point.x() + "," + point.y() + " to history");
    }

    public void clearMousePositionHistory() {
        mousePositionHistory.clear();
        idByMousePosition.clear();
        mousePositionIdCount = 0;
        logger.debug("Reset mouse position history");
    }

}
