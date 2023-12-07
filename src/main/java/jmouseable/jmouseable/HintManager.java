package jmouseable.jmouseable;

import jmouseable.jmouseable.HintGridArea.ActiveScreenHintGridArea;
import jmouseable.jmouseable.HintGridArea.ActiveWindowHintGridArea;
import jmouseable.jmouseable.HintGridArea.AllScreensHintGridArea;
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
    private List<PositionHistoryListener> positionHistoryListeners;
    private HintMesh hintMesh;
    private Set<Key> selectionKeySubset;
    private int mouseX, mouseY;
    private Mode currentMode;
    private final List<Point> positionHistory = new ArrayList<>();
    private final int maxPositionHistorySize;
    /**
     * Used for deterministic hint key sequences.
     */
    private int positionIdCount = 0;
    private final Map<Point, Integer> idByPosition = new HashMap<>();
    private int positionCycleIndex = 0;

    public HintManager(int maxPositionHistorySize, ScreenManager screenManager,
                       MouseController mouseController) {
        this.maxPositionHistorySize = maxPositionHistorySize;
        this.screenManager = screenManager;
        this.mouseController = mouseController;
    }

    public void setPositionHistoryListener(
            List<PositionHistoryListener> positionHistoryListeners) {
        this.positionHistoryListeners = positionHistoryListeners;
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
        if (type instanceof HintMeshType.HintGrid hintGrid) {
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
            if (hintGrid.area() instanceof ActiveScreenHintGridArea activeScreenHintGridArea) {
                Screen gridScreen = screenManager.activeScreen();
                Point gridCenter = switch (activeScreenHintGridArea.center()) {
                    case SCREEN_CENTER -> gridScreen.rectangle().center();
                    case MOUSE -> new Point(mouseX, mouseY);
                };
                fixedSizeHintGrids.add(
                        screenFixedSizeHintGrid(activeScreenHintGridArea, screenManager.activeScreen(),
                                gridCenter, hintGrid.rowCount(), hintGrid.columnCount()));
            }
            else if (hintGrid.area() instanceof AllScreensHintGridArea allScreensHintGridArea) {
                for (Screen screen : screenManager.screens()) {
                    Point gridCenter = screen.rectangle().center();
                    fixedSizeHintGrids.add(
                            screenFixedSizeHintGrid(allScreensHintGridArea, screen, gridCenter,
                                    hintGrid.rowCount(), hintGrid.columnCount()));
                }
            }
            else if (hintGrid.area() instanceof ActiveWindowHintGridArea activeWindowHintGridArea) {
                Rectangle activeWindowRectangle = WindowsOverlay.activeWindowRectangle(
                        activeWindowHintGridArea.widthPercent(),
                        activeWindowHintGridArea.heightPercent());
                Point gridCenter = activeWindowRectangle.center();
                int hintMeshX, hintMeshY, hintMeshWidth, hintMeshHeight, rowCount,
                        columnCount;
                hintMeshWidth = activeWindowRectangle.width();
                hintMeshHeight = activeWindowRectangle.height();
                hintMeshX = gridCenter.x() - hintMeshWidth / 2;
                hintMeshY = gridCenter.y() - hintMeshHeight / 2;
                Screen activeScreen = screenManager.activeScreen();
                rowCount = dpiDescaled(hintGrid.rowCount(), activeScreen.dpi());
                columnCount = dpiDescaled(hintGrid.columnCount(), activeScreen.dpi());
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
            int hintCount = positionHistory.size();
            List<Hint> hints = new ArrayList<>(hintCount);
            List<Key> selectionKeySubset = maxPositionHistorySize >=
                                           hintMeshConfiguration.selectionKeys().size() ?
                    hintMeshConfiguration.selectionKeys() :
                    hintMeshConfiguration.selectionKeys()
                                         .subList(0, maxPositionHistorySize);
            int hintLength = (int) Math.ceil(Math.log(maxPositionHistorySize) /
                                             Math.log(selectionKeySubset.size()));
            for (Point point : positionHistory) {
                List<Key> keySequence = hintKeySequence(selectionKeySubset, hintLength,
                        idByPosition.get(point) % maxPositionHistorySize);
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

    private FixedSizeHintGrid screenFixedSizeHintGrid(HintGridArea area, Screen screen,
                                                      Point gridCenter, int rowCount,
                                                      int columnCount) {
        if (!(area instanceof ActiveScreenHintGridArea) &&
            !(area instanceof AllScreensHintGridArea))
            throw new IllegalArgumentException();
        int hintMeshX, hintMeshY, hintMeshWidth, hintMeshHeight;
        double screenWidthPercent, screenHeightPercent;
        if (area instanceof ActiveScreenHintGridArea activeScreenHintGridArea) {
            screenWidthPercent = activeScreenHintGridArea.widthPercent();
            screenHeightPercent = activeScreenHintGridArea.heightPercent();
        }
        else if (area instanceof AllScreensHintGridArea allScreensHintGridArea) {
            screenWidthPercent = allScreensHintGridArea.widthPercent();
            screenHeightPercent = allScreensHintGridArea.heightPercent();
        }
        else
            throw new IllegalStateException();
        hintMeshWidth = (int) (screen.rectangle().width() * screenWidthPercent);
        hintMeshHeight = (int) (screen.rectangle().height() * screenHeightPercent);
        hintMeshX = gridCenter.x() - hintMeshWidth / 2;
        hintMeshY = gridCenter.y() - hintMeshHeight / 2;
        int dpi = screen.dpi();
        int dpiDescaledRowCount = dpiDescaled(rowCount, dpi);
        int dpiDescaledColumnCount = dpiDescaled(columnCount, dpi);
        return new FixedSizeHintGrid(hintMeshX, hintMeshY, hintMeshWidth, hintMeshHeight,
                dpiDescaledRowCount, dpiDescaledColumnCount);
    }

    /**
     * 26 rows, scale 150% (== dpi 144) -> 18 rows.
     */
    private static int dpiDescaled(int value, int dpi) {
        return (int) Math.ceil((double) value * 96 / dpi);
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
            if (hintMeshConfiguration.savePositionAfterSelection())
                savePosition();
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

    public void savePosition() {
        Point point = new Point(mouseX, mouseY);
        if (positionHistory.contains(point))
            return;
        idByPosition.put(point, positionIdCount);
        if (positionIdCount == Integer.MAX_VALUE)
            positionIdCount = 0;
        else
            positionIdCount++;
        if (positionHistory.size() == maxPositionHistorySize)
            positionHistory.removeFirst();
        positionHistory.add(point);
        positionCycleIndex = positionHistory.size() - 1;
        logger.debug(
                "Saved mouse position " + point.x() + "," + point.y() + " to history");
    }

    public void clearPositionHistory() {
        positionHistory.clear();
        idByPosition.clear();
        positionIdCount = 0;
        positionCycleIndex = 0;
        logger.debug("Reset mouse position history");
    }

    public void cycleNextPosition() {
        if (positionHistory.isEmpty())
            return;
        findPositionHistoryEntryMatchingCurrentPosition();
        positionCycleIndex = (positionCycleIndex + 1) % positionHistory.size();
        Point point = positionHistory.get(positionCycleIndex);
        mouseController.moveTo(point.x(), point.y());
        positionHistoryListeners.forEach(PositionHistoryListener::cycledPosition);
    }

    private void findPositionHistoryEntryMatchingCurrentPosition() {
        for (int positionIndex = 0;
             positionIndex < positionHistory.size(); positionIndex++) {
            Point point = positionHistory.get(positionIndex);
            if (point.x() == mouseX && point.y() == mouseY) {
                positionCycleIndex = positionIndex;
                break;
            }
        }
    }

    public void cyclePreviousPosition() {
        if (positionHistory.isEmpty())
            return;
        findPositionHistoryEntryMatchingCurrentPosition();
        positionCycleIndex = (positionCycleIndex - 1 + positionHistory.size()) %
                             positionHistory.size();
        Point point = positionHistory.get(positionCycleIndex);
        mouseController.moveTo(point.x(), point.y());
        positionHistoryListeners.forEach(PositionHistoryListener::cycledPosition);
    }

}
