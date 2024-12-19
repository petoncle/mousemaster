package mousemaster;

import mousemaster.HintGridArea.ActiveScreenHintGridArea;
import mousemaster.HintGridArea.ActiveWindowHintGridArea;
import mousemaster.HintGridArea.AllScreensHintGridArea;
import mousemaster.HintMesh.HintMeshBuilder;
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
    private final Map<HintMeshTypeAndSelectionKeys, HintMesh>
            previousHintMeshByTypeAndSelectionKeys = new HashMap<>();
    private int mouseX, mouseY;
    private Mode currentMode;
    private final List<Point> positionHistory = new ArrayList<>();
    private final int maxPositionHistorySize;
    private Point lastSelectedHintPoint;
    /**
     * Used for deterministic hint key sequences.
     */
    private int positionIdCount = 0;
    private final Map<Point, Integer> idByPosition = new HashMap<>();
    private int positionCycleIndex = 0;
    private Hint selectedHintToFinalize;

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

    public void update(double delta) {
        // Relying on mouseMoved() callbacks is not enough because the mouse may not move
        // when a hint is selected (and no there is no callback).
        tryFinalizeHintSelection();
    }

    @Override
    public void mouseMoved(int x, int y) {
        if (mouseController.jumping())
            return;
        mouseX = x;
        mouseY = y;
        tryFinalizeHintSelection();
    }

    private void tryFinalizeHintSelection() {
        if (selectedHintToFinalize != null) {
            if (!mouseController.jumping()) {
                finalizeHintSelection(selectedHintToFinalize);
                selectedHintToFinalize = null;
            }
        }
    }

    public boolean finalizingHintSelection() {
        return selectedHintToFinalize != null;
    }

    @Override
    public void modeChanged(Mode newMode) {
        HintMeshConfiguration hintMeshConfiguration = newMode.hintMesh();
        if (!hintMeshConfiguration.enabled()) {
            currentMode = newMode;
            previousHintMeshByTypeAndSelectionKeys.clear();
            WindowsOverlay.hideHintMesh();
            return;
        }
        if (!hintMeshConfiguration.visible()) {
            // This makes the behavior of the hint different depending on whether it is visible.
            // An alternative would be a setting like hint.reset-focused-key-sequence-history=true.
            previousHintMeshByTypeAndSelectionKeys.clear();
            WindowsOverlay.hideHintMesh();
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
        previousHintMeshByTypeAndSelectionKeys.put(
                hintMeshConfiguration.typeAndSelectionKeys(), hintMesh);
        WindowsOverlay.setHintMesh(hintMesh);
    }

    private HintMesh buildHintMesh(HintMeshConfiguration hintMeshConfiguration) {
        HintMeshBuilder hintMesh = new HintMeshBuilder();
        hintMesh.visible(hintMeshConfiguration.visible())
                .type(hintMeshConfiguration.typeAndSelectionKeys().type())
                .fontName(hintMeshConfiguration.fontName())
                .fontSize(hintMeshConfiguration.fontSize())
                .fontHexColor(hintMeshConfiguration.fontHexColor())
                .fontOpacity(hintMeshConfiguration.fontOpacity())
                .prefixFontHexColor(
                        hintMeshConfiguration.prefixFontHexColor())
                .highlightFontScale(hintMeshConfiguration.highlightFontScale())
                .boxHexColor(hintMeshConfiguration.boxHexColor())
                .boxOpacity(hintMeshConfiguration.boxOpacity())
                .boxBorderThickness(hintMeshConfiguration.boxBorderThickness())
                .boxOutlineHexColor(hintMeshConfiguration.boxOutlineHexColor())
                .boxOutlineOpacity(hintMeshConfiguration.boxOutlineOpacity())
        ;
        HintMeshType type = hintMeshConfiguration.typeAndSelectionKeys().type();
        if (type instanceof HintMeshType.HintGrid hintGrid) {
            List<FixedSizeHintGrid> fixedSizeHintGrids = new ArrayList<>();
            if (hintGrid.area() instanceof ActiveScreenHintGridArea activeScreenHintGridArea) {
                Screen gridScreen = screenManager.activeScreen();
                Point gridCenter = switch (activeScreenHintGridArea.center()) {
                    case SCREEN_CENTER -> gridScreen.rectangle().center();
                    case MOUSE -> new Point(mouseX, mouseY);
                    case LAST_SELECTED_HINT ->
                            lastSelectedHintPoint == null ? new Point(mouseX, mouseY) :
                                    lastSelectedHintPoint;
                };
                fixedSizeHintGrids.add(fixedSizeHintGrid(
                        screenManager.activeScreen().rectangle(), gridCenter, hintGrid.maxRowCount(),
                        hintGrid.maxColumnCount(), hintGrid.cellWidth(),
                        hintGrid.cellHeight()));
            }
            else if (hintGrid.area() instanceof AllScreensHintGridArea allScreensHintGridArea) {
                List<Screen> sortedScreens = //
                        screenManager.screens()
                                     .stream()
                                     .sorted(Comparator.comparing(
                                                               (Screen s) -> s.rectangle().x())
                                                       .thenComparing(
                                                               s -> s.rectangle().y()))
                                     .toList();
                for (Screen screen : sortedScreens) {
                    Point gridCenter = screen.rectangle().center();
                    fixedSizeHintGrids.add(
                            fixedSizeHintGrid(screen.rectangle(),
                                    gridCenter, hintGrid.maxRowCount(),
                                    hintGrid.maxColumnCount(), hintGrid.cellWidth(),
                                    hintGrid.cellHeight()));
                }
            }
            else if (hintGrid.area() instanceof ActiveWindowHintGridArea activeWindowHintGridArea) {
                Rectangle activeWindowRectangle =
                        WindowsOverlay.activeWindowRectangle(1, 1, 0, 0, 0, 0);
                Screen activeScreen = screenManager.activeScreen();
                // TODO should active screen scale be taken into account?
                Point gridCenter = activeWindowRectangle.center();
                fixedSizeHintGrids.add(fixedSizeHintGrid(
                        activeWindowRectangle, gridCenter, hintGrid.maxRowCount(),
                        hintGrid.maxColumnCount(), hintGrid.cellWidth(),
                        hintGrid.cellHeight()));
            }
            else
                throw new IllegalStateException();
            int hintCount = fixedSizeHintGrids.getFirst().rowCount *
                            fixedSizeHintGrids.getFirst().columnCount *
                            fixedSizeHintGrids.size();
            List<Hint> hints = new ArrayList<>();
            for (FixedSizeHintGrid fixedSizeHintGrid : fixedSizeHintGrids) {
                int beginHintIndex = hints.size();
                hints.addAll(buildHints(fixedSizeHintGrid,
                        hintMeshConfiguration.typeAndSelectionKeys().selectionKeys(),
                        hintCount,
                        beginHintIndex));
            }
            hintMesh.hints(hints);
        }
        else {
            int hintCount = positionHistory.size();
            List<Hint> hints = new ArrayList<>(hintCount);
            for (Point point : positionHistory) {
                List<Key> keySequence = hintKeySequence(
                        hintMeshConfiguration.typeAndSelectionKeys().selectionKeys(), hintCount,
                        idByPosition.get(point) % maxPositionHistorySize, -1, -1, -1,
                        -1);
                hints.add(new Hint(point.x(), point.y(), -1, -1, keySequence));
            }
            hintMesh.hints(hints);
        }
        HintMesh previousHintMesh = previousHintMeshByTypeAndSelectionKeys.get(
                hintMeshConfiguration.typeAndSelectionKeys());
        if (previousHintMesh != null &&
            previousHintMesh.hints().equals(hintMesh.hints())) {
            // Keep the old focusedKeySequence.
            // This is useful for hint-then-click-mode that extends hint-mode.
            hintMesh.focusedKeySequence(previousHintMesh.focusedKeySequence());
        }
        return hintMesh.build();
    }

    private static List<Hint> buildHints(FixedSizeHintGrid fixedSizeHintGrid,
                                         List<Key> selectionKeys, int hintCount,
                                         int beginHintIndex) {
        int rowCount = fixedSizeHintGrid.rowCount();
        int columnCount = fixedSizeHintGrid.columnCount();
        int hintMeshWidth = fixedSizeHintGrid.hintMeshWidth();
        int hintMeshHeight = fixedSizeHintGrid.hintMeshHeight();
        int hintMeshX = fixedSizeHintGrid.hintMeshX();
        int hintMeshY = fixedSizeHintGrid.hintMeshY();
        double cellWidth = fixedSizeHintGrid.cellWidth;
        double cellHeight = fixedSizeHintGrid.cellHeight;
        int spareWidthPixelCount = (int) (hintMeshWidth - cellWidth * columnCount);
        int spareHeightPixelCount = (int) (hintMeshHeight - cellHeight * rowCount);
        boolean[] rowExtraPixelDistribution = distributeTrueUniformly(rowCount, spareHeightPixelCount);
        boolean[] columnExtraPixelDistribution = distributeTrueUniformly(columnCount, spareWidthPixelCount);
        int gridHintCount = rowCount * columnCount;
        List<Hint> hints = new ArrayList<>(gridHintCount);
        int hintIndex = beginHintIndex;
        double rowHeightOffset = 0;
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            double cellHeightWithExtra =
                    cellHeight + (rowExtraPixelDistribution[rowIndex] ? 1 : 0);
            double columnWidthOffset = 0;
            for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                List<Key> keySequence = hintKeySequence(selectionKeys, hintCount, hintIndex,
                        rowIndex, columnIndex, rowCount, columnCount);
                double cellWidthWithExtra =
                        cellWidth + (columnExtraPixelDistribution[columnIndex] ? 1 : 0);
                double hintCenterX = hintMeshX + columnWidthOffset + cellWidthWithExtra / 2d;
                double hintCenterY = hintMeshY + rowHeightOffset + cellHeightWithExtra / 2d;
                hints.add(new Hint(hintCenterX, hintCenterY,
                        cellWidthWithExtra,
                        cellHeightWithExtra,
                        keySequence));
                hintIndex++;
                columnWidthOffset += cellWidthWithExtra;
            }
            rowHeightOffset += cellHeightWithExtra;
        }
        return hints;
    }

    /**
     * If columnCount * cellWidth is 1898, spread the 1920 - 1898 = 22 pixels across the cells.
     */
    private static boolean[] distributeTrueUniformly(int arraySize, int trueCount) {
        if (trueCount > arraySize)
            throw new IllegalArgumentException();
        boolean[] distribution = new boolean[arraySize];
        double step = (double) arraySize / trueCount;
        double position = 0.0;
        for (int i = 0; i < trueCount; i++) {
            int index = (int) position;
            distribution[index] = true;
            position += step;
        }
        return distribution;
    }

    private static List<Key> hintKeySequence(List<Key> keys, int hintCount,
                                             int hintIndex, int rowIndex, int columnIndex,
                                             int rowCount, int columnCount) {
        int keyCount = keys.size();
        if (rowIndex != -1) {
            if (rowCount * columnCount < keyCount) {
                return List.of(keys.get(hintIndex));
            }
            // We want the hints to look like this:
            // (column prefix)(row suffix)
            // (aa)(aa), (ab)(aa), ..., (ba)(aa), ..., (zz)(aa)
            // (aa)(ab), (ab)(ab), ..., (ba)(aa), ..., (zz)(ab)
            // ...
            // (aa)(ba), (ab)(ba), ..., (ba)(aa), ..., (zz)(ba)
            // ...
            // (aa)(zz), (ab)(zz), ..., (ba)(aa), ..., (zz)(zz)
            // The ideal situation is when rowCount = columnCount = hintKeys.size().
            if (rowCount <= keyCount && columnCount <= keyCount &&
                rowCount * columnCount <= Math.pow(keyCount, 2)) {
                return List.of(
                        keys.get(columnIndex),
                        keys.get(rowIndex)
                );
            }
            else if (rowCount * columnCount >= 100 &&
                     rowCount <= Math.pow(keyCount, 2) &&
                     columnCount <= Math.pow(keyCount, 2) &&
                     rowCount * columnCount < Math.pow(keyCount, 4)) {
                // Length 3 if rowCount or columnCount <= keyCount.
                if (columnCount <= keyCount)
                    return List.of(
                            keys.get(columnIndex),
                            keys.get(rowIndex / keyCount),
                            keys.get(rowIndex % keyCount)
                    );
                if (rowCount <= keyCount)
                    return List.of(
                            keys.get(columnIndex / keyCount),
                            keys.get(columnIndex % keyCount),
                            keys.get(rowIndex)
                    );
                // We don't do length 4 if keyCount too large because the hints would
                // always start with A or B.
                if (keyCount <= 6) // 6^4 = 1296 hints
                    return List.of(
                            keys.get(columnIndex / keyCount),
                            keys.get(columnIndex % keyCount),
                            keys.get(rowIndex / keyCount),
                            keys.get(rowIndex % keyCount)
                    );
            }
        }
        // Give up trying to have (column prefix)(row suffix).
        // Just try to minimize the hint length.
        // Find hintLength such that hintKeyCount^hintLength >= rowCount*columnCount
        int hintLength = (int) Math.ceil(
                            Math.log(hintCount) / Math.log(keyCount));
        List<Key> keySequence = new ArrayList<>();
        for (int i = 0; i < hintLength; i++) {
            keySequence.add(
                    keys.get((int) (hintIndex / Math.pow(keyCount, i) % keyCount)));
        }
        return keySequence;
    }

    private FixedSizeHintGrid fixedSizeHintGrid(Rectangle areaRectangle,
                                                Point gridCenter, int maxRowCount,
                                                int maxColumnCount, double cellWidth,
                                                double cellHeight) {
        int hintMeshX, hintMeshY, hintMeshWidth, hintMeshHeight;
        int rowCount = Math.max(1, Math.min(maxRowCount,
                (int) ((double) areaRectangle.height() / cellHeight)));
        int columnCount = Math.max(1, Math.min(maxColumnCount,
                (int) ((double) areaRectangle.width() / cellWidth)));
        hintMeshWidth = (int) Math.ceil(columnCount * cellWidth);
        // If there is space left around the edges, and the max cell count (in one direction) is reached,
        // we want to increase the cell size only if the space left is smaller than
        // the user-defined max size of a cell. Otherwise, it is a 2-pass hint scenario,
        // and it means there is a lot of space left, and we do not want to fill it.
        // If the max cell count is not reached, we can either:
        // 1. increase the cell size (even if it becomes greater than the user-defined max size)
        // 2. or increase the cell count and decrease the size so that it fills the space
        // (Currently, we only do 1.)
        boolean maxColumnCountReached = columnCount == maxColumnCount;
        int spareWidth = areaRectangle.width() - hintMeshWidth;
        if (spareWidth > 0) {
            if (maxColumnCountReached) {
                if (spareWidth < cellWidth) {
                    hintMeshWidth = areaRectangle.width();
                    cellWidth = (double) areaRectangle.width() / columnCount;
                }
            }
            else {
                // (Imagine the max column count is infinite.)
                hintMeshWidth = areaRectangle.width();
                cellWidth = (double) areaRectangle.width() / columnCount;
            }
        }
        hintMeshHeight = (int) Math.ceil(rowCount * cellHeight);
        boolean maxRowCountReached = rowCount == maxRowCount;
        int spareHeight = areaRectangle.height() - hintMeshHeight;
        if (spareHeight > 0) {
            if (maxRowCountReached) {
                if (spareHeight < cellHeight) {
                    hintMeshHeight = areaRectangle.height();
                    cellHeight = (double) areaRectangle.height() / rowCount;
                }
            }
            else {
                hintMeshHeight = areaRectangle.height();
                cellHeight = (double) areaRectangle.height() / rowCount;
            }
        }
        if (areaRectangle.height() - hintMeshHeight > 0
            && areaRectangle.height() - hintMeshHeight < rowCount)
            hintMeshHeight = areaRectangle.height();
        hintMeshX = gridCenter.x() - hintMeshWidth / 2;
        hintMeshY = gridCenter.y() - hintMeshHeight / 2;
        return new FixedSizeHintGrid(hintMeshX, hintMeshY, hintMeshWidth, hintMeshHeight,
                rowCount, columnCount, cellWidth, cellHeight);
    }

    private record FixedSizeHintGrid(int hintMeshX, int hintMeshY, int hintMeshWidth,
                                     int hintMeshHeight, int rowCount, int columnCount,
                                     double cellWidth, double cellHeight) {

    }

    @Override
    public void modeTimedOut() {
        // No op.
    }

    public PressKeyEventProcessing keyPressed(Key key) {
        HintMeshConfiguration hintMeshConfiguration = currentMode.hintMesh();
        if (!hintMeshConfiguration.enabled())
            return PressKeyEventProcessing.unhandled();
        if (key.equals(hintMeshConfiguration.undoKey())) {
            List<Key> focusedKeySequence = hintMesh.focusedKeySequence();
            if (!focusedKeySequence.isEmpty()) {
                hintMesh = hintMesh.builder()
                                   .focusedKeySequence(focusedKeySequence.subList(0,
                                           focusedKeySequence.size() - 1))
                                   .build();
                previousHintMeshByTypeAndSelectionKeys.put(
                        hintMeshConfiguration.typeAndSelectionKeys(), hintMesh);
                WindowsOverlay.setHintMesh(hintMesh);
                return PressKeyEventProcessing.hintUndo();
            }
            return PressKeyEventProcessing.unhandled(); // ComboWatcher can have a go at it.
        }
        if (!selectionKeySubset.contains(key))
            return PressKeyEventProcessing.unhandled();
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
            return PressKeyEventProcessing.unhandled();
        if (exactMatchHint != null) {
            lastSelectedHintPoint = new Point((int) exactMatchHint.centerX(),
                    (int) exactMatchHint.centerY());
             if (hintMeshConfiguration.moveMouse()) {
                 // After this moveTo call, the move is not fully completed.
                 // We need to wait until the jump completes before a click can be performed at
                 // the new position.
                 mouseController.moveTo((int) exactMatchHint.centerX(),
                         (int) exactMatchHint.centerY());
                selectedHintToFinalize = exactMatchHint;
             }
             else {
                finalizeHintSelection(exactMatchHint);
             }
            return hintMeshConfiguration.swallowHintEndKeyPress() ?
                    PressKeyEventProcessing.swallowedHintEnd() :
                    PressKeyEventProcessing.unswallowedHintEnd();
        }
        else {
            hintMesh =
                    hintMesh.builder().focusedKeySequence(newFocusedKeySequence).build();
            previousHintMeshByTypeAndSelectionKeys.put(
                    hintMeshConfiguration.typeAndSelectionKeys(), hintMesh);
            WindowsOverlay.setHintMesh(hintMesh);
            return PressKeyEventProcessing.partOfHintPrefix();
        }
    }

    private void finalizeHintSelection(Hint hint) {
        HintMeshConfiguration hintMeshConfiguration = currentMode.hintMesh();
        if (hintMeshConfiguration.savePositionAfterSelection())
            savePosition(new Point((int) hint.centerX(), (int) hint.centerY()));
        if (hintMeshConfiguration.modeAfterSelection() != null) {
            logger.debug("Hint " + hint.keySequence()
                                       .stream()
                                       .map(Key::name)
                                       .toList() +
                         " selected, switching to " +
                         hintMeshConfiguration.modeAfterSelection());
            modeController.switchMode(hintMeshConfiguration.modeAfterSelection());
        }
        else {
            hintMesh =
                    hintMesh.builder().focusedKeySequence(List.of()).build();
            previousHintMeshByTypeAndSelectionKeys.put(
                    hintMeshConfiguration.typeAndSelectionKeys(), hintMesh);
            WindowsOverlay.setHintMesh(hintMesh);
        }
    }

    public void saveCurrentPosition() {
        savePosition(new Point(mouseX, mouseY));
    }

    public void savePosition(Point point) {
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
                "Saved position " + point.x() + "," + point.y() + " to history");
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
