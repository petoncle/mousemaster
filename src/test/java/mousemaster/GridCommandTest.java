package mousemaster;

import mousemaster.platform.MouseController;
import mousemaster.platform.Overlay;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The grid commands act on a hint grid on a 1920x1080 screen. */
class GridCommandTest {

    private final List<Object> mousePositions = new ArrayList<>();
    private final List<Object> mouseMoves = new ArrayList<>();
    private double screenScale = 1;
    private ModeMap modeMap;
    private MouseManager mouseManager;
    private HintManager hintManager;

    private void start(String properties) throws IOException {
        Configuration configuration = ConfigurationParser.parse(PropertiesReader.readPropertiesFile(
                        new BufferedReader(new StringReader("""
                                idle-mode.to.grid-mode=+g
                                grid-mode.to.idle-mode=+esc
                                grid-mode.mouse.smooth-jump-enabled=false
                                grid-mode.hint.grid-cell-sizing=fit
                                """ + properties))),
                KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty"));
        modeMap = configuration.modeMap();
        ScreenManager screenManager = new ScreenManager(
                () -> Set.of(new Screen(new Rectangle(0, 0, 1920, 1080), 96, screenScale)));
        mouseManager = new MouseManager(screenManager,
                (MouseController) Proxy.newProxyInstance(
                        MouseController.class.getClassLoader(),
                        new Class<?>[] {MouseController.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("synchronousMoveTo"))
                                mousePositions.addAll(List.of(args));
                            if (method.getName().equals("moveBy"))
                                mouseMoves.addAll(List.of(args));
                            return null;
                        }));
        hintManager = new HintManager(configuration.positionHistoryConfigurationByName(),
                screenManager, mouseManager,
                (Overlay) Proxy.newProxyInstance(Overlay.class.getClassLoader(),
                        new Class<?>[] {Overlay.class}, (proxy, method, args) -> null),
                null, () -> new App("test.exe"), new KeyRedactor(KeyRedaction.NONE), null);
    }

    private void switchMode(String modeName) {
        Mode mode = modeMap.get(modeName);
        mouseManager.modeChanged(mode);
        hintManager.modeChanged(mode);
    }

    @Test
    void shrinkingKeepsTheHalfInTheDirection() throws IOException {
        start("""
                grid-mode.hint.grid-max-row-count=2
                grid-mode.hint.grid-max-column-count=2
                grid-mode.hint.mouse-movement=mouse-follows-overlay-center
                """);
        switchMode("grid-mode");
        hintManager.shrinkGridUp();
        hintManager.shrinkGridLeft();
        assertEquals(List.of(960, 540, 960, 270, 480, 270), mousePositions);
    }

    @Test
    void shrinkingHalvesTheGridWhateverItsNumberOfCells() throws IOException {
        start("""
                grid-mode.hint.grid-max-row-count=3
                grid-mode.hint.grid-max-column-count=3
                grid-mode.hint.mouse-movement=mouse-follows-overlay-center
                """);
        switchMode("grid-mode");
        hintManager.shrinkGridUp();
        hintManager.shrinkGridLeft();
        assertEquals(List.of(960, 540, 960, 270, 480, 270), mousePositions);
    }

    @Test
    void movingStopsWhenTheGridCenterLeavesTheScreens() throws IOException {
        start("""
                grid-mode.hint.area-width-percent=0.5
                grid-mode.hint.area-height-percent=0.5
                grid-mode.hint.grid-max-row-count=1
                grid-mode.hint.grid-max-column-count=1
                grid-mode.hint.mouse-movement=mouse-follows-overlay-center
                """);
        switchMode("grid-mode");
        hintManager.moveGridRight();
        hintManager.moveGridRight();
        assertEquals(List.of(960, 540, 1920, 540), mousePositions);
    }

    @Test
    void snappingInsideTheGridGoesToTheNextCellEdge() throws IOException {
        start("""
                grid-mode.hint.grid-max-row-count=2
                grid-mode.hint.grid-max-column-count=2
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("grid-mode");
        hintManager.snapTargetRight();
        hintManager.snapTargetRight();
        hintManager.snapTargetLeft();
        hintManager.mouseMoved(961, 100);
        hintManager.snapTargetLeft();
        assertEquals(List.of(960, 100, 1919, 100, 960, 100, 0, 100), mousePositions);
    }

    @Test
    void snappingStopsOnTheLastPixelOfTheGrid() throws IOException {
        start("""
                grid-mode.hint.area-width-percent=0.5
                grid-mode.hint.area-height-percent=0.5
                grid-mode.hint.grid-max-row-count=1
                grid-mode.hint.grid-max-column-count=1
                """);
        hintManager.mouseMoved(960, 540);
        switchMode("grid-mode");
        hintManager.snapTargetRight();
        hintManager.snapTargetDown();
        hintManager.snapTargetLeft();
        hintManager.snapTargetUp();
        assertEquals(List.of(1439, 540, 1439, 809, 480, 809, 480, 270), mousePositions);
    }

    @Test
    void snappingGoesToTheEdgesOfTheCellsTheGridShows() throws IOException {
        start("""
                idle-mode.to.fixed-grid-mode=+f
                fixed-grid-mode.to.idle-mode=+esc
                fixed-grid-mode.mouse.smooth-jump-enabled=false
                fixed-grid-mode.hint.grid-cell-width=100
                fixed-grid-mode.hint.grid-cell-height=100
                fixed-grid-mode.hint.grid-max-row-count=3
                fixed-grid-mode.hint.grid-max-column-count=3
                """);
        hintManager.mouseMoved(820, 400);
        switchMode("fixed-grid-mode");
        hintManager.snapTargetRight();
        assertEquals(List.of(910, 400), mousePositions);
    }

    @Test
    void snappingOutsideTheGridGoesToItsNearestEdge() throws IOException {
        start("""
                grid-mode.hint.area-width-percent=0.5
                grid-mode.hint.area-height-percent=0.5
                grid-mode.hint.grid-max-row-count=1
                grid-mode.hint.grid-max-column-count=1
                """);
        hintManager.mouseMoved(100, 400);
        switchMode("grid-mode");
        hintManager.snapTargetRight();
        assertEquals(List.of(480, 400), mousePositions);
    }

    @Test
    void aGridCenteredOnTheMouseFollowsItBetweenSnaps() throws IOException {
        start("""
                grid-mode.hint.area-width-percent=0.5
                grid-mode.hint.area-height-percent=0.5
                grid-mode.hint.area-center=mouse
                grid-mode.hint.grid-max-row-count=1
                grid-mode.hint.grid-max-column-count=1
                """);
        hintManager.mouseMoved(700, 500);
        switchMode("grid-mode");
        hintManager.snapTargetUp();
        hintManager.mouseMoved(800, 600);
        hintManager.snapTargetUp();
        assertEquals(List.of(700, 230, 800, 330), mousePositions);
    }

    @Test
    void aModeWithTheSameGridKeepsTheAreaOverride() throws IOException {
        start("""
                grid-mode.to.other-grid-mode=+o
                grid-mode.hint.grid-max-row-count=2
                grid-mode.hint.grid-max-column-count=2
                grid-mode.hint.mouse-movement=mouse-follows-overlay-center
                other-grid-mode.to.idle-mode=+esc
                other-grid-mode.mouse=grid-mode.mouse
                other-grid-mode.hint=grid-mode.hint
                other-grid-mode.hint.box-opacity=0.5
                """);
        switchMode("grid-mode");
        hintManager.shrinkGridUp();
        switchMode("other-grid-mode");
        switchMode("idle-mode");
        switchMode("grid-mode");
        assertEquals(List.of(960, 540, 960, 270, 960, 270, 960, 540), mousePositions);
    }

    @Test
    void aNegativeInsetGrowsTheArea() throws IOException {
        start("""
                grid-mode.hint.area-top-inset=-10
                """);
        switchMode("grid-mode");
        assertEquals(new Rectangle(0, -10, 1920, 1090), hintManager.hintMesh().area());
    }

    @Test
    void aSelectionDropsTheAreaOverride() throws IOException {
        start("""
                grid-mode.to.other-grid-mode=+o
                grid-mode.hint.selection-keys=a b c d
                grid-mode.hint.grid-max-row-count=2
                grid-mode.hint.grid-max-column-count=2
                other-grid-mode.to.idle-mode=+esc
                other-grid-mode.hint=grid-mode.hint
                """);
        switchMode("grid-mode");
        hintManager.shrinkGridUp();
        hintManager.selectHintKey(Key.ofName("a"));
        switchMode("other-grid-mode");
        assertEquals(new Rectangle(0, 0, 1920, 1080), hintManager.hintMesh().area());
    }

    @Test
    void withNoMovementSnappingMovesTheTargetAndNotTheMouse() throws IOException {
        start("""
                grid-mode.hint.grid-max-row-count=2
                grid-mode.hint.grid-max-column-count=2
                grid-mode.hint.mouse-movement=no-movement
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("grid-mode");
        hintManager.snapTargetRight();
        hintManager.snapTargetRight();
        assertEquals(List.of(), mousePositions);
        assertEquals(new Point(1919, 100), hintManager.targetPoint());
        hintManager.moveToTarget();
        assertEquals(List.of(1919, 100), mousePositions);
    }

    private void update(double delta) {
        mouseManager.update(delta);
        hintManager.update(delta);
    }

    @Test
    void withNoMovementAMoveMovesTheTargetAndNotTheMouse() throws IOException {
        start("""
                grid-mode.overlay.mouse-follows-target=false
                grid-mode.target.initial-velocity=1000
                grid-mode.target.max-velocity=1000
                grid-mode.target.deceleration=0
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("grid-mode");
        hintManager.startMoveTargetRight();
        update(0.1);
        assertEquals(new Point(200, 100), hintManager.targetPoint());
        hintManager.stopMoveTargetRight();
        update(0.1);
        assertEquals(new Point(200, 100), hintManager.targetPoint());
        assertEquals(List.of(), mouseMoves);
    }

    @Test
    void aMoveOfTheTargetMovesTheMouseThatFollowsIt() throws IOException {
        start("""
                grid-mode.mouse.initial-velocity=1000
                grid-mode.mouse.max-velocity=1000
                grid-mode.mouse.deceleration=0
                """);
        switchMode("grid-mode");
        hintManager.startMoveTargetRight();
        update(0.1);
        assertEquals(List.of(true, 100.0, false, 0.0), mouseMoves);
        hintManager.stopMoveTargetRight();
        update(0.1);
        assertEquals(List.of(true, 100.0, false, 0.0), mouseMoves);
    }

    @Test
    void aStopEndsTheMoveOfTheMouseAfterTheMouseStopsFollowing() throws IOException {
        start("""
                grid-mode.mouse.deceleration=0
                grid-mode.to.other-grid-mode=+o
                other-grid-mode.to.idle-mode=+esc
                other-grid-mode.overlay.mouse-follows-target=false
                """);
        switchMode("grid-mode");
        hintManager.startMoveTargetRight();
        switchMode("other-grid-mode");
        hintManager.stopMoveTargetRight();
        update(0.1);
        assertEquals(List.of(), mouseMoves);
    }

    @Test
    void aMoveOfTheTargetStopsWhenTheMouseFollowsTheTarget() throws IOException {
        start("""
                grid-mode.overlay.mouse-follows-target=false
                grid-mode.target.initial-velocity=1000
                grid-mode.target.max-velocity=1000
                grid-mode.to.other-grid-mode=+o
                other-grid-mode.to.idle-mode=+esc
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("grid-mode");
        hintManager.startMoveTargetRight();
        switchMode("other-grid-mode");
        switchMode("grid-mode");
        update(0.1);
        assertEquals(new Point(100, 100), hintManager.targetPoint());
    }

    @Test
    void aMoveOfTheTargetIsScaledAsAMoveOfTheMouseIs() throws IOException {
        screenScale = 3;
        start("""
                grid-mode.overlay.mouse-follows-target=false
                grid-mode.target.initial-velocity=100
                grid-mode.target.max-velocity=100
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("grid-mode");
        hintManager.startMoveTargetRight();
        update(0.1);
        assertEquals(new Point(130, 100), hintManager.targetPoint());
    }

    @Test
    void aSmoothJumpIsScaledAsAMoveIs() throws IOException {
        screenScale = 3;
        start("idle-mode.mouse.smooth-jump-velocity=1000\n");
        switchMode("idle-mode");
        mouseManager.mouseMoved(100, 100);
        mouseManager.moveTo(400, 100);
        mouseManager.update(0.1);
        assertEquals(List.of(400, 100), mousePositions);
    }

    @Test
    void theTargetHasTheSpeedKeysOfTheMouse() throws IOException {
        start("grid-mode.target.max-velocity=500\n");
        assertEquals(500, modeMap.get("grid-mode").target().maxVelocity());
        assertEquals(modeMap.get("idle-mode").mouse().velocity(),
                modeMap.get("idle-mode").target());
        assertThrows(IllegalArgumentException.class,
                () -> start("grid-mode.target.smooth-jump-enabled=true\n"));
    }

    private List<Command> commands(String modeName) {
        return modeMap.get(modeName).comboMap().commandsByCombo().values().stream()
                      .flatMap(List::stream).toList();
    }

    @Test
    void theFormerNamesOfTheGridCommandsStillWork() throws IOException {
        start("""
                grid-mode.snap.up=+u
                grid-mode.move-to-grid-center=+c
                grid-mode.move-to-last-selected-hint=+m
                grid-mode.to.other-grid-mode=+o
                other-grid-mode.to.idle-mode=+esc
                other-grid-mode.snap=grid-mode.snap
                other-grid-mode.move-to-grid-center=grid-mode.move-to-grid-center
                other-grid-mode.move-to-last-selected-hint=grid-mode.move-to-last-selected-hint
                """);
        List<Command> commands = List.of(new Command.SnapTargetUp(),
                new Command.MoveTargetToGridCenter(), new Command.MoveToLastSelectedHint());
        assertTrue(commands("grid-mode").containsAll(commands));
        assertTrue(commands("other-grid-mode").containsAll(commands));
    }

    @Test
    void withNoMovementAPositionHistorySavesAndCyclesTheTarget() throws IOException {
        start("""
                grid-mode.hint.grid-max-row-count=2
                grid-mode.hint.grid-max-column-count=2
                grid-mode.hint.mouse-movement=no-movement
                grid-mode.save.position-history=+s
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("grid-mode");
        hintManager.snapTargetRight();
        hintManager.saveCurrentPosition("position-history");
        hintManager.snapTargetRight();
        hintManager.saveCurrentPosition("position-history");
        assertEquals(List.of(new Point(960, 100), new Point(1919, 100)),
                hintManager.positionHistory("position-history").positions());
        hintManager.cyclePosition("position-history", 1);
        assertEquals(new Point(960, 100), hintManager.targetPoint());
        assertEquals(List.of(), mousePositions);
    }

    @Test
    void anOverlayShowsTheSavedPositionsOfItsSavedPositionHistory() throws IOException {
        start("""
                grid-mode.hint.saved-position-history=position-history
                grid-mode.hint.saved-box-color=#FF0000
                grid-mode.save.position-history=+s
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("grid-mode");
        assertEquals(List.of(), hintManager.hintMesh().savedPositions());
        hintManager.saveCurrentPosition("position-history");
        assertEquals(List.of(new Point(100, 100)), hintManager.hintMesh().savedPositions());
        hintManager.unsaveCurrentPosition("position-history");
        assertEquals(List.of(), hintManager.hintMesh().savedPositions());
        assertEquals(GradientColor.parse("#FF0000"), modeMap.get("grid-mode").hintMesh()
                .styleByFilter().get(ScreenFilter.AnyScreenFilter.ANY_SCREEN_FILTER).savedBoxColor());
    }

    @Test
    void aSavedFontInheritsFromTheFont() throws IOException {
        start("""
                grid-mode.hint.font-size=20
                grid-mode.hint.selected-font-color=#0000FF
                grid-mode.hint.saved-font-color=#00FF00
                """);
        HintFontStyle fontStyle = modeMap.get("grid-mode").hintMesh().styleByFilter()
                .get(ScreenFilter.AnyScreenFilter.ANY_SCREEN_FILTER).saved().fontStyle();
        assertEquals(Color.parse("#00FF00"), fontStyle.defaultFontStyle().color());
        assertEquals(20, fontStyle.defaultFontStyle().size());
        assertEquals(Color.parse("#00FF00"), fontStyle.savedFontStyle().color());
        assertEquals(Color.parse("#00FF00"), fontStyle.focusedFontStyle().color());
        assertEquals(Color.parse("#00FF00"), fontStyle.selectedFontStyle().color());
    }

    @Test
    void aFontMutationReachesTheSavedFont() throws IOException {
        start("""
                grid-mode.hint.font-color=#FFFFFF | _{isidling} -> #FF0000
                """);
        Mode mode = modeMap.get("grid-mode");
        for (List<Command> commands : mode.comboMap().commandsByCombo().values())
            for (Command command : commands)
                if (command instanceof Command.MutateMode mutateMode)
                    mode = mode.mutate(mutateMode.propertyPath(), mutateMode.newPropertyValue());
        assertEquals(Color.parse("#FF0000"), mode.hintMesh().styleByFilter()
                .get(ScreenFilter.AnyScreenFilter.ANY_SCREEN_FILTER).fontStyle().savedFontStyle().color());
    }

    @Test
    void aSavedPositionHistoryMustBeDefined() {
        assertThrows(IllegalArgumentException.class, () -> start("""
                grid-mode.hint.saved-position-history=drag-position-history
                """));
    }

    @Test
    void theTargetCanFollowTheOverlayCenterWithoutTheMouse() throws IOException {
        start("""
                grid-mode.hint.grid-max-row-count=2
                grid-mode.hint.grid-max-column-count=2
                grid-mode.hint.mouse-follows-target=false
                grid-mode.hint.target-follows-overlay-center=true
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("grid-mode");
        assertEquals(new Point(960, 540), hintManager.targetPoint());
        assertEquals(List.of(), mousePositions);
    }

    @Test
    void theTargetFollowsTheMouseUntilNoMovementStopsIt() throws IOException {
        start("""
                grid-mode.to.still-mode=+s
                still-mode.to.idle-mode=+esc
                still-mode.hint=grid-mode.hint
                still-mode.hint.mouse-movement=no-movement
                """);
        switchMode("grid-mode");
        hintManager.mouseMoved(100, 100);
        assertEquals(new Point(100, 100), hintManager.targetPoint());
        switchMode("still-mode");
        hintManager.mouseMoved(300, 300);
        assertEquals(new Point(100, 100), hintManager.targetPoint());
    }

    @Test
    void theTargetStaysWhereItIsFromOneNoMovementModeToAnother() throws IOException {
        start("""
                grid-mode.hint.grid-max-row-count=2
                grid-mode.hint.grid-max-column-count=2
                grid-mode.hint.mouse-movement=no-movement
                grid-mode.to.other-grid-mode=+o
                other-grid-mode.to.idle-mode=+esc
                other-grid-mode.hint=grid-mode.hint
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("grid-mode");
        hintManager.snapTargetRight();
        switchMode("other-grid-mode");
        assertEquals(new Point(960, 100), hintManager.targetPoint());
    }

    @Test
    void aGridOnTheLastOverlayAreaFillsTheShrunkGrid() throws IOException {
        start("""
                grid-mode.to.hint-mode=+h
                grid-mode.hint.grid-max-row-count=2
                grid-mode.hint.grid-max-column-count=2
                hint-mode.to.idle-mode=+esc
                hint-mode.hint.area=last-overlay-area
                hint-mode.hint.selection-keys=a b c d
                hint-mode.hint.grid-cell-sizing=fit
                hint-mode.hint.grid-max-row-count=4
                hint-mode.hint.grid-max-column-count=4
                """);
        switchMode("grid-mode");
        hintManager.shrinkGridUp();
        switchMode("hint-mode");
        assertEquals(new Rectangle(0, 0, 1920, 540), hintManager.hintMesh().area());
    }

    @Test
    void theLastOverlayAreaIsTheCellsTheSelectedKeysLeave() throws IOException {
        start("""
                grid-mode.to.hint-mode=+h
                grid-mode.hint.selection-keys=a b
                grid-mode.hint.grid-max-row-count=2
                grid-mode.hint.grid-max-column-count=2
                hint-mode.to.idle-mode=+esc
                hint-mode.hint.area=last-overlay-area
                hint-mode.hint.grid-cell-sizing=fit
                hint-mode.hint.grid-max-row-count=1
                hint-mode.hint.grid-max-column-count=1
                """);
        switchMode("grid-mode");
        hintManager.selectHintKey(Key.ofName("a"));
        List<Hint> leftHints = hintManager.hintMesh()
                                          .hints()
                                          .stream()
                                          .filter(hint -> hint.keySequence().getFirst()
                                                              .equals(Key.ofName("a")))
                                          .toList();
        switchMode("hint-mode");
        assertEquals(2, leftHints.size());
        assertEquals(new Rectangle(0, 0, 960, 1080), hintManager.hintMesh().area());
    }

    @Test
    void aModeWithoutAHintGridIgnoresTheGridCommands() throws IOException {
        start("");
        switchMode("idle-mode");
        hintManager.shrinkGridUp();
        hintManager.moveGridUp();
        hintManager.moveTargetToGridCenter();
        hintManager.snapTargetUp();
        assertEquals(List.of(), mousePositions);
    }

}
