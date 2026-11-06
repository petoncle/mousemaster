package mousemaster;

import mousemaster.platform.MouseController;
import mousemaster.platform.Overlay;
import mousemaster.platform.UiAutomation;
import mousemaster.platform.UiAutomation.UiElement;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The grid commands act on a hint grid on a 1920x1080 screen. */
class GridCommandTest {

    private final List<Object> mousePositions = new ArrayList<>();
    private final List<Object> mouseMoves = new ArrayList<>();
    private HintMesh shownHintMesh;
    private boolean shownHintMeshAnimated;
    private double screenScale = 1;
    private List<UiElement> uiElements = List.of();
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
                        new Class<?>[] {Overlay.class}, (proxy, method, args) -> {
                            if (method.getName().equals("setHintMesh")) {
                                shownHintMesh = (HintMesh) args[0];
                                shownHintMeshAnimated = (boolean) args[2];
                            }
                            return null;
                        }),
                new UiAutomation() {
                    @Override
                    public Future<List<UiElement>> startFindActiveWindowUiElements() {
                        return CompletableFuture.completedFuture(uiElements);
                    }

                    @Override
                    public Future<List<UiElement>> startFindUiElementsInArea(Rectangle area) {
                        return CompletableFuture.completedFuture(uiElements);
                    }
                }, () -> new App("test.exe"), new KeyRedactor(KeyRedaction.NONE), null);
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
        hintManager.shrinkOverlayUp();
        hintManager.shrinkOverlayLeft();
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
        hintManager.shrinkOverlayUp();
        hintManager.shrinkOverlayLeft();
        assertEquals(List.of(960, 540, 960, 270, 480, 270), mousePositions);
    }

    @Test
    void movingTheGridStopsWhenItsCenterLeavesTheScreens() throws IOException {
        start("""
                grid-mode.hint.area-width-percent=0.5
                grid-mode.hint.area-height-percent=0.5
                grid-mode.hint.grid-max-row-count=1
                grid-mode.hint.grid-max-column-count=1
                grid-mode.hint.mouse-movement=mouse-follows-overlay-center
                """);
        switchMode("grid-mode");
        hintManager.moveOverlayToAreaRight();
        hintManager.moveOverlayToAreaRight();
        assertEquals(List.of(960, 540, 1920, 540), mousePositions);
    }

    @Test
    void theTargetGoesToTheNextCellEdgeInsideTheGrid() throws IOException {
        start("""
                grid-mode.hint.grid-max-row-count=2
                grid-mode.hint.grid-max-column-count=2
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("grid-mode");
        hintManager.moveTargetToCellRight();
        hintManager.moveTargetToCellRight();
        hintManager.moveTargetToCellLeft();
        hintManager.mouseMoved(961, 100);
        hintManager.moveTargetToCellLeft();
        assertEquals(List.of(959, 100, 1919, 100, 960, 100, 0, 100), mousePositions);
    }

    @Test
    void theTargetStopsOnTheLastPixelOfTheGrid() throws IOException {
        start("""
                grid-mode.hint.area-width-percent=0.5
                grid-mode.hint.area-height-percent=0.5
                grid-mode.hint.grid-max-row-count=1
                grid-mode.hint.grid-max-column-count=1
                """);
        hintManager.mouseMoved(960, 540);
        switchMode("grid-mode");
        hintManager.moveTargetToCellRight();
        hintManager.moveTargetToCellBottom();
        hintManager.moveTargetToCellLeft();
        hintManager.moveTargetToCellTop();
        assertEquals(List.of(1439, 540, 1439, 809, 480, 809, 480, 270), mousePositions);
    }

    @Test
    void theTargetGoesToTheEdgesOfTheCellsTheGridShows() throws IOException {
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
        hintManager.moveTargetToCellRight();
        assertEquals(List.of(909, 400), mousePositions);
    }

    @Test
    void outsideTheGridTheTargetGoesToTheNearestPointOfTheEdge() throws IOException {
        start("""
                grid-mode.hint.area-width-percent=0.5
                grid-mode.hint.area-height-percent=0.5
                grid-mode.hint.grid-max-row-count=1
                grid-mode.hint.grid-max-column-count=1
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("grid-mode");
        hintManager.moveTargetToCellRight();
        assertEquals(List.of(1439, 270), mousePositions);
    }

    @Test
    void theTargetGoesToTheCenterOfTheNearestCell() throws IOException {
        start("""
                grid-mode.hint.area-width-percent=0.5
                grid-mode.hint.area-height-percent=0.5
                grid-mode.hint.grid-max-row-count=2
                grid-mode.hint.grid-max-column-count=2
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("grid-mode");
        hintManager.moveTargetToCellCenter();
        hintManager.mouseMoved(1000, 600);
        hintManager.moveTargetToCellCenter();
        assertEquals(List.of(720, 405, 1200, 675), mousePositions);
    }

    @Test
    void shrinkingSavedPositionsKeepsThePositionsInTheHalf() throws IOException {
        start("""
                idle-mode.to.saved-mode=+s
                idle-mode.save.position-history=+f1
                saved-mode.to.idle-mode=+esc
                saved-mode.overlay.type=position-history
                saved-mode.overlay.selection-keys=a b c d
                """);
        switchMode("idle-mode");
        hintManager.mouseMoved(100, 100);
        hintManager.saveCurrentPosition("position-history");
        hintManager.mouseMoved(1500, 100);
        hintManager.saveCurrentPosition("position-history");
        switchMode("saved-mode");
        assertEquals(2, hintManager.hintMesh().hints().size());
        hintManager.shrinkOverlayLeft();
        assertEquals(List.of(100.0),
                hintManager.hintMesh().hints().stream().map(Hint::centerX).toList());
    }

    @Test
    void betweenUiHintsTheTargetGoesToTheNearestElement() throws IOException {
        uiElements = List.of(new UiElement(new Rectangle(100, 100, 50, 20)),
                new UiElement(new Rectangle(400, 100, 50, 20)));
        start("""
                idle-mode.to.ui-hint-mode=+u
                ui-hint-mode.to.idle-mode=+esc
                ui-hint-mode.mouse.smooth-jump-enabled=false
                ui-hint-mode.overlay.type=ui-accessibility
                ui-hint-mode.overlay.selection-keys=a b c d
                """);
        hintManager.mouseMoved(200, 300);
        switchMode("ui-hint-mode");
        hintManager.completePendingUiHintQuery();
        hintManager.moveTargetToCellCenter();
        hintManager.moveTargetToCellRight();
        assertEquals(List.of(125, 110, 149, 110), mousePositions);
    }

    @Test
    void inAUiHintTheTargetGoesToTheEdgeOfItsElement() throws IOException {
        uiElements = List.of(new UiElement(new Rectangle(100, 500, 300, 40)),
                new UiElement(new Rectangle(230, 560, 70, 40)));
        start("""
                idle-mode.to.ui-hint-mode=+u
                ui-hint-mode.to.idle-mode=+esc
                ui-hint-mode.mouse.smooth-jump-enabled=false
                ui-hint-mode.overlay.type=ui-accessibility
                ui-hint-mode.overlay.selection-keys=a b c d
                """);
        hintManager.mouseMoved(250, 520);
        switchMode("ui-hint-mode");
        hintManager.completePendingUiHintQuery();
        hintManager.moveTargetToCellLeft();
        hintManager.mouseMoved(250, 520);
        hintManager.moveTargetToCellRight();
        assertEquals(List.of(100, 520, 399, 520), mousePositions);
    }

    @Test
    void aGridCenteredOnTheMouseFollowsItBetweenTargetMoves() throws IOException {
        start("""
                grid-mode.hint.area-width-percent=0.5
                grid-mode.hint.area-height-percent=0.5
                grid-mode.hint.area-center=mouse
                grid-mode.hint.grid-max-row-count=1
                grid-mode.hint.grid-max-column-count=1
                """);
        hintManager.mouseMoved(700, 500);
        switchMode("grid-mode");
        hintManager.moveTargetToCellTop();
        hintManager.mouseMoved(800, 600);
        hintManager.moveTargetToCellTop();
        assertEquals(List.of(700, 230, 800, 330), mousePositions);
    }

    @Test
    void aGridCenteredOnTheTargetFollowsItBetweenTargetMoves() throws IOException {
        start("""
                grid-mode.overlay.area-width-percent=0.5
                grid-mode.overlay.area-height-percent=0.25
                grid-mode.overlay.area-center=target
                grid-mode.overlay.mouse-follows-target=false
                grid-mode.overlay.grid-max-row-count=1
                grid-mode.overlay.grid-max-column-count=1
                """);
        hintManager.mouseMoved(700, 500);
        switchMode("grid-mode");
        hintManager.moveTargetToCellTop();
        hintManager.moveTargetToCellTop();
        assertEquals(new Point(700, 230), hintManager.targetPoint());
    }

    @Test
    void aGridCenteredOnTheTargetIsBuiltOnTheMouseItFollowsAgain() throws IOException {
        start("""
                grid-mode.to.stopped-mode=+s
                stopped-mode.overlay.mouse-follows-target=false
                grid-mode.overlay.area-width-percent=0.5
                grid-mode.overlay.area-height-percent=0.25
                grid-mode.overlay.area-center=target
                grid-mode.overlay.grid-max-row-count=1
                grid-mode.overlay.grid-max-column-count=1
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("stopped-mode");
        hintManager.mouseMoved(700, 500);
        switchMode("grid-mode");
        Hint hint = shownHintMesh.hints().getFirst();
        assertEquals(new Point(700, 500), new Point(hint.centerX(), hint.centerY()));
    }

    @Test
    void aCellCommandActsOnTheDrawnCellsOfAnAreaWithAnInset() throws IOException {
        start("""
                grid-mode.overlay.area-width-percent=0.5
                grid-mode.overlay.area-height-percent=0.5
                grid-mode.overlay.area-top-inset=100
                grid-mode.overlay.area-center=mouse
                grid-mode.overlay.grid-max-row-count=1
                grid-mode.overlay.grid-max-column-count=1
                """);
        hintManager.mouseMoved(700, 500);
        switchMode("grid-mode");
        hintManager.moveToCellCenter();
        hintManager.mouseMoved(800, 600);
        hintManager.moveToCellCenter();
        assertEquals(List.of(700, 550, 800, 650), mousePositions);
    }

    @Test
    void aGridCenteredOnTheMouseFollowsItWithoutATransition() throws IOException {
        start("""
                grid-mode.overlay.area-width-percent=0.5
                grid-mode.overlay.area-height-percent=0.25
                grid-mode.overlay.area-center=mouse
                grid-mode.overlay.grid-max-row-count=1
                grid-mode.overlay.grid-max-column-count=1
                """);
        hintManager.mouseMoved(700, 500);
        switchMode("grid-mode");
        assertTrue(shownHintMeshAnimated);
        hintManager.mouseMoved(800, 600);
        update(0);
        Hint hint = shownHintMesh.hints().getFirst();
        assertEquals(new Point(800, 600), new Point(hint.centerX(), hint.centerY()));
        assertFalse(shownHintMeshAnimated);
    }

    @Test
    void aGridThatFollowsTheTargetDoesNotSendItToItsCenter() throws IOException {
        start("""
                grid-mode.overlay.area-width-percent=0.5
                grid-mode.overlay.area-height-percent=0.5
                grid-mode.overlay.area-top-inset=100
                grid-mode.overlay.area-center=mouse
                grid-mode.overlay.target-follows-overlay-center=true
                grid-mode.overlay.grid-max-row-count=1
                grid-mode.overlay.grid-max-column-count=1
                """);
        hintManager.mouseMoved(700, 500);
        switchMode("grid-mode");
        hintManager.mouseMoved(710, 500);
        update(0);
        Hint hint = shownHintMesh.hints().getFirst();
        assertEquals(new Point(710, 550), new Point(hint.centerX(), hint.centerY()));
        assertEquals(List.of(), mousePositions);
    }

    @Test
    void aGridCenteredOnTheTargetDoesNotStopTheTargetMove() throws IOException {
        start("""
                grid-mode.stop-commands-from-previous-mode=true
                grid-mode.overlay.mouse-follows-target=false
                grid-mode.overlay.area-center=target
                grid-mode.target.initial-velocity=1000
                grid-mode.target.max-velocity=1000
                grid-mode.target.deceleration=0
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("grid-mode");
        hintManager.startMoveTargetRight();
        update(0.1);
        update(0.1);
        assertEquals(new Point(300, 100), hintManager.targetPoint());
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
        hintManager.shrinkOverlayUp();
        switchMode("other-grid-mode");
        switchMode("idle-mode");
        switchMode("grid-mode");
        assertEquals(List.of(960, 540, 960, 270, 960, 540), mousePositions);
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
        hintManager.shrinkOverlayUp();
        hintManager.selectHintKey(Key.ofName("a"));
        switchMode("other-grid-mode");
        assertEquals(new Rectangle(0, 0, 1920, 1080), hintManager.hintMesh().area());
    }

    @Test
    void withNoMovementACellEdgeMovesTheTargetAndNotTheMouse() throws IOException {
        start("""
                grid-mode.hint.grid-max-row-count=2
                grid-mode.hint.grid-max-column-count=2
                grid-mode.hint.mouse-movement=no-movement
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("grid-mode");
        hintManager.moveTargetToCellRight();
        hintManager.moveTargetToCellRight();
        assertEquals(List.of(), mousePositions);
        assertEquals(new Point(1919, 100), hintManager.targetPoint());
        hintManager.moveToTarget();
        assertEquals(List.of(1919, 100), mousePositions);
    }

    @Test
    void withNoMovementACellEdgeMovesTheMouseAndNotTheTarget() throws IOException {
        start("""
                grid-mode.hint.grid-max-row-count=2
                grid-mode.hint.grid-max-column-count=2
                grid-mode.hint.mouse-movement=no-movement
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("grid-mode");
        hintManager.moveToCellRight();
        hintManager.moveToOverlayCenter();
        assertEquals(List.of(959, 100, 960, 540), mousePositions);
        assertEquals(new Point(100, 100), hintManager.targetPoint());
    }

    @Test
    void withNoMovementTheTargetGoesToTheMouseAndToTheLastSelectedHint() throws IOException {
        start("""
                grid-mode.hint.selection-keys=a b c d
                grid-mode.hint.grid-max-row-count=2
                grid-mode.hint.grid-max-column-count=2
                grid-mode.hint.mouse-movement=no-movement
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("grid-mode");
        hintManager.selectHintKey(Key.ofName("a"));
        hintManager.moveTargetToMouse();
        assertEquals(new Point(100, 100), hintManager.targetPoint());
        hintManager.moveTargetToLastSelectedHint();
        assertEquals(new Point(480, 270), hintManager.targetPoint());
        assertEquals(List.of(), mousePositions);
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
                grid-mode.move-grid.up=+p
                grid-mode.shrink-grid.up=+k
                grid-mode.move-to-grid-center=+c
                grid-mode.move-to-last-selected-hint=+m
                grid-mode.to.other-grid-mode=+o
                other-grid-mode.to.idle-mode=+esc
                other-grid-mode.snap=grid-mode.snap
                other-grid-mode.move-grid=grid-mode.move-grid
                other-grid-mode.shrink-grid=grid-mode.shrink-grid
                other-grid-mode.move-to-grid-center=grid-mode.move-to-grid-center
                other-grid-mode.move-to-last-selected-hint=grid-mode.move-to-last-selected-hint
                """);
        List<Command> commands = List.of(new Command.MoveToCellTop(),
                new Command.MoveOverlayToAreaAbove(), new Command.MoveToOverlayCenter(),
                new Command.MoveToLastSelectedHint(), new Command.ShrinkOverlayUp());
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
        hintManager.moveTargetToCellRight();
        hintManager.saveCurrentPosition("position-history");
        hintManager.moveTargetToCellRight();
        hintManager.saveCurrentPosition("position-history");
        assertEquals(List.of(new Point(959, 100), new Point(1919, 100)),
                hintManager.positionHistory("position-history").positions());
        hintManager.cyclePosition("position-history", 1);
        assertEquals(new Point(959, 100), hintManager.targetPoint());
        assertEquals(List.of(), mousePositions);
    }

    @Test
    void anOverlayShowsTheHintsThePositionsOfItsSavedPositionHistoryWereSavedAt()
            throws IOException {
        start("""
                grid-mode.hint.saved-hints=position-history
                grid-mode.hint.saved-box-color=#FF0000
                grid-mode.hint.selection-keys=a b c d
                grid-mode.hint.grid-max-row-count=2
                grid-mode.hint.grid-max-column-count=2
                grid-mode.hint.mouse-movement=no-movement
                grid-mode.save.position-history=+s
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("grid-mode");
        hintManager.saveCurrentPosition("position-history");
        assertEquals(List.of(), hintManager.hintMesh().savedHints());
        hintManager.selectHintKey(Key.ofName("a"));
        hintManager.saveCurrentPosition("position-history");
        switchMode("idle-mode");
        switchMode("grid-mode");
        assertEquals(List.of(List.of(Key.ofName("a"))), hintManager.hintMesh()
                .savedHints().stream().map(Hint::keySequence).toList());
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

    private HintMeshStyle mutatedGridModeStyle() {
        Mode mode = modeMap.get("grid-mode");
        for (List<Command> commands : mode.comboMap().commandsByCombo().values())
            for (Command command : commands)
                if (command instanceof Command.MutateMode mutateMode)
                    mode = mode.mutate(mutateMode.propertyPath(), mutateMode.newPropertyValue());
        return mode.hintMesh().styleByFilter().get(ScreenFilter.AnyScreenFilter.ANY_SCREEN_FILTER);
    }

    @Test
    void aFontMutationReachesTheSavedFont() throws IOException {
        start("""
                grid-mode.hint.font-color=#FFFFFF | _{isidling} -> #FF0000
                """);
        assertEquals(Color.parse("#FF0000"),
                mutatedGridModeStyle().fontStyle().savedFontStyle().color());
    }

    @Test
    void aFontMutationKeepsTheFocusedFontAnExtendedModeSets() throws IOException {
        start("""
                _base-mode.hint.focused-font-opacity=1
                _base-mode.hint.font-opacity=1 | _{isidling} -> 0.5
                grid-mode=_base-mode
                """);
        HintMeshStyle style = mutatedGridModeStyle();
        assertEquals(0.5, style.fontStyle().defaultFontStyle().opacity());
        assertEquals(1, style.fontStyle().focusedFontStyle().opacity());
        assertEquals(0.5, style.fontStyle().selectedFontStyle().opacity());
        assertEquals(0.5, style.prefixFontStyle().defaultFontStyle().opacity());
        assertEquals(1, style.prefixFontStyle().focusedFontStyle().opacity());
    }

    @Test
    void aFontMutationReachesTheFocusedFontOnlyAnExtendedModeSets() throws IOException {
        start("""
                _base-mode.hint.focused-font-opacity=1
                grid-mode=_base-mode
                grid-mode.hint.font-opacity=1 | _{isidling} -> 0.5
                """);
        HintMeshStyle style = mutatedGridModeStyle();
        assertEquals(0.5, style.fontStyle().focusedFontStyle().opacity());
        assertEquals(0.5, style.prefixFontStyle().focusedFontStyle().opacity());
    }

    @Test
    void aFocusedFontMutationReachesThePrefixFocusedFont() throws IOException {
        start("""
                grid-mode.hint.focused-font-opacity=1 | _{isidling} -> 0.5
                """);
        HintMeshStyle style = mutatedGridModeStyle();
        assertEquals(0.5, style.prefixFontStyle().focusedFontStyle().opacity());
        assertEquals(1, style.prefixFontStyle().defaultFontStyle().opacity());
    }

    @Test
    void anInheritedFocusedFontMutationSkipsAModeThatSetsTheFont() throws IOException {
        start("""
                _base-mode.hint.focused-font-opacity=1 | _{isidling} -> 0.5
                grid-mode=_base-mode
                grid-mode.hint.font-opacity=0.8
                """);
        assertEquals(0.8, mutatedGridModeStyle().fontStyle().focusedFontStyle().opacity());
    }

    @Test
    void aSavedPositionHistoryMustBeDefined() {
        assertThrows(IllegalArgumentException.class, () -> start("""
                grid-mode.hint.saved-hints=drag-position-history
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
        hintManager.moveTargetToCellRight();
        switchMode("other-grid-mode");
        assertEquals(new Point(959, 100), hintManager.targetPoint());
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
        hintManager.shrinkOverlayUp();
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
        hintManager.shrinkOverlayUp();
        hintManager.moveOverlayToAreaAbove();
        hintManager.moveTargetToOverlayCenter();
        hintManager.moveTargetToCellTop();
        assertEquals(List.of(), mousePositions);
    }

}
