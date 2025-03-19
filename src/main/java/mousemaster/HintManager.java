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
    private ViewportFilter screenFilter;
    private Set<Key> selectionKeySubset;
    private final Map<HintMeshKey, HintMeshState> hintMeshStates = new HashMap<>();
    private boolean hintJustSelected = false;
    private int mouseX, mouseY;
    private Mode currentMode;
    private Zoom currentZoom;
    private final List<Point> positionHistory = new ArrayList<>();
    private final int maxPositionHistorySize;
    private Point lastSelectedHintPoint;
    /**
     * Used for deterministic hint key sequences.
     */
    private int positionIdCount = 0;
    private final Map<Point, Integer> idByPosition = new HashMap<>();
    private int positionCycleIndex = 0;

    /**
     * It would be better to have an instance of Zoom instead of ZoomConfiguration
     * (one ZoomConfiguration could lead to two different HintMeshes on two screens),
     * but #modeChanged needs to create a HintMeshKey to alter lastSelectedHintPoint
     * (this is arguably hacky, it is used for undoing the triple hint grid in the
     * author's configuration),
     * and that altered lastSelectedHintPoint is used to instantiate a Zoom object
     * from the ZoomConfiguration.
     */
    private record HintMeshKey(HintMeshType type, List<Key> selectionKeys,
                               ZoomConfiguration zoom) {

    }

    private record HintMeshState(HintMesh hintMesh,
                                 Point previousModeSelectedHintPoint) {

    }

    public HintManager(int maxPositionHistorySize, ScreenManager screenManager,
                       MouseController mouseController) {
        this.maxPositionHistorySize = maxPositionHistorySize;
        this.screenManager = screenManager;
        this.mouseController = mouseController;
    }

    public void setModeController(ModeController modeController) {
        this.modeController = modeController;
    }

    public Point lastSelectedHintPoint() {
        logger.trace("Zoom " + lastSelectedHintPoint);
        return lastSelectedHintPoint;
    }

    public void moveToLastSelectedHint() {
        if (lastSelectedHintPoint == null)
            return;
        mouseController.moveTo((int) lastSelectedHintPoint.x(),
                (int) lastSelectedHintPoint.y());
    }

    @Override
    public void mouseMoved(int x, int y) {
        if (mouseController.jumping())
            return;
        mouseX = x;
        mouseY = y;
    }

    @Override
    public void modeChanged(Mode newMode) {
        HintMeshConfiguration hintMeshConfiguration = newMode.hintMesh();
        List<Key> selectionKeys =
                hintMesh == null ? null : hintMeshConfiguration.keysByFilter()
                                                               .get(screenFilter)
                                                               .selectionKeys();
        if (hintJustSelected) {
            // When going from hint2-1 to hint2-2, even if we already have been in hint2-2
            // before, we don't want the old state of hint2-2.
            hintJustSelected = false;
            hintMeshStates.remove(
                    new HintMeshKey(hintMeshConfiguration.type(),
                            selectionKeys, newMode.zoom()));
        }
        else if (hintMeshConfiguration.type() instanceof HintMeshType.HintGrid hintGrid &&
                         hintGrid.area() instanceof ActiveScreenHintGridArea activeScreenHintGridArea &&
                         activeScreenHintGridArea.center() == ActiveScreenHintGridAreaCenter.LAST_SELECTED_HINT) {
            // When going back from hint3-3 to hint3-2, we find the selected hint of hint1 that led to hint3-2.
            // (Because currently, last selected hint is the hint selected by hint3-2.)
            HintMeshState
                    hintMeshState =
                    hintMeshStates.get(
                            new HintMeshKey(hintMeshConfiguration.type(),
                                    selectionKeys,
                                    newMode.zoom()));
            if (hintMeshState != null)
                lastSelectedHintPoint =
                        hintMeshState.previousModeSelectedHintPoint;
        }
        if (!hintMeshConfiguration.enabled()) {
            currentMode = newMode;
            hintMeshStates.clear();
            WindowsOverlay.hideHintMesh();
            return;
        }
        if (!hintMeshConfiguration.visible()) {
            // This makes the behavior of the hint different depending on whether it is visible.
            // An alternative would be a setting like hint.reset-focused-key-sequence-history-after-selection=true.
            hintMeshStates.clear();
            WindowsOverlay.hideHintMesh();
        }
        Point zoomCenterPoint = newMode.zoom().center().centerPoint(
                screenManager.activeScreen().rectangle(), mouseX, mouseY,
                lastSelectedHintPoint);
        Zoom newZoom = new Zoom(newMode.zoom().percent(),
                zoomCenterPoint, screenManager.screenContaining(zoomCenterPoint.x(),
                zoomCenterPoint.y()).rectangle());
        HintMeshAndScreenFilter
                newHintMeshAndScreenFilter = buildHintMesh(hintMeshConfiguration, newMode.zoom(), newZoom);
        HintMesh newHintMesh = newHintMeshAndScreenFilter.hintMesh;
        ViewportFilter newScreenFilter = newHintMeshAndScreenFilter.screenFilter;
        if (currentMode != null && newMode.hintMesh().equals(currentMode.hintMesh()) &&
            newHintMesh.equals(hintMesh))
            return;
        selectionKeySubset = newHintMesh.hints()
                                        .stream()
                                        .map(Hint::keySequence)
                                        .flatMap(Collection::stream)
                                        .collect(Collectors.toSet());
        currentMode = newMode;
        currentZoom = newZoom;
        List<Key> newSelectionKeys =
                hintMeshConfiguration.keysByFilter().get(newScreenFilter).selectionKeys();
        hintMeshStates.put(new HintMeshKey(hintMeshConfiguration.type(),
                        newSelectionKeys,
                        newMode.zoom()),
                new HintMeshState(newHintMesh, lastSelectedHintPoint));
        hintMesh = newHintMesh;
        screenFilter = newScreenFilter;
        WindowsOverlay.setHintMesh(hintMesh);
    }

    private record HintMeshAndScreenFilter(HintMesh hintMesh,
                                           ViewportFilter screenFilter) {
    }

    private HintMeshAndScreenFilter buildHintMesh(
            HintMeshConfiguration hintMeshConfiguration,
            ZoomConfiguration zoomConfiguration, Zoom zoom) {
        HintMeshBuilder hintMesh = new HintMeshBuilder();
        hintMesh.visible(hintMeshConfiguration.visible())
                .styleByFilter(hintMeshConfiguration.styleByFilter());
        HintMeshType type = hintMeshConfiguration.type();
        ViewportFilter screenFilter;
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
                screenFilter = ViewportFilter.of(gridScreen);
                HintGridLayout gridLayout = hintGrid.layout(screenFilter);
                FixedSizeHintGrid fixedSizeHintGrid = fixedSizeHintGrid(
                        screenManager.activeScreen().rectangle(), gridCenter,
                        gridLayout.maxRowCount(),
                        gridLayout.maxColumnCount(),
                        gridLayout.cellWidth() * screenManager.activeScreen().scale(),
                        gridLayout.cellHeight() * screenManager.activeScreen().scale());
                fixedSizeHintGrids.add(fixedSizeHintGrid);
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
                ViewportFilter firstScreenFilter = null;
                for (Screen screen : sortedScreens) {
                    Point gridCenter = screen.rectangle().center();
                    HintGridLayout gridLayout = hintGrid.layout(
                            ViewportFilter.of(screenManager.activeScreen()));
                    FixedSizeHintGrid fixedSizeHintGrid =
                            fixedSizeHintGrid(screen.rectangle(),
                                    gridCenter, gridLayout.maxRowCount(),
                                    gridLayout.maxColumnCount(),
                                    gridLayout.cellWidth() * screen.scale(),
                                    gridLayout.cellHeight() * screen.scale());
                    fixedSizeHintGrids.add(fixedSizeHintGrid);
                    if (firstScreenFilter == null) {
                        firstScreenFilter = ViewportFilter.of(screen);
                    }
                }
                screenFilter = firstScreenFilter;
            }
            else if (hintGrid.area() instanceof ActiveWindowHintGridArea activeWindowHintGridArea) {
                Rectangle activeWindowRectangle =
                        WindowsOverlay.activeWindowRectangle(1, 1, 0, 0, 0, 0);
                Point gridCenter = activeWindowRectangle.center();
                Screen screen =
                        screenManager.screenContaining(gridCenter.x(), gridCenter.y());
                screenFilter = ViewportFilter.of(screenManager.activeScreen());
                HintGridLayout gridLayout = hintGrid.layout(screenFilter);
                FixedSizeHintGrid fixedSizeHintGrid =
                        fixedSizeHintGrid(activeWindowRectangle, gridCenter,
                                gridLayout.maxRowCount(), gridLayout.maxColumnCount(),
                                gridLayout.cellWidth() * screen.scale(),
                                gridLayout.cellHeight() * screen.scale());
                fixedSizeHintGrids.add(fixedSizeHintGrid);
            }
            else
                throw new IllegalStateException();
            int hintCountSum = fixedSizeHintGrids.stream()
                                                 .mapToInt(FixedSizeHintGrid::hintCount)
                                                 .sum();
            HintGridLayout firstScreenGridLayout = hintGrid.layout(screenFilter);
            FixedSizeHintGrid firstScreen = fixedSizeHintGrids.getFirst();
            int layoutRowCount = Math.min(firstScreen.rowCount(),
                    firstScreenGridLayout.layoutRowCount());
            int layoutColumnCount = Math.min(firstScreen.columnCount(),
                    firstScreenGridLayout.layoutColumnCount());
            boolean layoutRowOriented = firstScreenGridLayout.layoutRowOriented();
            int subgridCount = fixedSizeHintGrids.stream()
                                                 .mapToInt(
                                                         fixedSizeHintGrid -> fixedSizeHintGrid.subgridCount(
                                                                 layoutRowCount,
                                                                 layoutColumnCount))
                                                 .sum();
            HintMeshKeys hintMeshKeys =
                    hintMeshConfiguration.keysByFilter().get(screenFilter);
            List<Key> selectionKeys = hintMeshKeys.selectionKeys();
            List<Hint> hints = new ArrayList<>();
            int beginSubgridIndex = 0;
            for (FixedSizeHintGrid fixedSizeHintGrid : fixedSizeHintGrids) {
                int beginHintIndex = hints.size();
                hints.addAll(buildHints(fixedSizeHintGrid,
                        selectionKeys,
                        hintCountSum,
                        beginSubgridIndex, subgridCount,
                        beginHintIndex,
                        layoutRowCount,
                        layoutColumnCount, layoutRowOriented,
                        zoom));
                beginSubgridIndex +=
                        fixedSizeHintGrid.subgridCount(layoutRowCount, layoutColumnCount);
            }
            hintMesh.hints(hints);
        }
        else {
            if (positionHistory.isEmpty())
                saveCurrentPosition();
            int hintCount = positionHistory.size();
            List<Hint> hints = new ArrayList<>(hintCount);
            Screen firstHintScreen =
                    screenManager.screenContaining(positionHistory.getFirst().x(),
                            positionHistory.getFirst().y());
            screenFilter = ViewportFilter.of(firstHintScreen);
            HintMeshKeys hintMeshKeys =
                    hintMeshConfiguration.keysByFilter().get(screenFilter);
            List<Key> selectionKeys = hintMeshKeys.selectionKeys();
            for (Point point : positionHistory) {
                List<Key> keySequence = hintKeySequence(
                        selectionKeys,
                        hintCount,
                        0, -1, idByPosition.get(point) % maxPositionHistorySize,
                        -1, -1,
                        -1, -1,
                        -1, -1, false);
                Zoom zoom1 = new Zoom(1, zoom.center(), zoom.screenRectangle());
                if (zoom1.screenRectangle().contains(point.x(), point.y()))
                    hints.add(new Hint(zoom1.zoomedX(point.x()), zoom1.zoomedY(point.y()),
                            -1, -1, keySequence));
                else
                    hints.add(new Hint(point.x(), point.y(), -1, -1, keySequence));
            }
            hintMesh.hints(hints);
        }
        HintMeshState previousHintMeshState = hintMeshStates.get(
                new HintMeshKey(hintMeshConfiguration.type(),
                        hintMeshConfiguration.keysByFilter()
                                             .get(screenFilter)
                                             .selectionKeys(),
                        zoomConfiguration));
        if (previousHintMeshState != null &&
            previousHintMeshState.hintMesh.hints()
                                          .equals(hintMesh.hints())) {
            // Keep the old focusedKeySequence.
            // This is useful for hint-then-click-mode that extends hint-mode.
            hintMesh.focusedKeySequence(
                    previousHintMeshState.hintMesh.focusedKeySequence());
        }
        return new HintMeshAndScreenFilter(hintMesh.build(), screenFilter);
    }

    private static List<Hint> buildHints(FixedSizeHintGrid fixedSizeHintGrid,
                                         List<Key> selectionKeys, int hintCount,
                                         int beginSubgridIndex, int subgridCount,
                                         int beginHintIndex, int layoutRowCount,
                                         int layoutColumnCount, boolean layoutRowOriented,
                                         Zoom zoom) {
        int rowCount = fixedSizeHintGrid.rowCount();
        int columnCount = fixedSizeHintGrid.columnCount();
        Zoom zoom1 = new Zoom(1, zoom.center(), zoom.screenRectangle());
        double hintMeshX;
        double hintMeshY;
        if (zoom1.screenRectangle()
                 .contains(fixedSizeHintGrid.hintMeshX() +
                           fixedSizeHintGrid.hintMeshWidth / 2,
                         fixedSizeHintGrid.hintMeshY() +
                         fixedSizeHintGrid.hintMeshHeight / 2)) {
            hintMeshX = zoom1.zoomedX(fixedSizeHintGrid.hintMeshX());
            hintMeshY = zoom1.zoomedY(fixedSizeHintGrid.hintMeshY());
        }
        else {
            hintMeshX = fixedSizeHintGrid.hintMeshX();
            hintMeshY = fixedSizeHintGrid.hintMeshY();
        }
        double cellWidth = fixedSizeHintGrid.cellWidth;
        double cellHeight = fixedSizeHintGrid.cellHeight;
        int gridHintCount = rowCount * columnCount;
        List<Hint> hints = new ArrayList<>(gridHintCount);
        int hintIndex = beginHintIndex;
        double rowHeightOffset = 0;
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            double columnWidthOffset = 0;
            for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                List<Key> keySequence = hintKeySequence(selectionKeys, hintCount,
                        beginSubgridIndex, subgridCount,
                        hintIndex,
                        rowIndex, columnIndex,
                        rowCount, columnCount,
                        layoutRowCount, layoutColumnCount, layoutRowOriented);
                double hintCenterX = hintMeshX + columnWidthOffset + cellWidth / 2d;
                double hintCenterY = hintMeshY + rowHeightOffset + cellHeight / 2d;
                hints.add(new Hint(hintCenterX, hintCenterY, cellWidth, cellHeight,
                        keySequence));
                hintIndex++;
                columnWidthOffset += cellWidth;
            }
            rowHeightOffset += cellHeight;
        }
        return hints;
    }

    /**
     * If columnCount * cellWidth is 1898, spread the 1920 - 1898 = 22 pixels across the cells.
     */
    public static boolean[] distributeTrueUniformly(int arraySize, int trueCount) {
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
                                             int beginSubgridIndex, int subgridCount,
                                             int hintIndex,
                                             int rowIndex, int columnIndex,
                                             int rowCount, int columnCount,
                                             int layoutRowCount, int layoutColumnCount,
                                             boolean layoutRowOriented) {
        int bigColumnCount = (int) Math.ceil((double) columnCount / layoutColumnCount);
        // Number of sub grids in a column.
        int bigRowCount = (int) Math.ceil((double) rowCount / layoutRowCount);
        int keyCount = keys.size();
        if (rowIndex != -1) {
            if (hintCount <= keyCount) {
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
            // With subgrids (here layoutRowCount = 6 and layoutColumnCount = 5):
            // qq qw qe qr qt wq ww we wr wt ... tq tw te tr tt
            // qa qs qd qf qg wa ws wd wf wg ... ta ts td tf tg
            // ...
            // yq yw ye yr yt ... pq pw pe pr pt
            // ...
            // yn ym y, y. y/ ... pn pm p, p. p/
            HintKeySequenceLayout layout =
                    hintKeySequenceLayout(layoutRowOriented, keyCount,
                            columnIndex, rowIndex,
                            bigColumnCount, bigRowCount,
                            layoutColumnCount, layoutRowCount, beginSubgridIndex,
                            subgridCount, hintCount);
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

    /**
     * columnIndex is the index of the column in the current FixedSizeHintGrid.
     * columnCount is the number of columns in the current FixedSizeHintGrid.
     * bigColumnCount is the number of big columns in the current FixedSizeHintGrid.
     */
    private static HintKeySequenceLayout hintKeySequenceLayout(boolean layoutRowOriented, int keyCount,
                                                               int columnIndex, int rowIndex,
                                                               int bigColumnCount, int bigRowCount,
                                                               int layoutColumnCount, int layoutRowCount,
                                                               int beginSubgridIndex,
                                                               int subgridCount, int hintCount) {
        int first;
        int maxFirst;
        int second;
        int maxSecond;
        if (layoutRowOriented) {
            columnIndex += beginSubgridIndex * layoutColumnCount
                           + rowIndex / layoutRowCount * (bigColumnCount * layoutColumnCount);
            int columnCount = subgridCount * layoutColumnCount;

            first = columnIndex / layoutColumnCount;
            maxFirst = (columnCount - 1) / layoutColumnCount;
            second = columnIndex % layoutColumnCount + rowIndex % layoutRowCount * layoutColumnCount;
            maxSecond = layoutColumnCount - 1 + (layoutRowCount - 1) * layoutColumnCount;
        }
        else {
            rowIndex += beginSubgridIndex * layoutRowCount
                        + columnIndex / layoutColumnCount * (bigRowCount * layoutRowCount);
            int rowCount = subgridCount * layoutRowCount;

            first = rowIndex / layoutRowCount;
            maxFirst = (rowCount - 1) / layoutRowCount;
            second = rowIndex % layoutRowCount + columnIndex % layoutColumnCount * layoutRowCount;
            maxSecond = layoutRowCount - 1 + (layoutColumnCount - 1) * layoutRowCount;
        }
        boolean oneOne = maxFirst <= keyCount - 1 && maxSecond <= keyCount - 1;
        boolean threeOrFour = !oneOne && hintCount >= 100 &&
                              maxFirst <= Math.pow(keyCount, 2) - 1 &&
                              maxSecond <= Math.pow(keyCount, 2) - 1;
        // Length 3 if rowCount or columnCount <= keyCount.
        boolean oneTwo = threeOrFour && maxFirst <= keyCount - 1;
        boolean twoOne = threeOrFour && maxSecond <= keyCount - 1;
        // We don't do length 4 if keyCount too large because the hints would
        // always start with A or B.
        boolean twoTwo = threeOrFour && keyCount <= 6;

        return new HintKeySequenceLayout(first, maxFirst, second, maxSecond, oneOne,
                threeOrFour, oneTwo, twoOne, twoTwo);
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

        public int hintCount() {
            return rowCount * columnCount;
        }

        public int bigColumnCount(int layoutColumnCount) {
            return (int) Math.ceil((double) columnCount / layoutColumnCount);
        }

        public int bigRowCount(int layoutRowCount) {
            return (int) Math.ceil((double) rowCount / layoutRowCount);
        }

        public int subgridCount(int layoutRowCount, int layoutColumnCount) {
            return bigRowCount(layoutRowCount) * bigColumnCount(layoutColumnCount);
        }

    }

    @Override
    public void modeTimedOut() {
        // No op.
    }

    public PressKeyEventProcessing keyPressed(Key key) {
        HintMeshConfiguration hintMeshConfiguration = currentMode.hintMesh();
        if (!hintMeshConfiguration.enabled())
            return PressKeyEventProcessing.unhandled();
        HintMeshKeys hintMeshKeys = hintMeshConfiguration.keysByFilter()
                                                         .get(screenFilter);
        if (hintMeshKeys.undoKeys().contains(key)) {
            List<Key> focusedKeySequence = hintMesh.focusedKeySequence();
            if (!focusedKeySequence.isEmpty()) {
                hintMesh = hintMesh.builder()
                                   .focusedKeySequence(focusedKeySequence.subList(0,
                                           focusedKeySequence.size() - 1))
                                   .build();
                HintMeshKey hintMeshKey =
                        new HintMeshKey(hintMeshConfiguration.type(),
                                hintMeshKeys.selectionKeys(),
                                currentMode.zoom());
                hintMeshStates.put(
                        hintMeshKey,
                        new HintMeshState(
                                hintMesh, hintMeshStates.get(
                                hintMeshKey).previousModeSelectedHintPoint
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
            boolean hintIsInZoom = currentZoom.screenRectangle()
                                          .contains(exactMatchHint.centerX(),
                                                  exactMatchHint.centerY());
            if (hintIsInZoom) {
                lastSelectedHintPoint =
                        new Point(Math.round(currentZoom.unzoomedX(exactMatchHint.centerX())),
                                Math.round(currentZoom.unzoomedY(exactMatchHint.centerY())));
            }
            else {
                lastSelectedHintPoint =
                        new Point(Math.round(exactMatchHint.centerX()),
                                Math.round(exactMatchHint.centerY()));
            }
            logger.trace("Saving lastSelectedHintPoint " + lastSelectedHintPoint);
             if (hintMeshConfiguration.moveMouse()) {
                 // After this moveTo call, the move is not fully completed.
                 // We need to wait until the jump completes before a click can be performed at
                 // the new position.
                 if (hintIsInZoom) {
                     mouseX = (int) Math.round(
                             currentZoom.unzoomedX(exactMatchHint.centerX()));
                     mouseY = (int) Math.round(
                             currentZoom.unzoomedY(exactMatchHint.centerY()));
                 }
                 else {
                     mouseX = (int) Math.round(exactMatchHint.centerX());
                     mouseY = (int) Math.round(exactMatchHint.centerY());
                 }
                 mouseController.moveTo(mouseX, mouseY);
             }
            finalizeHintSelection(exactMatchHint);
            return hintMeshConfiguration.swallowHintEndKeyPress() ?
                    PressKeyEventProcessing.swallowedHintEnd() :
                    PressKeyEventProcessing.unswallowedHintEnd();
        }
        else {
            hintMesh =
                    hintMesh.builder().focusedKeySequence(newFocusedKeySequence).build();
            HintMeshKey hintMeshKey =
                    new HintMeshKey(hintMeshConfiguration.type(),
                            hintMeshKeys.selectionKeys(), currentMode.zoom());
            hintMeshStates.put(
                    hintMeshKey,
                    new HintMeshState(
                            hintMesh,
                            hintMeshStates.get(hintMeshKey).previousModeSelectedHintPoint
                    ));
            WindowsOverlay.setHintMesh(hintMesh);
            return PressKeyEventProcessing.partOfHintPrefix();
        }
    }

    private void finalizeHintSelection(Hint hint) {
        HintMeshConfiguration hintMeshConfiguration = currentMode.hintMesh();
        if (hintMeshConfiguration.savePositionAfterSelection()) {
            if (currentZoom.screenRectangle().contains(hint.centerX(), hint.centerY())) {
                savePosition(new Point(Math.round(currentZoom.unzoomedX(hint.centerX())),
                        Math.round(currentZoom.unzoomedY(hint.centerY()))));
            }
            else {
                savePosition(new Point(Math.round(hint.centerX()),
                        Math.round(hint.centerY())));
            }
        }
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
            HintMeshKey hintMeshKey =
                    new HintMeshKey(hintMeshConfiguration.type(),
                            hintMeshConfiguration.keysByFilter()
                                                 .get(screenFilter)
                                                 .selectionKeys(), currentMode.zoom());
            hintMeshStates.put(
                    hintMeshKey,
                    new HintMeshState(
                            hintMesh,
                            hintMeshStates.get(hintMeshKey).previousModeSelectedHintPoint
                    ));
            WindowsOverlay.setHintMesh(hintMesh);
        }
    }

    public void saveCurrentPosition() {
        savePosition(new Point(mouseX, mouseY));
    }

    public void unsaveCurrentPosition() {
        Point currentPosition = new Point(mouseX, mouseY);
        if (positionHistory.remove(currentPosition)) {
            int currentPositionId = idByPosition.remove(currentPosition);
            Map<Point, Integer> newIdByPosition = new HashMap<>();
            for (Map.Entry<Point, Integer> entry : idByPosition.entrySet()) {
                int id = entry.getValue();
                newIdByPosition.put(entry.getKey(), id < currentPositionId ? id : id - 1);
            }
            idByPosition.clear();
            idByPosition.putAll(newIdByPosition);
            positionIdCount--;
            positionCycleIndex = positionHistory.size() - 1;
        }
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
