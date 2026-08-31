package mousemaster;

import mousemaster.HintMesh.HintMeshBuilder;
import mousemaster.PositionHistoryIsolationKey.ActiveAppPositionHistoryIsolationKey;
import mousemaster.PositionHistoryIsolationKey.NonePositionHistoryIsolationKey;
import mousemaster.platform.ActiveAppFinder;
import mousemaster.platform.Overlay;
import mousemaster.platform.UiAutomation;
import mousemaster.platform.UiAutomation.UiElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class HintManager implements ModeListener, MousePositionListener {

    private static final Logger logger = LoggerFactory.getLogger(HintManager.class);

    private final ScreenManager screenManager;
    private final MouseManager mouseManager;
    private final Overlay overlay;
    private final UiAutomation uiAutomation;
    private final ActiveAppFinder activeAppFinder;
    private final KeyRedactor keyRedactor;
    private final Vision vision;
    private ModeController modeController;
    private HintMesh hintMesh;
    private ScreenFilter screenFilter;
    private final Map<HintMeshKey, HintMeshState> hintMeshStates = new HashMap<>();
    private boolean hintJustSelected = false;
    private int mouseX, mouseY;
    private Mode currentMode;
    private Zoom currentZoom;
    private PositionHistory currentPositionHistory;
    private final Map<String, PositionHistoryConfiguration> positionHistoryConfigurationByName;
    private final Map<PositionHistoryKey, PositionHistory> positionHistoryByKey =
            new HashMap<>();
    private Point lastSelectedHintPoint;
    private Rectangle lastSelectedHintCell;
    // One level per last-selected-hint-cell drill-down step. The area is frozen at push
    // time so going back restores the view that step rendered.
    private final Deque<CellGridLevel> cellGridLevelStack = new ArrayDeque<>();
    private Rectangle pendingSelectedCell;

    private record CellGridLevel(String modeName, Rectangle cell, Rectangle area) {
    }

    private boolean lastHintCommandSupercedesOtherCommands;

    private record PendingUiHintQuery(
            Future<List<UiElement>> future,
            HintMeshConfiguration hintMeshConfiguration,
            Zoom zoom,
            ScreenFilter screenFilter) {
    }

    private PendingUiHintQuery pendingUiHintQuery;
    private List<UiElement> lastUiElements = List.of();

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

    public HintManager(
            Map<String, PositionHistoryConfiguration> positionHistoryConfigurationByName,
            ScreenManager screenManager,
            MouseManager mouseManager, Overlay overlay,
            UiAutomation uiAutomation, ActiveAppFinder activeAppFinder,
            KeyRedactor keyRedactor, Vision vision) {
        this.positionHistoryConfigurationByName = positionHistoryConfigurationByName;
        this.screenManager = screenManager;
        this.mouseManager = mouseManager;
        this.overlay = overlay;
        this.uiAutomation = uiAutomation;
        this.activeAppFinder = activeAppFinder;
        this.keyRedactor = keyRedactor;
        this.vision = vision;
    }

    public void setModeController(ModeController modeController) {
        this.modeController = modeController;
    }

    private static final int maxHintMeshVariantBranchCount = 12;
    private static final int minPreWarmedHintCount = 100;
    private static final int preWarmedUiElementCount = 500;

    /** Builds and caches the meshes that are slow to build. */
    public void preWarmHintMeshes(ModeMap modeMap) {
        long before = System.nanoTime();
        Set<HintMesh> warmed = new HashSet<>();
        for (Mode mode : modeMap.modes()) {
            if (!isPreWarmedHintMesh(mode.hintMesh()))
                continue;
            for (Screen screen : screenManager.screens()) {
                Rectangle screenRectangle = screen.rectangle();
                Zoom zoom = new Zoom(mode.zoom().percent(null, screenRectangle),
                        screenRectangle.center(), screenRectangle);
                for (HintMeshConfiguration configuration : hintMeshVariants(mode)) {
                    if (configuration.type() instanceof HintMeshType.PositionHistoryHintMesh)
                        continue;
                    HintMesh hintMesh = buildHintMesh(configuration, mode.zoom(), zoom,
                            ScreenFilter.of(screen), preWarmedUiElements(screen), screen);
                    if (!hintMesh.visible() ||
                        hintMesh.hints().size() < minPreWarmedHintCount)
                        continue;
                    if (warmed.add(hintMesh))
                        overlay.preWarmHintMesh(hintMesh, zoom);
                }
            }
        }
        if (!warmed.isEmpty())
            logger.debug("Pre-warmed " + warmed.size() + " hint meshes in " +
                         (long) ((System.nanoTime() - before) / 1e6) + "ms");
    }

    /** The mode's hint mesh configuration and every distinct one its precondition-only
     *  mutations produce. */
    private static List<HintMeshConfiguration> hintMeshVariants(Mode mode) {
        List<Combo> mutatingCombos = new ArrayList<>();
        Set<String> variableNames = new TreeSet<>();
        List<ComboPrecondition.ComboKeyPrecondition> keyPreconditions = new ArrayList<>();
        for (var entry : mode.comboMap().commandsByCombo().entrySet()) {
            Combo combo = entry.getKey();
            if (!combo.sequence().isEmpty())
                continue;
            for (Command command : entry.getValue()) {
                if (!(command instanceof Command.MutateMode mutateMode) ||
                    !mutateMode.propertyPath().fieldNames().getFirst().equals("hintMesh"))
                    continue;
                if (combo.precondition().keyPrecondition().pressedKeyPrecondition().allKeys()
                         .contains(Os.macos ? BuiltInVirtualKey.IS_WINDOWS :
                                 BuiltInVirtualKey.IS_MACOS))
                    break;
                mutatingCombos.add(combo);
                combo.precondition().variablePrecondition().conditions()
                     .forEach(condition -> variableNames.add(condition.variableName()));
                ComboPrecondition.ComboKeyPrecondition keyPrecondition =
                        combo.precondition().keyPrecondition();
                if (!keyPrecondition.isEmpty() && !keyPreconditions.contains(keyPrecondition))
                    keyPreconditions.add(keyPrecondition);
                break;
            }
        }
        List<String> variables = new ArrayList<>(variableNames);
        int branchCount = variables.size() + keyPreconditions.size();
        if (branchCount == 0)
            return List.of(mode.hintMesh());
        if (branchCount > maxHintMeshVariantBranchCount) {
            logger.info("Not pre-warming the mutated hint meshes of " + mode.name() + ": " +
                        branchCount + " branches drive them");
            return List.of(mode.hintMesh());
        }
        Set<HintMeshConfiguration> variants = new LinkedHashSet<>();
        variants.add(mode.hintMesh());
        for (int branchBits = 0; branchBits < (1 << branchCount); branchBits++) {
            Set<String> activeVariables = new HashSet<>();
            for (int i = 0; i < variables.size(); i++)
                if ((branchBits & (1 << i)) != 0)
                    activeVariables.add(variables.get(i));
            Set<ComboPrecondition.ComboKeyPrecondition> satisfiedKeyPreconditions = new HashSet<>();
            for (int i = 0; i < keyPreconditions.size(); i++)
                if ((branchBits & (1 << (variables.size() + i))) != 0)
                    satisfiedKeyPreconditions.add(keyPreconditions.get(i));
            Mode mutatedMode = mode;
            for (Combo combo : mutatingCombos) {
                if (!combo.precondition().variablePrecondition().satisfiedBy(activeVariables))
                    continue;
                ComboPrecondition.ComboKeyPrecondition keyPrecondition =
                        combo.precondition().keyPrecondition();
                if (!keyPrecondition.isEmpty() &&
                    !satisfiedKeyPreconditions.contains(keyPrecondition))
                    continue;
                for (Command command : mode.comboMap().commandsByCombo().get(combo))
                    if (command instanceof Command.MutateMode mutateMode &&
                        mutateMode.propertyPath().fieldNames().getFirst().equals("hintMesh"))
                        mutatedMode = mutatedMode.mutate(mutateMode.propertyPath(),
                                mutateMode.newPropertyValue());
            }
            variants.add(mutatedMode.hintMesh());
        }
        return List.copyOf(variants);
    }

    private static List<UiElement> preWarmedUiElements(Screen screen) {
        Rectangle rectangle = screen.rectangle();
        int columns = (int) Math.ceil(Math.sqrt(preWarmedUiElementCount));
        int rows = (preWarmedUiElementCount + columns - 1) / columns;
        List<UiElement> uiElements = new ArrayList<>(preWarmedUiElementCount);
        for (int i = 0; i < preWarmedUiElementCount; i++)
            uiElements.add(new UiElement(
                    rectangle.x() + rectangle.width() * (i % columns + 0.5) / columns,
                    rectangle.y() + rectangle.height() * (i / columns + 0.5) / rows));
        return uiElements;
    }

    private static boolean isPreWarmedHintMesh(HintMeshConfiguration configuration) {
        if (!configuration.enabled() || !configuration.visible())
            return false;
        if (configuration.type() instanceof HintMeshType.UiAccessibilityHintMesh
            || configuration.type() instanceof HintMeshType.UiVisionHintMesh)
            return true;
        if (!(configuration.type() instanceof HintMeshType.GridHintMesh gridHintMesh))
            return false;
        HintGridAreaSizeSource source = gridHintMesh.area().size().source();
        return (source == HintGridAreaSizeSource.ACTIVE_SCREEN ||
                source == HintGridAreaSizeSource.ALL_SCREENS) &&
               gridHintMesh.area().center() == HintGridAreaCenter.SCREEN_CENTER;
    }

    public HintMesh hintMesh() {
        return hintMesh;
    }

    public Point lastSelectedHintPoint() {
        logger.trace("Zoom " + lastSelectedHintPoint);
        return lastSelectedHintPoint;
    }

    public Rectangle lastSelectedHintCell() {
        return lastSelectedHintCell;
    }

    public void moveToLastSelectedHint() {
        if (lastSelectedHintPoint == null)
            return;
        mouseManager.moveTo((int) lastSelectedHintPoint.x(),
                (int) lastSelectedHintPoint.y());
    }

    @Override
    public void mouseMoved(int x, int y) {
        if (mouseManager.jumping())
            return;
        mouseX = x;
        mouseY = y;
    }

    @Override
    public void modeChanged(Mode newMode) {
        boolean hintWasJustSelected = hintJustSelected;
        boolean sameMode =
                currentMode != null && newMode.name().equals(currentMode.name());
        HintMeshConfiguration hintMeshConfiguration = newMode.hintMesh();
        boolean sameUiHintArea = hintMeshConfiguration.enabled() &&
                                 currentMode != null &&
                                 sameUiHintArea(hintMeshConfiguration.type(),
                                         currentMode.hintMesh().type());
        if (pendingUiHintQuery != null && !sameUiHintArea) {
            pendingUiHintQuery.future().cancel(false);
            pendingUiHintQuery = null;
        }
        if (hintMeshConfiguration.type() instanceof
                HintMeshType.PositionHistoryHintMesh positionHistoryHintMesh) {
            currentPositionHistory =
                    positionHistory(positionHistoryHintMesh.positionHistoryName());
            if (currentPositionHistory.positions().isEmpty())
                currentPositionHistory.save(new Point(mouseX, mouseY));
        }
        ScreenFilter newScreenFilter = screenFilter(hintMeshConfiguration);
        List<Key> selectionKeys =
                hintMeshConfiguration.keysByFilter()
                                     .get(newScreenFilter)
                                     .selectionKeys();
        if (hintJustSelected) {
            if (sameMode &&
                !(hintMeshConfiguration.type() instanceof HintMeshType.PositionHistoryHintMesh)) {
                // Same-mode mutation (e.g., variable change triggering
                // refreshPreconditionOnlyMutations before SwitchMode runs).
                // Skip rebuilding: the grid would get a new center (because
                // lastSelectedHintPoint moved) making hints differ from the
                // stored state, which resets selectedKeySequence to [].
                // If we wanted to do the rebuilding, we should probably have a hint.clear-selection command
                // (similar to hint.undo which could be renamed to undo-selection).
                return;
            }
             // When going from hint2-1 to hint2-2, even if we already have been in hint2-2
            // before, we don't want the old state of hint2-2.
            hintJustSelected = false;
            hintMeshStates.remove(
                    new HintMeshKey(hintMeshConfiguration.type(),
                            selectionKeys, newMode.zoom()));
        }
        else if (hintMeshConfiguration.type() instanceof HintMeshType.GridHintMesh gridHintMesh &&
                         gridHintMesh.area().size().source() == HintGridAreaSizeSource.ACTIVE_SCREEN &&
                         gridHintMesh.area().center() == HintGridAreaCenter.LAST_SELECTED_HINT) {
            // When going back from hint3-3 to hint3-2, we find the selected hint of hint1 that led to hint3-2.
            // (Because currently, last selected hint is the hint selected by hint3-2.)
            // Skip for same-mode mutations (e.g. zoom toggle): lastSelectedHintPoint
            // is already correct, and a stale cache entry (from a previous visit with
            // different variable state) could overwrite it with an outdated value.
            if (!sameMode) {
                HintMeshState hintMeshState = hintMeshStates.get(
                        new HintMeshKey(hintMeshConfiguration.type(),
                                selectionKeys, newMode.zoom()));
                if (hintMeshState != null)
                    lastSelectedHintPoint =
                            hintMeshState.previousModeSelectedHintPoint;
            }
        }
        // Selecting drills one step deeper, going back drops the steps above, staying on a
        // step recomputes its area, leaving the recursion clears the stack.
        if (hintMeshConfiguration.enabled() &&
            hintMeshConfiguration.type() instanceof HintMeshType.GridHintMesh cellAreaGrid &&
            cellAreaGrid.area().size().source() == HintGridAreaSizeSource.LAST_SELECTED_HINT_CELL) {
            HintGridAreaSize size = cellAreaGrid.area().size();
            HintGridAreaCenter center = cellAreaGrid.area().center();
            if (hintWasJustSelected) {
                if (pendingSelectedCell != null)
                    cellGridLevelStack.push(
                            new CellGridLevel(newMode.name(), pendingSelectedCell,
                                    cellGridArea(pendingSelectedCell, size, center)));
            }
            else if (popLevelsAbove(newMode.name())) {
                // The anchors still point at the level left behind, which is one deeper.
                Rectangle cell = cellGridLevelStack.peek().cell();
                lastSelectedHintCell = cell;
                lastSelectedHintPoint = cell.center();
            }
            else
                recomputeTopLevelArea(size, center);
        }
        else if (!sameMode) {
            cellGridLevelStack.clear();
            // No level left for a zoom anchored on the selection to take its depth from.
            lastSelectedHintCell = null;
        }
        if (!hintMeshConfiguration.enabled()) {
            currentMode = newMode;
            hintMeshStates.clear();
            hintMesh = null;
            overlay.hideHintMesh();
            return;
        }
        if (!hintMeshConfiguration.visible()) {
            // This makes the behavior of the hint different depending on whether it is visible.
            // An alternative would be a setting like hint.reset-selected-key-sequence-history-after-selection=true.
            hintMeshStates.clear();
            hintMesh = null;
            overlay.hideHintMesh();
        }
        Point zoomCenterPoint = newMode.zoom().center().centerPoint(
                screenManager.activeScreen().rectangle(), mouseX, mouseY,
                lastSelectedHintPoint);
        Rectangle zoomScreen = screenManager.nearestScreenContaining(zoomCenterPoint.x(),
                zoomCenterPoint.y()).rectangle();
        Zoom newZoom = new Zoom(newMode.zoom().percent(lastSelectedHintCell, zoomScreen),
                zoomCenterPoint, zoomScreen);
        HintMesh newHintMesh;
        if (hintMeshConfiguration.type() instanceof HintMeshType.UiAccessibilityHintMesh ||
            hintMeshConfiguration.type() instanceof HintMeshType.UiVisionHintMesh) {
            // Do not recompute the elements when switching between two hint modes that
            // look for them in the same area.
            if (!sameUiHintArea) {
                pendingUiHintQuery = new PendingUiHintQuery(
                        startUiElementQuery(hintMeshConfiguration.type()),
                        hintMeshConfiguration, newZoom, newScreenFilter);
                currentMode = newMode;
                currentZoom = newZoom;
                screenFilter = newScreenFilter;
                return;
            }
            else if (pendingUiHintQuery != null) {
                currentMode = newMode;
                currentZoom = newZoom;
                screenFilter = newScreenFilter;
                // Future unchanged.
                pendingUiHintQuery = new PendingUiHintQuery(pendingUiHintQuery.future,
                        hintMeshConfiguration, newZoom, newScreenFilter);
                // completePendingUiHintQuery() will use the new mode's configuration/zoom/screenFilter.
                return;
            }
            else {
                newHintMesh = buildHintMesh(hintMeshConfiguration, newMode.zoom(), newZoom,
                        newScreenFilter, lastUiElements, screenManager.activeScreen());
            }
        }
        else {
            newHintMesh = buildHintMesh(hintMeshConfiguration, newMode.zoom(), newZoom,
                    newScreenFilter, null, screenManager.activeScreen());
        }
        if (currentMode != null && newMode.hintMesh().equals(currentMode.hintMesh()) &&
            newHintMesh.equals(hintMesh))
            return;
        currentMode = newMode;
        currentZoom = newZoom;
        screenFilter = newScreenFilter;
        activateHintMesh(newMode, newHintMesh, hintMeshConfiguration, newScreenFilter, newZoom);
    }

    private void activateHintMesh(Mode newMode, HintMesh newHintMesh,
                                  HintMeshConfiguration hintMeshConfiguration,
                                  ScreenFilter newScreenFilter, Zoom newZoom) {
        List<Key> newSelectionKeys =
                hintMeshConfiguration.keysByFilter().get(newScreenFilter).selectionKeys();
        hintMeshStates.put(new HintMeshKey(hintMeshConfiguration.type(), newSelectionKeys,
                        newMode.zoom()),
                new HintMeshState(newHintMesh, lastSelectedHintPoint));
        hintMesh = newHintMesh;
        if (hintMesh.hints().isEmpty())
            overlay.hideHintMesh();
        else
            overlay.setHintMesh(hintMesh, newZoom);
        if (hintMeshConfiguration.mouseMovement() ==
            HintMouseMovement.MOUSE_FOLLOWS_HINT_GRID_CENTER) {
            moveMouse(hintMeshCenter(hintMesh.hints(), hintMesh.selectedKeySequence()));
        }
    }

    private static Rectangle hintCenterBounds(List<Hint> hints, Zoom zoom) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Hint hint : hints) {
            double x = zoom.unzoomedX(hint.centerX());
            double y = zoom.unzoomedY(hint.centerY());
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        return new Rectangle((int) minX, (int) minY,
                (int) (maxX - minX), (int) (maxY - minY));
    }

    private static Point hintMeshCenter(List<Hint> hints, List<Key> selectedHintKeySequence) {
        int hintCountThatStartWithSelectedHintKeySequence = 0;
        double selectedHintKeySequenceCenterX = 0;
        double selectedHintKeySequenceCenterY = 0;
        for (Hint hint : hints) {
            if (!hint.startsWith(selectedHintKeySequence))
                continue;
            hintCountThatStartWithSelectedHintKeySequence++;
            selectedHintKeySequenceCenterX += hint.centerX();
            selectedHintKeySequenceCenterY += hint.centerY();
            if (hint.keySequence().size() == selectedHintKeySequence.size()) {
                break;
            }
        }
        selectedHintKeySequenceCenterX /=
                hintCountThatStartWithSelectedHintKeySequence == 0 ?
                        hints.size() :
                        hintCountThatStartWithSelectedHintKeySequence;
        selectedHintKeySequenceCenterY /=
                hintCountThatStartWithSelectedHintKeySequence == 0 ?
                        hints.size() :
                        hintCountThatStartWithSelectedHintKeySequence;
        return new Point(selectedHintKeySequenceCenterX,
                selectedHintKeySequenceCenterY);
    }

    public boolean showingHintMesh() {
        return pendingUiHintQuery != null ||
               (!hintMeshStates.isEmpty() && !hintJustSelected);
    }

    public boolean waitingForUiElements() {
        return pendingUiHintQuery != null;
    }

    public boolean hintMeshEmpty() {
        return pendingUiHintQuery == null && hintMesh != null &&
               hintMesh.hints().isEmpty();
    }

    public void completePendingUiHintQuery() {
        if (pendingUiHintQuery == null || !pendingUiHintQuery.future().isDone())
            return;
        PendingUiHintQuery pending = pendingUiHintQuery;
        pendingUiHintQuery = null;
        List<UiElement> uiElements;
        try {
            uiElements = pending.future().get();
        }
        catch (ExecutionException e) {
            logger.warn("UI element query failed", e.getCause());
            return;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        lastUiElements = uiElements;
        HintMeshConfiguration hintMeshConfiguration = pending.hintMeshConfiguration();
        ScreenFilter screenFilter = pending.screenFilter();
        ZoomConfiguration zoom = currentMode.zoom();
        HintMesh newHintMesh = buildHintMesh(hintMeshConfiguration,
                zoom, pending.zoom(), screenFilter,
                uiElements, screenManager.activeScreen());
        activateHintMesh(currentMode, newHintMesh, hintMeshConfiguration, screenFilter,
                currentZoom);
        overlay.runPendingHintMeshWork();
    }

    private ScreenFilter screenFilter(HintMeshConfiguration hintMeshConfiguration) {
        return switch (hintMeshConfiguration.type()) {
            case HintMeshType.GridHintMesh gridHintMesh -> screenFilter(gridHintMesh.area());
            case HintMeshType.UiAccessibilityHintMesh uiAccessibilityHintMesh -> screenFilter(uiAccessibilityHintMesh.area());
            case HintMeshType.UiVisionHintMesh uiVisionHintMesh -> {
                Point center = uiHintArea(uiVisionHintMesh.area()).center();
                yield ScreenFilter.of(
                        screenManager.nearestScreenContaining(center.x(), center.y()));
            }
            case HintMeshType.PositionHistoryHintMesh positionHistoryHintMesh -> {
                Point position = currentPositionHistory.positions().getFirst();
                yield ScreenFilter.of(
                        screenManager.screenContaining(position.x(), position.y()));
            }
        };
    }

    private ScreenFilter screenFilter(HintGridArea area) {
        return switch (area.size().source()) {
            case ACTIVE_SCREEN, LAST_SELECTED_HINT_CELL ->
                    ScreenFilter.of(screenManager.activeScreen());
            case ALL_SCREENS ->
                    ScreenFilter.of(sortedScreens().getFirst());
            case ACTIVE_WINDOW -> activeWindowScreenFilter();
        };
    }

    private ScreenFilter screenFilter(UiHintArea uiArea) {
        return switch (uiArea) {
            case ACTIVE_SCREEN -> ScreenFilter.of(screenManager.activeScreen());
            case ALL_SCREENS -> ScreenFilter.of(sortedScreens().getFirst());
            case ACTIVE_WINDOW -> activeWindowScreenFilter();
        };
    }

    private ScreenFilter activeWindowScreenFilter() {
        Point areaCenter = overlay.activeWindowRectangle(1, 1, 0, 0, 0, 0).center();
        return ScreenFilter.of(screenManager.nearestScreenContaining(
                areaCenter.x(), areaCenter.y()));
    }

    private static boolean sameUiHintArea(HintMeshType type, HintMeshType currentType) {
        if (type instanceof HintMeshType.UiAccessibilityHintMesh uiAccessibilityHintMesh)
            return currentType instanceof HintMeshType.UiAccessibilityHintMesh currentUiAccessibilityHintMesh &&
                   uiAccessibilityHintMesh.area() == currentUiAccessibilityHintMesh.area();
        return type instanceof HintMeshType.UiVisionHintMesh uiVisionHintMesh &&
               currentType instanceof HintMeshType.UiVisionHintMesh currentUiVisionHintMesh &&
               uiVisionHintMesh.area() == currentUiVisionHintMesh.area();
    }

    private Future<List<UiElement>> startUiElementQuery(HintMeshType type) {
        if (type instanceof HintMeshType.UiVisionHintMesh uiVisionHintMesh)
            return vision.startFindElements(screenManager.screens(),
                    uiHintArea(uiVisionHintMesh.area()), uiVisionHintMesh.density());
        UiHintArea uiArea = ((HintMeshType.UiAccessibilityHintMesh) type).area();
        return uiArea == UiHintArea.ACTIVE_WINDOW ?
                uiAutomation.startFindActiveWindowUiElements() :
                uiAutomation.startFindUiElementsInArea(uiHintArea(uiArea));
    }

    /** The area the UI elements are looked for in, and the one the background covers. */
    private Rectangle uiHintArea(UiHintArea uiArea) {
        return switch (uiArea) {
            case ACTIVE_WINDOW -> overlay.activeWindowRectangle(1, 1, 0, 0, 0, 0);
            case ACTIVE_SCREEN -> screenManager.activeScreen().rectangle();
            case ALL_SCREENS -> Rectangle.union(screenRectangles(screenManager.screens()));
        };
    }

    private static List<Rectangle> screenRectangles(Collection<Screen> screens) {
        return screens.stream().map(Screen::rectangle).toList();
    }

    /**
     * Drops the levels above the one modeName owns. Nothing is dropped when it owns the
     * top level, or owns none at all (a mode that renders a level without being a step).
     */
    private boolean popLevelsAbove(String modeName) {
        int popCount = 0;
        for (CellGridLevel level : cellGridLevelStack) {
            if (level.modeName().equals(modeName))
                break;
            popCount++;
        }
        if (popCount == cellGridLevelStack.size())
            return false;
        for (int levelIndex = 0; levelIndex < popCount; levelIndex++)
            cellGridLevelStack.pop();
        return popCount != 0;
    }

    /** Same cell, but size and center may have changed with the mode or a variable. */
    private void recomputeTopLevelArea(HintGridAreaSize size, HintGridAreaCenter center) {
        if (cellGridLevelStack.isEmpty())
            return;
        CellGridLevel level = cellGridLevelStack.pop();
        cellGridLevelStack.push(new CellGridLevel(level.modeName(), level.cell(),
                cellGridArea(level.cell(), size, center)));
    }

    /** A desktop region as it appears on screen. */
    private static Rectangle zoomedRectangle(Rectangle rectangle, Zoom zoom) {
        int left = (int) Math.round(zoom.zoomedX(rectangle.x()));
        int top = (int) Math.round(zoom.zoomedY(rectangle.y()));
        return new Rectangle(left, top,
                (int) Math.round(zoom.zoomedX(rectangle.x() + rectangle.width())) - left,
                (int) Math.round(zoom.zoomedY(rectangle.y() + rectangle.height())) - top);
    }

    private static Point zoomedPoint(Point point, Zoom zoom) {
        return new Point((int) Math.round(zoom.zoomedX(point.x())),
                (int) Math.round(zoom.zoomedY(point.y())));
    }

    /**
     * The cell scaled by the size percents, centered on the point grid-area-center selects.
     */
    private Rectangle cellGridArea(Rectangle cell, HintGridAreaSize size,
                                   HintGridAreaCenter center) {
        Point gridCenter = switch (center) {
            case SCREEN_CENTER -> screenManager.activeScreen().rectangle().center();
            case MOUSE -> new Point(mouseX, mouseY);
            case LAST_SELECTED_HINT -> cell.center();
            case ACTIVE_WINDOW_CENTER ->
                    overlay.activeWindowRectangle(1, 1, 0, 0, 0, 0).center();
        };
        return scaledArea(cell, size, gridCenter);
    }

    private static Rectangle scaledArea(Rectangle sourceRectangle, HintGridAreaSize size,
                                        Point gridCenter) {
        int width = (int) Math.round(sourceRectangle.width() * size.widthPercent());
        int height = (int) Math.round(sourceRectangle.height() * size.heightPercent());
        return new Rectangle(
                (int) Math.round(gridCenter.x() - width / 2.0),
                (int) Math.round(gridCenter.y() - height / 2.0),
                width, height);
    }

    private HintMesh buildHintMesh(
            HintMeshConfiguration hintMeshConfiguration,
            ZoomConfiguration zoomConfiguration, Zoom zoom,
            ScreenFilter screenFilter,
            List<UiElement> uiElements, Screen activeScreen) {
        HintMeshBuilder hintMesh = new HintMeshBuilder();
        hintMesh.visible(hintMeshConfiguration.visible())
                .styleByFilter(hintMeshConfiguration.styleByFilter());
        HintMeshType type = hintMeshConfiguration.type();
        if (type instanceof HintMeshType.GridHintMesh gridHintMesh) {
            List<FixedSizeHintGrid> fixedSizeHintGrids = new ArrayList<>();
            HintGridArea area = gridHintMesh.area();
            if (area.size().source() == HintGridAreaSizeSource.ALL_SCREENS) {
                // The one multi-grid source: a screen-centered grid per screen (scaled
                // by the size percents). The center does not apply.
                List<Screen> sortedScreens = sortedScreens();
                List<Rectangle> areaRectangles = new ArrayList<>();
                for (Screen screen : sortedScreens) {
                    Rectangle areaRectangle = scaledArea(screen.rectangle(), area.size(),
                            screen.rectangle().center());
                    areaRectangles.add(areaRectangle);
                    HintGridLayout gridLayout = gridHintMesh.layout(
                            ScreenFilter.of(activeScreen));
                    fixedSizeHintGrids.add(hintGridForArea(areaRectangle,
                            areaRectangle.center(), gridLayout, screen.scale(), zoom));
                }
                hintMesh.area(Rectangle.union(areaRectangles));
                hintMesh.backgroundArea(
                        Rectangle.union(screenRectangles(sortedScreens)));
            }
            else {
                // Single grid: the source gives a rectangle, the size percents scale
                // it, the center places it. A cell grid reads its frozen level
                // instead, so back navigation reproduces the exact area it rendered.
                Rectangle areaRectangle;
                Point gridCenter;
                // Areas resolve to screen space: a screen source is already there, a
                // desktop region is mapped through the zoom.
                if (area.size().source() == HintGridAreaSizeSource.LAST_SELECTED_HINT_CELL) {
                    areaRectangle = cellGridLevelStack.isEmpty() ?
                            activeScreen.rectangle() :
                            zoomedRectangle(cellGridLevelStack.peek().area(), zoom);
                    gridCenter = areaRectangle.center();
                }
                else {
                    Rectangle sourceRectangle = switch (area.size().source()) {
                        case ACTIVE_SCREEN -> activeScreen.rectangle();
                        case ACTIVE_WINDOW -> zoomedRectangle(
                                overlay.activeWindowRectangle(1, 1, 0, 0, 0, 0), zoom);
                        default -> throw new IllegalStateException();
                    };
                    gridCenter = switch (area.center()) {
                        case SCREEN_CENTER ->
                                activeScreen.rectangle().center();
                        case MOUSE -> zoomedPoint(new Point(mouseX, mouseY), zoom);
                        case LAST_SELECTED_HINT -> lastSelectedHintPoint == null ?
                                activeScreen.rectangle().center() :
                                zoomedPoint(lastSelectedHintPoint, zoom);
                        case ACTIVE_WINDOW_CENTER -> zoomedRectangle(
                                overlay.activeWindowRectangle(1, 1, 0, 0, 0, 0), zoom)
                                .center();
                    };
                    areaRectangle = scaledArea(sourceRectangle, area.size(), gridCenter);
                }
                logger.trace("Grid center " + gridCenter);
                Screen scaleScreen = screenManager.nearestScreenContaining(
                        gridCenter.x(), gridCenter.y());
                HintGridLayout gridLayout = gridHintMesh.layout(screenFilter);
                fixedSizeHintGrids.add(hintGridForArea(areaRectangle, gridCenter,
                        gridLayout, scaleScreen.scale(), zoom));
                hintMesh.area(areaRectangle);
                hintMesh.backgroundArea(
                        area.size().source() == HintGridAreaSizeSource.ACTIVE_WINDOW ?
                                areaRectangle : scaleScreen.rectangle());
            }
            int hintCountSum = fixedSizeHintGrids.stream()
                                                 .mapToInt(FixedSizeHintGrid::hintCount)
                                                 .sum();
            HintGridLayout firstScreenGridLayout = gridHintMesh.layout(screenFilter);
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
            int rowKeyOffset = hintMeshKeys.rowKeyOffset();
            List<Hint> hints = new ArrayList<>();
            int beginSubgridIndex = 0;
            Set<Integer> prefixLengths = new HashSet<>();
            for (FixedSizeHintGrid fixedSizeHintGrid : fixedSizeHintGrids) {
                int beginHintIndex = hints.size();
                hints.addAll(buildHints(fixedSizeHintGrid,
                        selectionKeys, rowKeyOffset,
                        hintCountSum,
                        beginSubgridIndex, subgridCount,
                        beginHintIndex,
                        layoutRowCount,
                        layoutColumnCount, layoutRowOriented,
                        prefixLengths));
                beginSubgridIndex +=
                        fixedSizeHintGrid.subgridCount(layoutRowCount, layoutColumnCount);
            }
            hintMesh.hints(hints)
                    .prefixLength(prefixLengths.size() == 1 ?
                            prefixLengths.iterator().next() : -1);
            if (!hints.isEmpty()) {
                HintMeshStyle style =
                        styleForFilter(hintMeshConfiguration, screenFilter);
                List<Decoration> decorations = style.decorations();
                double scale = activeScreen.scale();
                // Tiled: subdecoration (index 1) in each cell, subsubdecoration
                // (index 2) inside each of those.
                hintMesh.subDecoration(buildDecorationMesh(style,
                        hintCellRectangle(hints.getFirst()), scale, zoom,
                        decorations.get(1), decorations.get(2)));
                // Whole-area (index 0): the grid drawn as one big cell. Not the background area,
                // which is the whole screen even when the grid is a small drilled cell.
                Rectangle gridRectangle = Rectangle.union(
                        fixedSizeHintGrids.stream()
                                          .map(FixedSizeHintGrid::rectangle)
                                          .toList());
                hintMesh.decoration(buildDecorationMesh(style, gridRectangle, scale, zoom,
                        decorations.get(0), null));
            }
        }
        else if (type instanceof HintMeshType.UiAccessibilityHintMesh uiAccessibilityHintMesh) {
            int hintCount = uiElements.size();
            List<Hint> hints = new ArrayList<>(hintCount);
            Set<Integer> prefixLengths = new HashSet<>();
            buildUiHints(hintMeshConfiguration, screenFilter, uiElements,
                    prefixLengths, hints);
            Rectangle uiArea = uiHintArea(uiAccessibilityHintMesh.area());
            hintMesh.hints(hints)
                    .prefixLength(prefixLengths.size() == 1 ?
                            prefixLengths.iterator().next() : -1)
                    .area(uiArea)
                    .backgroundArea(uiArea);
        }
        else if (type instanceof HintMeshType.UiVisionHintMesh uiVisionHintMesh) {
            int hintCount = uiElements.size();
            List<Hint> hints = new ArrayList<>(hintCount);
            Set<Integer> prefixLengths = new HashSet<>();
            buildUiHints(hintMeshConfiguration, screenFilter, uiElements,
                    prefixLengths, hints);
            Rectangle uiArea = uiHintArea(uiVisionHintMesh.area());
            hintMesh.hints(hints)
                    .prefixLength(prefixLengths.size() == 1 ?
                            prefixLengths.iterator().next() : -1)
                    .area(uiArea)
                    .backgroundArea(uiArea);
        }
        else {
            List<Point> positions = currentPositionHistory.positions();
            int hintCount = positions.size();
            List<Hint> hints = new ArrayList<>(hintCount);
            HintMeshKeys hintMeshKeys =
                    hintMeshConfiguration.keysByFilter().get(screenFilter);
            List<Key> selectionKeys = hintMeshKeys.selectionKeys();
            Set<Integer> prefixLengths = new HashSet<>();
            int rowKeyOffset = hintMeshKeys.rowKeyOffset();
            for (Point point : positions) {
                List<Key> keySequence = hintKeySequence(
                        selectionKeys, rowKeyOffset, hintCount,
                        0, -1, currentPositionHistory.id(point),
                        -1, -1,
                        -1, -1,
                        -1, -1, false, prefixLengths);
                hints.add(new Hint(zoom.zoomedX(point.x()), zoom.zoomedY(point.y()),
                        -1, -1, keySequence));
            }
            hintMesh.hints(hints)
                    .prefixLength(prefixLengths.size() == 1 ?
                            prefixLengths.iterator().next() : -1);
            // Background covers all screens that contain at least one hint.
            List<Rectangle> screensWithHint = screenManager.screens()
                    .stream()
                    .map(Screen::rectangle)
                    .filter(screenRectangle -> hints.stream()
                                                    .anyMatch(hint -> screenRectangle.contains(
                                                            hint.centerX(), hint.centerY())))
                    .toList();
            if (!screensWithHint.isEmpty()) {
                Rectangle hintScreens = Rectangle.union(screensWithHint);
                hintMesh.area(hintScreens).backgroundArea(hintScreens);
            }
        }
        // Prefer the live selection from the current mesh when the hint
        // area overlaps (e.g. zoom toggle). This is authoritative: do not
        // fall through to hintMeshStates even if the live selection is empty.
        boolean selectionResolved = false;
        if (this.hintMesh != null && !this.hintMesh.hints().isEmpty() &&
            !hintMesh.hints().isEmpty() &&
            !(hintMeshConfiguration.type() instanceof HintMeshType.PositionHistoryHintMesh)) {
            Rectangle oldBounds = hintCenterBounds(this.hintMesh.hints(), currentZoom);
            Rectangle newBounds = hintCenterBounds(hintMesh.hints(), zoom);
            List<Key> selectedKeySequence = this.hintMesh.selectedKeySequence();
            // Only carry the selection over to hints it can still select: a mesh covering the same
            // area can label it with fewer keys, and a selection longer than its labels then matches
            // nothing and grows with every keypress.
            if (oldBounds.overlapRatio(newBounds) >= 0.9 &&
                hintMesh.hints()
                        .stream()
                        .anyMatch(hint -> hint.startsWith(selectedKeySequence))) {
                hintMesh.selectedKeySequence(selectedKeySequence);
                selectionResolved = true;
            }
        }
        if (!selectionResolved) {
            HintMeshState previousHintMeshState = hintMeshStates.get(
                    new HintMeshKey(hintMeshConfiguration.type(),
                            hintMeshConfiguration.keysByFilter()
                                                 .get(screenFilter)
                                                 .selectionKeys(),
                            zoomConfiguration));
            if (previousHintMeshState != null &&
                previousHintMeshState.hintMesh.hints()
                                              .equals(hintMesh.hints())) {
                hintMesh.selectedKeySequence(
                        previousHintMeshState.hintMesh.selectedKeySequence());
            }
        }
        return hintMesh.build();
    }

    private void buildUiHints(HintMeshConfiguration hintMeshConfiguration,
                           ScreenFilter screenFilter,
                           List<UiElement> uiElements,
                              Set<Integer> prefixLengths, List<Hint> hints) {
        HintMeshKeys hintMeshKeys =
                hintMeshConfiguration.keysByFilter().get(screenFilter);
        List<Key> selectionKeys = hintMeshKeys.selectionKeys();
        int rowKeyOffset = hintMeshKeys.rowKeyOffset();
        for (int i = 0; i < uiElements.size(); i++) {
            UiElement element = uiElements.get(i);
            List<Key> keySequence = hintKeySequence(
                    selectionKeys, rowKeyOffset, uiElements.size(),
                    0, -1, i,
                    -1, -1, -1, -1, -1, -1, false, prefixLengths);
            hints.add(new Hint(element.centerX(), element.centerY(),
                    -1, -1, keySequence));
        }
        if (!hints.isEmpty()) {
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (Hint hint : hints) {
                minX = Math.min(minX, hint.centerX());
                maxX = Math.max(maxX, hint.centerX());
                minY = Math.min(minY, hint.centerY());
                maxY = Math.max(maxY, hint.centerY());
            }
            logger.debug("UI hint bounds: minX = " + (int) minX +
                    ", maxX = " + (int) maxX + ", minY = " + (int) minY +
                    ", maxY = " + (int) maxY);
        }
    }

    private List<Screen> sortedScreens() {
        return screenManager.screens()
                            .stream()
                            .sorted(Comparator.comparing(
                                                      (Screen s) -> s.rectangle().x())
                                              .thenComparing(
                                                      s -> s.rectangle().y()))
                            .toList();
    }

    private static List<Hint> buildHints(FixedSizeHintGrid fixedSizeHintGrid,
                                         List<Key> selectionKeys, int rowKeyOffset, int hintCount,
                                         int beginSubgridIndex, int subgridCount,
                                         int beginHintIndex, int layoutRowCount,
                                         int layoutColumnCount, boolean layoutRowOriented,
                                         Set<Integer> prefixLengths) {
        int rowCount = fixedSizeHintGrid.rowCount();
        int columnCount = fixedSizeHintGrid.columnCount();
        double hintMeshX = fixedSizeHintGrid.hintMeshX();
        double hintMeshY = fixedSizeHintGrid.hintMeshY();
        double cellWidth = fixedSizeHintGrid.cellWidth;
        double cellHeight = fixedSizeHintGrid.cellHeight;
        int gridHintCount = rowCount * columnCount;
        List<Hint> hints = new ArrayList<>(gridHintCount);
        int hintIndex = beginHintIndex;
        double rowHeightOffset = 0;
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            double columnWidthOffset = 0;
            for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                List<Key> keySequence = hintKeySequence(selectionKeys, rowKeyOffset, hintCount,
                        beginSubgridIndex, subgridCount,
                        hintIndex,
                        rowIndex, columnIndex,
                        rowCount, columnCount,
                        layoutRowCount, layoutColumnCount, layoutRowOriented,
                        prefixLengths);
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

    private static List<Key> hintKeySequence(List<Key> keys, int rowKeyOffset, int hintCount,
                                             int beginSubgridIndex, int subgridCount,
                                             int hintIndex,
                                             int rowIndex, int columnIndex,
                                             int rowCount, int columnCount,
                                             int layoutRowCount, int layoutColumnCount,
                                             boolean layoutRowOriented,
                                             Set<Integer> prefixLengths) {
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
            int second = (layout.second + rowKeyOffset) % keyCount;
            if (layout.oneOne) {
                prefixLengths.add(1);
                return List.of(
                        keys.get(layout.first),
                        keys.get(second)
                );
            }
            else if (layout.threeOrFour) {
                if (layout.oneTwo) {
                    prefixLengths.add(1);
                    return List.of(
                            keys.get(layout.first),
                            keys.get(second / keyCount),
                            keys.get(second % keyCount)
                    );
                }
                if (layout.twoOne) {
                    prefixLengths.add(2);
                    return List.of(
                            keys.get(layout.first / keyCount),
                            keys.get(layout.first % keyCount),
                            keys.get(second)
                    );
                }
                if (layout.twoTwo) { // 6^4 = 1296 hints
                    prefixLengths.add(2);
                    return List.of(
                            keys.get(layout.first / keyCount),
                            keys.get(layout.first % keyCount),
                            keys.get(second / keyCount),
                            keys.get(second % keyCount)
                    );
                }
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

    private FixedSizeHintGrid hintGridForArea(Rectangle areaRectangle, Point gridCenter,
                                              HintGridLayout gridLayout, double scale,
                                              Zoom zoom) {
        return switch (gridLayout.cellSizing()) {
            case HintCellSizing.FixedCellSize fixedCellSize -> fixedSizeHintGrid(
                    areaRectangle, gridCenter, gridLayout.maxRowCount(),
                    gridLayout.maxColumnCount(),
                    fixedCellSize.cellWidth() * scale * zoom.percent(),
                    fixedCellSize.cellHeight() * scale * zoom.percent());
            case HintCellSizing.FitToArea fitToArea -> fitToAreaHintGrid(
                    areaRectangle, gridLayout.maxRowCount(), gridLayout.maxColumnCount());
        };
    }

    // Divides the area into maxRowCount x maxColumnCount cells that fill it exactly.
    private FixedSizeHintGrid fitToAreaHintGrid(Rectangle areaRectangle, int rowCount,
                                                int columnCount) {
        double cellWidth = (double) areaRectangle.width() / columnCount;
        double cellHeight = (double) areaRectangle.height() / rowCount;
        return new FixedSizeHintGrid(areaRectangle.x(), areaRectangle.y(),
                cellWidth * columnCount, cellHeight * rowCount, rowCount, columnCount,
                cellWidth, cellHeight);
    }

    /** The desktop region a hint's cell covers. */
    private static Rectangle unzoomedHintCell(Hint hint, Zoom zoom) {
        int left = (int) Math.round(zoom.unzoomedX(hint.centerX() - hint.cellWidth() / 2));
        int top = (int) Math.round(zoom.unzoomedY(hint.centerY() - hint.cellHeight() / 2));
        return new Rectangle(left, top,
                (int) Math.round(zoom.unzoomedX(hint.centerX() + hint.cellWidth() / 2)) -
                left,
                (int) Math.round(zoom.unzoomedY(hint.centerY() + hint.cellHeight() / 2)) -
                top);
    }

    private static Rectangle hintCellRectangle(Hint hint) {
        // Round the cell's edges (not its center and width independently) so this matches the
        // renderer's box geometry. Otherwise the drilled-into area can be a pixel narrower than the
        // box the crop reveals, leaving a seam on the grid's right/bottom during the transition.
        int left = (int) Math.round(hint.centerX() - hint.cellWidth() / 2);
        int top = (int) Math.round(hint.centerY() - hint.cellHeight() / 2);
        return new Rectangle(left, top,
                (int) Math.round(hint.centerX() + hint.cellWidth() / 2) - left,
                (int) Math.round(hint.centerY() + hint.cellHeight() / 2) - top);
    }

    private static HintMeshStyle styleForFilter(HintMeshConfiguration configuration,
                                                ScreenFilter filter) {
        HintMeshStyle style = configuration.styleByFilter().get(filter);
        if (style != null)
            return style;
        return configuration.styleByFilter().get(
                ScreenFilter.AnyScreenFilter.ANY_SCREEN_FILTER);
    }

    /**
     * Builds a decoration grid laid out inside area. When childDecoration is
     * non-null, a deeper decoration is built inside each cell of this one.
     */
    private HintMesh buildDecorationMesh(HintMeshStyle style, Rectangle area,
                                         double scale, Zoom zoom, Decoration decoration,
                                         Decoration childDecoration) {
        // A single cell draws no interior lines, but still shows if it has a label, a
        // perimeter, or a fill (e.g. a whole-area 1x1 with label-override).
        boolean hasGrid = decoration.maxRowCount() * decoration.maxColumnCount() > 1;
        boolean hasLabel = !decoration.labelOverride().isEmpty()
                           || !decoration.labelKeys().isEmpty();
        boolean hasFill = decoration.boxOpacity() > 0;
        if (!hasGrid && !hasLabel && !decoration.boxFramed() && !hasFill)
            return null;
        HintGridLayout decorationLayout = new HintGridLayout(
                decoration.maxRowCount(), decoration.maxColumnCount(),
                new HintCellSizing.FitToArea(),
                decoration.maxRowCount(), decoration.maxColumnCount(), true);
        FixedSizeHintGrid grid = hintGridForArea(area,
                area.center(), decorationLayout, scale, zoom);
        List<Hint> decorationHints;
        int prefixLength = -1;
        if (decoration.labelKeys().isEmpty()) {
            // Lines only (e.g. a centered cross): positioned cells, no labels.
            decorationHints = new ArrayList<>();
            for (int row = 0; row < grid.rowCount(); row++)
                for (int column = 0; column < grid.columnCount(); column++)
                    decorationHints.add(new Hint(
                            grid.hintMeshX() + (column + 0.5) * grid.cellWidth(),
                            grid.hintMeshY() + (row + 0.5) * grid.cellHeight(),
                            grid.cellWidth(), grid.cellHeight(), List.of()));
        }
        else {
            int layoutRowCount = Math.min(grid.rowCount(), decorationLayout.layoutRowCount());
            int layoutColumnCount = Math.min(grid.columnCount(),
                    decorationLayout.layoutColumnCount());
            int subgridCount = grid.subgridCount(layoutRowCount, layoutColumnCount);
            Set<Integer> prefixLengths = new HashSet<>();
            decorationHints = buildHints(grid, decoration.labelKeys(), 0,
                    grid.hintCount(), 0, subgridCount, 0, layoutRowCount,
                    layoutColumnCount, decorationLayout.layoutRowOriented(),
                    prefixLengths);
            prefixLength = prefixLengths.size() == 1 ?
                    prefixLengths.iterator().next() : -1;
        }
        HintMeshStyle decorationStyle = style.builder()
                .boxColor(decoration.boxColor())
                .boxOpacity(decoration.boxOpacity())
                .boxBorderThickness(decoration.boxBorderThickness())
                .boxBorderLength(decoration.boxBorderLength())
                .boxBorderColor(decoration.boxBorderColor())
                .boxBorderOpacity(decoration.boxBorderOpacity())
                .boxBorderRadius(decoration.boxBorderRadius())
                .prefixInBackground(false)
                .prefixBoxEnabled(false)
                .boxWidthPercent(1d)
                .boxHeightPercent(1d)
                .backgroundOpacity(0d)
                .build(style);
        ScreenFilterMap<HintMeshStyle> decorationStyleByFilter = new ScreenFilterMap<>(
                Map.of(ScreenFilter.AnyScreenFilter.ANY_SCREEN_FILTER, decorationStyle));
        HintMesh childMesh = null;
        if (childDecoration != null) {
            Rectangle decorationCell = new Rectangle(
                    (int) Math.round(grid.hintMeshX()),
                    (int) Math.round(grid.hintMeshY()),
                    (int) Math.round(grid.cellWidth()),
                    (int) Math.round(grid.cellHeight()));
            childMesh = buildDecorationMesh(style, decorationCell, scale, zoom,
                    childDecoration, null);
        }
        return new HintMesh(true, decorationHints, prefixLength, List.of(),
                decorationStyleByFilter, area, area, null, childMesh);
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

        /** Rounded on its edges, like a hint cell's rectangle. */
        public Rectangle rectangle() {
            int left = (int) Math.round(hintMeshX);
            int top = (int) Math.round(hintMeshY);
            return new Rectangle(left, top,
                    (int) Math.round(hintMeshX + hintMeshWidth) - left,
                    (int) Math.round(hintMeshY + hintMeshHeight) - top);
        }

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

    /**
     * Undo.
     */
    public void unselectHintKey() {
        if (pendingUiHintQuery != null)
            // Let user perform an action on undo (e.g. switch mode) even when UI hint query is ongoing.
            return;
        HintMeshConfiguration hintMeshConfiguration = currentMode.hintMesh();
        if (!hintMeshConfiguration.enabled())
            return;
        HintMeshKeys hintMeshKeys = hintMeshConfiguration.keysByFilter()
                                                         .get(screenFilter);
        hintJustSelected = false;
        List<Key> selectedKeySequence = hintMesh.selectedKeySequence();
        if (!selectedKeySequence.isEmpty()) {
            hintMesh = hintMesh.builder()
                               .selectedKeySequence(selectedKeySequence.subList(0,
                                       selectedKeySequence.size() - 1))
                               .build();
            HintMeshKey hintMeshKey =
                    new HintMeshKey(hintMeshConfiguration.type(),
                            hintMeshKeys.selectionKeys(),
                            currentMode.zoom());
            hintMeshStates.put(
                    hintMeshKey,
                    new HintMeshState(
                            hintMesh,
                            hintMeshStates.get(hintMeshKey).previousModeSelectedHintPoint
                    )
            );
            overlay.setHintMesh(hintMesh, currentZoom);
            if (hintMeshConfiguration.mouseMovement() == HintMouseMovement.MOUSE_FOLLOWS_HINT_GRID_CENTER) {
                moveMouse(hintMeshCenter(hintMesh.hints(),
                        hintMesh.selectedKeySequence()));
            }
            lastHintCommandSupercedesOtherCommands = true;
        }
    }

    public void selectHintKey(Key key) {
        if (pendingUiHintQuery != null) {
            lastHintCommandSupercedesOtherCommands = true;
            return;
        }
        if (key == null)
            return;
        HintMeshConfiguration hintMeshConfiguration = currentMode.hintMesh();
        if (!hintMeshConfiguration.enabled())
            return;
        HintMeshKeys hintMeshKeys = hintMeshConfiguration.keysByFilter()
                                                         .get(screenFilter);
        if (hintJustSelected)
            return;
        List<Key> newSelectedKeySequence = new ArrayList<>(hintMesh.selectedKeySequence());
        newSelectedKeySequence.add(key);
        Hint exactMatchHint = null;
        boolean atLeastOneHintStartsWithNewSelectedHintKeySequence = false;
        for (Hint hint : hintMesh.hints()) {
            if (!hint.startsWith(newSelectedKeySequence))
                continue;
            atLeastOneHintStartsWithNewSelectedHintKeySequence = true;
            if (hint.keySequence().size() == newSelectedKeySequence.size()) {
                exactMatchHint = hint;
                break;
            }
        }
        if (!atLeastOneHintStartsWithNewSelectedHintKeySequence) {
            if (hintMeshConfiguration.eatUnusedSelectionKeys())
                lastHintCommandSupercedesOtherCommands = true;
            return;
        }
        if (exactMatchHint != null) {
            if (isInZoom(exactMatchHint.centerX(), exactMatchHint.centerY())) {
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
            // Unzoomed, like lastSelectedHintPoint: a cell in screen units could not
            // compound, so zooming into it twice would magnify by the same factor.
            pendingSelectedCell = exactMatchHint.cellWidth() > 0 ?
                    unzoomedHintCell(exactMatchHint, currentZoom) : null;
            lastSelectedHintCell = pendingSelectedCell;
             if (hintMeshConfiguration.mouseMovement() != HintMouseMovement.NO_MOVEMENT) {
                 moveMouse(new Point(exactMatchHint.centerX(), exactMatchHint.centerY()));
             }
            finalizeHintSelection(exactMatchHint, newSelectedKeySequence);
        }
        else {
            hintMesh =
                    hintMesh.builder().selectedKeySequence(newSelectedKeySequence).build();
            HintMeshKey hintMeshKey =
                    new HintMeshKey(hintMeshConfiguration.type(),
                            hintMeshKeys.selectionKeys(), currentMode.zoom());
            hintMeshStates.put(
                    hintMeshKey,
                    new HintMeshState(
                            hintMesh,
                            hintMeshStates.get(hintMeshKey).previousModeSelectedHintPoint
                    ));
            overlay.setHintMesh(hintMesh, currentZoom);
            if (hintMeshConfiguration.mouseMovement() == HintMouseMovement.MOUSE_FOLLOWS_HINT_GRID_CENTER) {
                moveMouse(hintMeshCenter(hintMesh.hints(), newSelectedKeySequence));
            }
            lastHintCommandSupercedesOtherCommands = true;
        }
    }

    /**
     * Other commands should be canceled when an unselect hint key is successful,
     * when a select hint key does not trigger a hint match (and there are still some
     * letters to select), and when an unused selection key is eaten.
     */
    public boolean pollLastHintCommandSupercedesOtherCommands() {
        try {
            return lastHintCommandSupercedesOtherCommands;
        } finally {
            lastHintCommandSupercedesOtherCommands = false;
        }
    }

    /** A point on no screen was laid out by the zoom, past the zoomed screen's edge. */
    private boolean isInZoom(double x, double y) {
        return currentZoom.screenRectangle().contains(x, y) ||
               screenManager.screenContaining(x, y) == null;
    }

    private void moveMouse(Point point) {
        if (isInZoom(point.x(), point.y())) {
            mouseX = (int) Math.round(currentZoom.unzoomedX(point.x()));
            mouseY = (int) Math.round(currentZoom.unzoomedY(point.y()));
        }
        else {
            mouseX = (int) Math.round(point.x());
            mouseY = (int) Math.round(point.y());
        }
        logger.debug("Moving mouse to (" + mouseX + ", " + mouseY + ")");
        mouseManager.moveTo(mouseX, mouseY);
    }

    private void finalizeHintSelection(Hint hint, List<Key> newSelectedKeySequence) {
        HintMeshConfiguration hintMeshConfiguration = currentMode.hintMesh();
        hintJustSelected = true;
        logger.trace("Hint " + keyRedactor.keys(hint.keySequence()) + " selected");
        if (hintMeshConfiguration.visible())
            overlay.animateHintMatch(hint);
        hintMesh =
                hintMesh.builder()
                        .selectedKeySequence(newSelectedKeySequence)
                        .build();
        if (hintMeshConfiguration.modeAfterSelection() != null) {
            logger.warn(
                    "hint.mode-after-selection has been deprecated: use " +
                    currentMode.name() + ".to." +
                    hintMeshConfiguration.modeAfterSelection() +
                    "=<combo> instead, along with " + currentMode.name() +
                    ".break-combo-preparation=<combo>");
            modeController.switchMode(hintMeshConfiguration.modeAfterSelection());
        }
    }

    PositionHistory positionHistory(String positionHistoryName) {
        PositionHistoryConfiguration configuration =
                positionHistoryConfigurationByName.get(positionHistoryName);
        PositionHistoryKey key = new PositionHistoryKey(positionHistoryName,
                isolationKey(configuration.isolation()));
        return positionHistoryByKey.computeIfAbsent(key,
                key1 -> new PositionHistory(key1, configuration.maxSize()));
    }

    private PositionHistoryIsolationKey isolationKey(PositionHistoryIsolation isolation) {
        return switch (isolation) {
            case NONE -> new NonePositionHistoryIsolationKey();
            case ACTIVE_APP -> new ActiveAppPositionHistoryIsolationKey(
                    activeAppFinder.activeApp());
        };
    }

    public void saveCurrentPosition(String positionHistoryName) {
        positionHistory(positionHistoryName).save(new Point(mouseX, mouseY));
    }

    public void unsaveCurrentPosition(String positionHistoryName) {
        positionHistory(positionHistoryName).unsave(new Point(mouseX, mouseY));
    }

    public void clearPositionHistory(String positionHistoryName) {
        positionHistory(positionHistoryName).clear();
    }

    public void cyclePosition(String positionHistoryName, int offset) {
        Point position =
                positionHistory(positionHistoryName).cycle(offset, mouseX, mouseY);
        if (position != null)
            mouseManager.moveTo((int) Math.round(position.x()),
                    (int) Math.round(position.y()));
    }

}
