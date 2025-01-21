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
    private HintMesh hintMesh;
    private Set<Key> selectionKeySubset;
    private final Map<HintMeshTypeAndSelectionKeys, HintMeshAndPreviousModeSelectedHintPoint>
            previousHintMeshByTypeAndSelectionKeys = new HashMap<>();
    private boolean hintJustSelected = false;
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

    private record HintMeshAndPreviousModeSelectedHintPoint(HintMesh hintMesh,
                                                            Point previousModeSelectedHintPoint) {

    }

    public HintManager(int maxPositionHistorySize, ScreenManager screenManager,
                       MouseController mouseController) {
        this.maxPositionHistorySize = maxPositionHistorySize;
        this.screenManager = screenManager;
        this.mouseController = mouseController;
    }

    public Point lastSelectedHintPoint() {
        logger.trace("Zoom " + lastSelectedHintPoint);
        return lastSelectedHintPoint;
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
        if (hintJustSelected) {
            // When going from hint2-1 to hint2-2, even if we already have been in hint2-2
            // before, we don't want the old state of hint2-2.
            hintJustSelected = false;
            previousHintMeshByTypeAndSelectionKeys.remove(
                    hintMeshConfiguration.typeAndSelectionKeys());
        }
        else if (hintMeshConfiguration.typeAndSelectionKeys().type() instanceof HintMeshType.HintGrid hintGrid &&
                         hintGrid.area() instanceof  ActiveScreenHintGridArea activeScreenHintGridArea &&
                         activeScreenHintGridArea.center() == ActiveScreenHintGridAreaCenter.LAST_SELECTED_HINT) {
            // When going back from hint3-3 to hint3-2, we find the selected hint of hint1 that led to hint3-2.
            // (Because currently, last selected hint is the hint selected by hint3-2.)
            HintMeshAndPreviousModeSelectedHintPoint
                    hintMeshAndPreviousModeSelectedHintPoint =
                    previousHintMeshByTypeAndSelectionKeys.get(
                            hintMeshConfiguration.typeAndSelectionKeys());
            if (hintMeshAndPreviousModeSelectedHintPoint != null)
                lastSelectedHintPoint =
                        hintMeshAndPreviousModeSelectedHintPoint.previousModeSelectedHintPoint;
        }
        if (!hintMeshConfiguration.enabled()) {
            currentMode = newMode;
            previousHintMeshByTypeAndSelectionKeys.clear();
            WindowsOverlay.hideHintMesh();
            return;
        }
        if (!hintMeshConfiguration.visible()) {
            // This makes the behavior of the hint different depending on whether it is visible.
            // An alternative would be a setting like hint.reset-focused-key-sequence-history-after-selection=true.
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
        previousHintMeshByTypeAndSelectionKeys.put(
                hintMeshConfiguration.typeAndSelectionKeys(),
                new HintMeshAndPreviousModeSelectedHintPoint(newHintMesh, lastSelectedHintPoint));
        hintMesh = newHintMesh;
        WindowsOverlay.setHintMesh(hintMesh);
    }

    private HintMesh buildHintMesh(HintMeshConfiguration hintMeshConfiguration) {
        HintMeshBuilder hintMesh = new HintMeshBuilder();
        hintMesh.visible(hintMeshConfiguration.visible())
                .type(hintMeshConfiguration.typeAndSelectionKeys().type())
                .fontName(hintMeshConfiguration.fontName())
                .fontSize(hintMeshConfiguration.fontSize())
                .fontSpacingPercent(hintMeshConfiguration.fontSpacingPercent())
                .fontHexColor(hintMeshConfiguration.fontHexColor())
                .fontOpacity(hintMeshConfiguration.fontOpacity())
                .fontOutlineThickness(hintMeshConfiguration.fontOutlineThickness())
                .fontOutlineHexColor(hintMeshConfiguration.fontOutlineHexColor())
                .fontOutlineOpacity(hintMeshConfiguration.fontOutlineOpacity())
                .prefixFontHexColor(
                        hintMeshConfiguration.prefixFontHexColor())
                .highlightFontScale(hintMeshConfiguration.highlightFontScale())
                .boxHexColor(hintMeshConfiguration.boxHexColor())
                .boxOpacity(hintMeshConfiguration.boxOpacity())
                .boxBorderThickness(hintMeshConfiguration.boxBorderThickness())
                .boxBorderLength(hintMeshConfiguration.boxBorderLength())
                .boxBorderHexColor(hintMeshConfiguration.boxBorderHexColor())
                .boxBorderOpacity(hintMeshConfiguration.boxBorderOpacity())
                .expandBoxes(hintMeshConfiguration.expandBoxes())
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
                logger.trace("Grid center " + gridCenter);
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
                        beginHintIndex, hintGrid.subgridRowCount(),
                        hintGrid.subgridColumnCount(), hintGrid.rowOriented()));
            }
            hintMesh.hints(hints);
        }
        else {
            if (positionHistory.isEmpty())
                saveCurrentPosition();
            int hintCount = positionHistory.size();
            List<Hint> hints = new ArrayList<>(hintCount);
            for (Point point : positionHistory) {
                List<Key> keySequence = hintKeySequence(
                        hintMeshConfiguration.typeAndSelectionKeys().selectionKeys(), hintCount,
                        idByPosition.get(point) % maxPositionHistorySize, -1, -1, -1,
                        -1, -1, -1, false);
                hints.add(new Hint(point.x(), point.y(), -1, -1, keySequence));
            }
            hintMesh.hints(hints);
        }
        HintMeshAndPreviousModeSelectedHintPoint
                previousHintMeshAndPreviousModeSelectedHintPoint =
                previousHintMeshByTypeAndSelectionKeys.get(
                        hintMeshConfiguration.typeAndSelectionKeys());
        if (previousHintMeshAndPreviousModeSelectedHintPoint != null &&
            previousHintMeshAndPreviousModeSelectedHintPoint.hintMesh.hints()
                                                                     .equals(hintMesh.hints())) {
            // Keep the old focusedKeySequence.
            // This is useful for hint-then-click-mode that extends hint-mode.
            hintMesh.focusedKeySequence(
                    previousHintMeshAndPreviousModeSelectedHintPoint.hintMesh.focusedKeySequence());
        }
        return hintMesh.build();
    }

    private static List<Hint> buildHints(FixedSizeHintGrid fixedSizeHintGrid,
                                         List<Key> selectionKeys, int hintCount,
                                         int beginHintIndex, int subgridRowCount,
                                         int subgridColumnCount, boolean rowOriented) {
        int rowCount = fixedSizeHintGrid.rowCount();
        int columnCount = fixedSizeHintGrid.columnCount();
        double hintMeshWidth = fixedSizeHintGrid.hintMeshWidth();
        double hintMeshHeight = fixedSizeHintGrid.hintMeshHeight();
        double hintMeshX = fixedSizeHintGrid.hintMeshX();
        double hintMeshY = fixedSizeHintGrid.hintMeshY();
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
                        rowIndex, columnIndex, rowCount, columnCount, subgridRowCount, subgridColumnCount, rowOriented);
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
                                             int rowCount, int columnCount,
                                             int subgridRowCount, int subgridColumnCount,
                                             boolean rowOriented) {
        int keyCount = keys.size();
        // Number of sub grids in a row.
        int bigColumnCount = (int) Math.ceil((double) columnCount / subgridColumnCount);
        // Number of sub grids in a column.
        int bigRowCount = (int) Math.ceil((double) rowCount / subgridRowCount);
        if (rowIndex != -1) {
            if (rowCount * columnCount <= keyCount) {
                return List.of(keys.get(hintIndex));
            }
            // With no subgrids, we want the hints to look like this:
            // (column prefix)(row suffix)
            // (aa)(aa), (ab)(aa), ..., (ba)(aa), ..., (zz)(aa)
            // (aa)(ab), (ab)(ab), ..., (ba)(aa), ..., (zz)(ab)
            // ...
            // (aa)(ba), (ab)(ba), ..., (ba)(aa), ..., (zz)(ba)
            // ...
            // (aa)(zz), (ab)(zz), ..., (ba)(aa), ..., (zz)(zz)
            // The ideal situation is when rowCount = columnCount = hintKeys.size().
            // With subgrids (here subgridRowCount = 6 and subgridColumnCount = 5):
            // qq qw qe qr qt wq ww we wr wt ... tq tw te tr tt
            // qa qs qd qf qg wa ws wd wf wg ... ta ts td tf tg
            // ...
            // yq yw ye yr yt ... pq pw pe pr pt
            // ...
            // yn ym y, y. y/ ... pn pm p, p. p/
            HintKeySequenceLayout layout =
                    hintKeySequenceLayout(rowOriented, keyCount, columnIndex, rowIndex, columnCount,
                            rowCount, subgridColumnCount, subgridRowCount, bigColumnCount,
                            bigRowCount);
            if (layout.twoOne || layout.twoTwo) {
                // If two*, then try to have each bigColumn (if column-oriented) starts with a different letter
                // TODO Find a non-bruteforce approach?
                while (true) {
                    int first1 =
                            hintKeySequenceLayout(rowOriented, keyCount, 0, 0, columnCount,
                                    rowCount, subgridColumnCount, subgridRowCount,
                                    bigColumnCount,
                                    bigRowCount).first;
                    int first2 =
                            hintKeySequenceLayout(rowOriented, keyCount, rowOriented ? 0 : subgridColumnCount, rowOriented ? subgridRowCount : 0, columnCount,
                                    rowCount, subgridColumnCount, subgridRowCount,
                                    bigColumnCount,
                                    bigRowCount).first;
                    if (first1 / keyCount != first2 / keyCount)
                        break;
                    bigColumnCount++;
                    bigRowCount++;
                    HintKeySequenceLayout newLayout =
                            hintKeySequenceLayout(rowOriented, keyCount, columnIndex, rowIndex, columnCount,
                                    rowCount, subgridColumnCount, subgridRowCount, bigColumnCount,
                                    bigRowCount);
                    if (newLayout.threeOrFour)
                        layout = newLayout;
                    else
                        break;
                }
            }
            if (layout.oneOne) {
                return List.of(
                        keys.get(layout.first),
                        keys.get(layout.second)
                );
            }
            else if (layout.threeOrFour) {
                if (layout.oneTwo)
                    return List.of(
                            keys.get(layout.first),
                            keys.get(layout.second / keyCount),
                            keys.get(layout.second % keyCount)
                    );
                if (layout.twoOne)
                    return List.of(
                            keys.get(layout.first / keyCount),
                            keys.get(layout.first % keyCount),
                            keys.get(layout.second)
                    );
                if (layout.twoTwo) // 6^4 = 1296 hints
                    return List.of(
                            keys.get(layout.first / keyCount),
                            keys.get(layout.first % keyCount),
                            keys.get(layout.second / keyCount),
                            keys.get(layout.second % keyCount)
                    );
            }
        }
        // Give up trying to have (column prefix)(row suffix).
        // Just try to minimize the hint length.
        // Find hintLength such that hintKeyCount^hintLength >= rowCount*columnCount
        int hintLength = Math.max(1, (int) Math.ceil(
                Math.log(hintCount) / Math.log(keyCount)));
        List<Key> keySequence = new ArrayList<>();
        for (int i = 0; i < hintLength; i++) {
            keySequence.add(
                    keys.get((int) (hintIndex / Math.pow(keyCount, i) % keyCount)));
        }
        return keySequence;
    }

    private static HintKeySequenceLayout hintKeySequenceLayout(boolean rowOriented, int keyCount,
                                                               int columnIndex, int rowIndex,
                                                               int columnCount, int rowCount,
                                                               int subgridColumnCount, int subgridRowCount,
                                                               int bigColumnCount, int bigRowCount) {
        int first;
        int maxFirst;
        int second;
        int maxSecond;
        if (rowOriented) {
            first = columnIndex / subgridColumnCount + rowIndex / subgridRowCount * bigColumnCount;
            maxFirst = (columnCount - 1) / subgridColumnCount + (rowCount - 1) / subgridRowCount * bigColumnCount;
            second = columnIndex % subgridColumnCount + rowIndex % subgridRowCount * subgridColumnCount;
            maxSecond = Math.min(columnCount - 1, subgridColumnCount - 1) + Math.min(rowCount - 1, subgridRowCount - 1) * subgridColumnCount;
        }
        else {
            first = rowIndex / subgridRowCount + columnIndex / subgridColumnCount * bigRowCount;
            maxFirst = (rowCount - 1) / subgridRowCount + (columnCount - 1) / subgridColumnCount * bigRowCount;
            second = rowIndex % subgridRowCount + columnIndex % subgridColumnCount * subgridRowCount;
            maxSecond = Math.min(rowCount - 1, subgridRowCount - 1) + Math.min(columnCount - 1, subgridColumnCount - 1) * subgridRowCount;
        }
        boolean oneOne = maxFirst <= keyCount - 1 && maxSecond <= keyCount - 1;
        boolean threeOrFour = !oneOne && rowCount * columnCount >= 100 &&
                              maxFirst <= Math.pow(keyCount, 2) - 1 &&
                              maxSecond <= Math.pow(keyCount, 2) - 1;
        // Length 3 if rowCount or columnCount <= keyCount.
        boolean oneTwo = threeOrFour && maxFirst <= keyCount - 1;
        boolean twoOne = threeOrFour && maxSecond <= keyCount - 1;
        // We don't do length 4 if keyCount too large because the hints would
        // always start with A or B.
        boolean twoTwo = threeOrFour && keyCount <= 6;

        return new HintKeySequenceLayout(first, maxFirst, second, maxSecond, oneOne, threeOrFour, oneTwo, twoOne, twoTwo);
    }

    /**
     * With a simple AA-ZZ layout, first is e.g. H, maxFirst is Z, second is I, maxSecond is Z.
     */
    private record HintKeySequenceLayout(int first, int maxFirst, int second, int maxSecond,
                                         boolean oneOne, boolean threeOrFour, boolean oneTwo, boolean twoOne, boolean twoTwo) {

    }

    private FixedSizeHintGrid fixedSizeHintGrid(Rectangle areaRectangle,
                                                Point gridCenter, int maxRowCount,
                                                int maxColumnCount, double cellWidth,
                                                double cellHeight) {
        double hintMeshX, hintMeshY, hintMeshWidth, hintMeshHeight;
        int rowCount = Math.max(1, Math.min(maxRowCount,
                (int) ((double) areaRectangle.height() / cellHeight)));
        int columnCount = Math.max(1, Math.min(maxColumnCount,
                (int) ((double) areaRectangle.width() / cellWidth)));
        hintMeshWidth = columnCount * cellWidth;
        // If there is space left around the edges, and the max cell count (in one direction) is reached,
        // we want to increase the cell size only if the space left is smaller than
        // the user-defined max size of a cell. Otherwise, it is a 2-pass hint scenario,
        // and it means there is a lot of space left, and we do not want to fill it.
        // If the max cell count is not reached, we can either:
        // 1. increase the cell size (even if it becomes greater than the user-defined max size)
        // 2. or increase the cell count and decrease the size so that it fills the space
        // (Currently, we only do 1.)
        boolean maxColumnCountReached = columnCount == maxColumnCount;
        double spareWidth = areaRectangle.width() - hintMeshWidth;
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
        hintMeshHeight = rowCount * cellHeight;
        boolean maxRowCountReached = rowCount == maxRowCount;
        double spareHeight = areaRectangle.height() - hintMeshHeight;
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

    private record FixedSizeHintGrid(double hintMeshX, double hintMeshY, double hintMeshWidth,
                                     double hintMeshHeight, int rowCount, int columnCount,
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
                        hintMeshConfiguration.typeAndSelectionKeys(),
                        new HintMeshAndPreviousModeSelectedHintPoint(
                                hintMesh, previousHintMeshByTypeAndSelectionKeys.get(
                                hintMeshConfiguration.typeAndSelectionKeys()).previousModeSelectedHintPoint
                        ));
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
            lastSelectedHintPoint = new Point(Math.round(exactMatchHint.centerX()),
                    Math.round(exactMatchHint.centerY()));
            logger.trace("Saving lastSelectedHintPoint " + lastSelectedHintPoint);
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
                    hintMeshConfiguration.typeAndSelectionKeys(),
                    new HintMeshAndPreviousModeSelectedHintPoint(
                            hintMesh,
                            previousHintMeshByTypeAndSelectionKeys.get(
                                    hintMeshConfiguration.typeAndSelectionKeys()).previousModeSelectedHintPoint
                    ));
            WindowsOverlay.setHintMesh(hintMesh);
            return PressKeyEventProcessing.partOfHintPrefix();
        }
    }

    private void finalizeHintSelection(Hint hint) {
        HintMeshConfiguration hintMeshConfiguration = currentMode.hintMesh();
        if (hintMeshConfiguration.savePositionAfterSelection())
            savePosition(new Point(Math.round(hint.centerX()), Math.round(hint.centerY())));
        if (hintMeshConfiguration.modeAfterSelection() != null) {
            hintJustSelected = true;
            logger.trace("Hint " + hint.keySequence()
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
                    hintMeshConfiguration.typeAndSelectionKeys(),
                    new HintMeshAndPreviousModeSelectedHintPoint(
                            hintMesh, previousHintMeshByTypeAndSelectionKeys.get(
                            hintMeshConfiguration.typeAndSelectionKeys()).previousModeSelectedHintPoint
                    ));
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
        mouseController.moveTo((int) Math.round(point.x()), (int) Math.round(point.y()));
    }

    private void findPositionHistoryEntryMatchingCurrentPosition() {
        for (int positionIndex = 0;
             positionIndex < positionHistory.size(); positionIndex++) {
            Point point = positionHistory.get(positionIndex);
            if (Math.round(point.x()) == mouseX && Math.round(point.y()) == mouseY) {
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
        mouseController.moveTo((int) Math.round(point.x()), (int) Math.round(point.y()));
    }

}
