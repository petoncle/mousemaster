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
                .selectedPrefixFontHexColor(
                        hintMeshConfiguration.selectedPrefixFontHexColor())
                .boxHexColor(hintMeshConfiguration.boxHexColor());
        HintMeshType type = hintMeshConfiguration.typeAndSelectionKeys().type();
        if (type instanceof HintMeshType.HintGrid hintGrid) {
            List<FixedSizeHintGrid> fixedSizeHintGrids = new ArrayList<>();
            if (hintGrid.area() instanceof ActiveScreenHintGridArea activeScreenHintGridArea) {
                Screen gridScreen = screenManager.activeScreen();
                Point gridCenter = switch (activeScreenHintGridArea.center()) {
                    case SCREEN_CENTER -> gridScreen.rectangle().center();
                    case MOUSE -> new Point(mouseX, mouseY);
                };
                fixedSizeHintGrids.add(screenFixedSizeHintGrid(activeScreenHintGridArea,
                        screenManager.activeScreen(), gridCenter, hintGrid.maxRowCount(),
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
                            screenFixedSizeHintGrid(allScreensHintGridArea, screen,
                                    gridCenter, hintGrid.maxRowCount(),
                                    hintGrid.maxColumnCount(), hintGrid.cellWidth(),
                                    hintGrid.cellHeight()));
                }
            }
            else if (hintGrid.area() instanceof ActiveWindowHintGridArea activeWindowHintGridArea) {
                Rectangle activeWindowRectangle =
                        WindowsOverlay.activeWindowRectangle(1, 1, 0, 0, 0, 0);
                Point gridCenter = activeWindowRectangle.center();
                int hintMeshX, hintMeshY, hintMeshWidth, hintMeshHeight, rowCount, columnCount;
                Screen activeScreen = screenManager.activeScreen();
                rowCount = Math.max(1, Math.min(hintGrid.maxRowCount(),
                        (int) ((double) activeWindowRectangle.height() /
                               hintGrid.cellHeight() / activeScreen.scale())));
                columnCount = Math.max(1, Math.min(hintGrid.maxColumnCount(),
                        (int) ((double) activeWindowRectangle.width() /
                               hintGrid.cellWidth() / activeScreen.scale())));
                hintMeshWidth = Math.min(activeWindowRectangle.width(),
                        (int) (columnCount * hintGrid.cellWidth() /
                               activeScreen.scale()));
                hintMeshHeight = Math.min(activeWindowRectangle.height(),
                        (int) (rowCount * hintGrid.cellHeight() / activeScreen.scale()));
                hintMeshX = gridCenter.x() - hintMeshWidth / 2;
                hintMeshY = gridCenter.y() - hintMeshHeight / 2;
                fixedSizeHintGrids.add(
                        new FixedSizeHintGrid(hintMeshX, hintMeshY, hintMeshWidth,
                                hintMeshHeight, rowCount, columnCount));
            }
            else
                throw new IllegalStateException();
            List<Key> selectionKeySubset =
                    gridSelectionKeySubset(hintMeshConfiguration.typeAndSelectionKeys().selectionKeys(),
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
                                           hintMeshConfiguration.typeAndSelectionKeys()
                                                                .selectionKeys()
                                                                .size() ?
                    hintMeshConfiguration.typeAndSelectionKeys().selectionKeys() :
                    hintMeshConfiguration.typeAndSelectionKeys()
                                         .selectionKeys()
                                         .subList(0, maxPositionHistorySize);
            int hintLength = (int) Math.ceil(Math.log(maxPositionHistorySize) /
                                             Math.log(selectionKeySubset.size()));
            for (Point point : positionHistory) {
                List<Key> keySequence = hintKeySequence(selectionKeySubset, hintLength,
                        idByPosition.get(point) % maxPositionHistorySize);
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

    // TODO if hintMeshWidth is 1898, spread the 22 px on the cells
    //  (26 is columnCount) 22/26 <= 1 meaning we have to add 1 px to some cells.
    //  22 cells will have the extra px, cell index 0, (maybe not 1)...,
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
                hints.add(new Hint(hintCenterX, hintCenterY, cellWidth, cellHeight, keySequence));
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
                                                      Point gridCenter, int maxRowCount,
                                                      int maxColumnCount, int cellWidth,
                                                      int cellHeight) {
        if (!(area instanceof ActiveScreenHintGridArea) &&
            !(area instanceof AllScreensHintGridArea))
            throw new IllegalArgumentException();
        int hintMeshX, hintMeshY, hintMeshWidth, hintMeshHeight;
        int rowCount = Math.max(1, Math.min(maxRowCount,
                (int) ((double) screen.rectangle().height() / cellHeight)));
        int columnCount = Math.max(1, Math.min(maxColumnCount,
                (int) ((double) screen.rectangle().width() / cellWidth)));
        hintMeshWidth = Math.min(screen.rectangle().width(),
                columnCount * cellWidth);
        hintMeshHeight = Math.min(screen.rectangle().height(),
                rowCount * cellHeight);
        hintMeshX = gridCenter.x() - hintMeshWidth / 2;
        hintMeshY = gridCenter.y() - hintMeshHeight / 2;
        return new FixedSizeHintGrid(hintMeshX, hintMeshY, hintMeshWidth, hintMeshHeight,
                rowCount, columnCount);
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
            // After this moveTo call, the move is not fully completed.
            // We need to wait until the jump completes before a click can be performed at
            // the new position.
            mouseController.moveTo(exactMatchHint.centerX(), exactMatchHint.centerY());
            this.selectedHintToFinalize = exactMatchHint;
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
            savePosition();
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
