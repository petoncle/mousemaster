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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hint a is on the element at (100, 100, 200, 50), hint b on the one at (500, 500, 100, 100). */
class HintPointTest {

    private final List<Object> mousePositions = new ArrayList<>();
    private final List<HintMesh> shownHintMeshes = new ArrayList<>();
    private HintMesh shownHintMesh;
    private double screenScale = 1;
    private List<Rectangle> screenRectangles = List.of(new Rectangle(0, 0, 1920, 1080));
    private List<UiElement> uiElements = List.of(new UiElement(new Rectangle(100, 100, 200, 50)),
            new UiElement(new Rectangle(500, 500, 100, 100)));
    private Configuration configuration;
    private ModeMap modeMap;
    private MouseManager mouseManager;
    private HintManager hintManager;
    private ComboWatcher comboWatcher;
    private Instant now = Instant.parse("2026-01-01T00:00:00Z");

    private void start(String properties) throws IOException {
        configuration = ConfigurationParser.parse(PropertiesReader.readPropertiesFile(
                        new BufferedReader(new StringReader("""
                                idle-mode.to.hint-mode=+h
                                hint-mode.to.idle-mode=+esc
                                hint-mode.to.pick-mode=+p
                                hint-mode.mouse.smooth-jump-enabled=false
                                hint-mode.overlay.type=ui-accessibility
                                hint-mode.overlay.selection-keys=a b
                                pick-mode.to.hint-mode=+h
                                pick-mode.overlay=hint-mode.overlay
                                pick-mode.mouse=hint-mode.mouse
                                """ + properties))),
                KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty"));
        modeMap = configuration.modeMap();
        ScreenManager screenManager = new ScreenManager(() -> screenRectangles.stream()
                .map(screenRectangle -> new Screen(screenRectangle, 96, screenScale))
                .collect(Collectors.toSet()));
        mouseManager = new MouseManager(screenManager,
                (MouseController) Proxy.newProxyInstance(
                        MouseController.class.getClassLoader(),
                        new Class<?>[] {MouseController.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("synchronousMoveTo"))
                                mousePositions.addAll(List.of(args));
                            return null;
                        }));
        hintManager = new HintManager(configuration.positionHistoryConfigurationByName(),
                screenManager, mouseManager,
                (Overlay) Proxy.newProxyInstance(Overlay.class.getClassLoader(),
                        new Class<?>[] {Overlay.class}, (proxy, method, args) -> {
                            if (method.getName().equals("setHintMesh")) {
                                shownHintMesh = (HintMesh) args[0];
                                shownHintMeshes.add(shownHintMesh);
                            }
                            return method.getReturnType() == boolean.class ?
                                    method.getName().equals("canZoom") : null;
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
        hintManager.completePendingUiHintQuery();
    }

    private void select(String keyName) {
        hintManager.selectHintKey(Key.ofName(keyName));
    }

    private Point shownPoint(String keyName) {
        return shownHintMesh.hints()
                            .stream()
                            .filter(hint -> hint.keySequence().equals(List.of(Key.ofName(keyName))))
                            .map(shownHintMesh::point)
                            .findFirst()
                            .orElseThrow();
    }

    @Test
    void aSelectionPutsTheTargetOnTheHintPoint() throws IOException {
        start("");
        switchMode("hint-mode");
        assertEquals(Map.of(), shownHintMesh.pointByKeySequence());
        select("a");
        assertEquals(List.of(200, 125), mousePositions);
        assertEquals(new Point(200, 125), hintManager.lastSelectedHintPoint());
    }

    @Test
    void theHintPointsGoToTheEdgesOfTheirOwnCells() throws IOException {
        start("""
                hint-mode.hint-points.cell-x-percent=0.5 | +q -> 0 | +w -> 1
                hint-mode.hint-points.cell-y-percent=0.5 | +e -> 0 | +r -> 1
                """);
        startComboWatcher();
        press("h");
        press("q");
        assertEquals(new Point(100, 125), shownPoint("a"));
        assertEquals(new Point(500, 550), shownPoint("b"));
        press("e");
        assertEquals(new Point(100, 100), shownPoint("a"));
        assertEquals(new Point(500, 500), shownPoint("b"));
        press("w");
        assertEquals(new Point(299, 100), shownPoint("a"));
        assertEquals(new Point(599, 500), shownPoint("b"));
        press("r");
        assertEquals(new Point(299, 149), shownPoint("a"));
        assertEquals(new Point(599, 599), shownPoint("b"));
        select("b");
        assertEquals(List.of(599, 599), mousePositions);
        assertEquals(new Point(599, 599), hintManager.lastSelectedHintPoint());
    }

    @Test
    void theHintPointsAreAtThePercentsOfTheirCells() throws IOException {
        start("""
                hint-mode.hint-points.cell-x-percent=0.25
                hint-mode.hint-points.cell-y-percent=0.75
                """);
        switchMode("hint-mode");
        assertEquals(new Point(150, 138), shownPoint("a"));
        assertEquals(new Point(525, 575), shownPoint("b"));
        select("a");
        assertEquals(List.of(150, 138), mousePositions);
    }

    @Test
    void aNewPercentUndoesTheNudgesOnItsAxisOnly() throws IOException {
        start("""
                hint-mode.hint-points.nudge-distance=4
                hint-mode.hint-points.cell-x-percent=0.5 | +q -> 0 | +w -> 0.5
                hint-mode.nudge-hint-points.right=+rightarrow
                hint-mode.nudge-hint-points.down=+downarrow
                """);
        startComboWatcher();
        press("h");
        press("rightarrow");
        press("downarrow");
        assertEquals(new Point(204, 129), shownPoint("a"));
        press("q");
        assertEquals(new Point(100, 129), shownPoint("a"));
        press("rightarrow");
        press("w");
        assertEquals(new Point(200, 129), shownPoint("a"));
    }

    @Test
    void aNudgeMovesTheHintPointsByTheirNudgeDistanceAtTheScreenScale() throws IOException {
        screenScale = 2;
        start("hint-mode.hint-points.nudge-distance=4\n");
        switchMode("hint-mode");
        hintManager.nudgeHintPointsRight();
        hintManager.nudgeHintPointsRight();
        hintManager.nudgeHintPointsUp();
        assertEquals(new Point(216, 117), shownPoint("a"));
        assertFalse(hintManager.hintPointsCentered());
        hintManager.nudgeHintPointsLeft();
        hintManager.nudgeHintPointsLeft();
        hintManager.nudgeHintPointsDown();
        assertTrue(hintManager.hintPointsCentered());
    }

    @Test
    void aHintPointIsOnAWholePixel() throws IOException {
        screenScale = 1.25;
        start("hint-mode.hint-points.nudge-distance=2\n");
        switchMode("hint-mode");
        hintManager.nudgeHintPointsRight();
        assertEquals(new Point(203, 125), shownPoint("a"));
    }

    @Test
    void aModeWithoutHintsUndoesTheNudges() throws IOException {
        start("");
        switchMode("hint-mode");
        hintManager.nudgeHintPointsRight();
        assertEquals(new Point(201, 125), shownPoint("a"));
        switchMode("idle-mode");
        assertTrue(hintManager.hintPointsCentered());
        switchMode("hint-mode");
        assertEquals(Map.of(), shownHintMesh.pointByKeySequence());
    }

    @Test
    void aNudgeInAModeWithoutHintsMovesNoPoint() throws IOException {
        start("");
        switchMode("idle-mode");
        hintManager.nudgeHintPointsRight();
        switchMode("hint-mode");
        assertEquals(Map.of(), shownHintMesh.pointByKeySequence());
    }

    @Test
    void aModeWithTheSameHintPointsKeepsThem() throws IOException {
        start("""
                hint-mode.hint-points.cell-x-percent=0.5 | +q -> 0
                hint-mode.nudge-hint-points.right=+rightarrow
                pick-mode.hint-points=hint-mode.hint-points
                """);
        startComboWatcher();
        press("h");
        press("q");
        press("rightarrow");
        press("p");
        assertEquals(new Point(101, 125), shownPoint("a"));
        assertFalse(hintManager.hintPointsCentered());
    }

    @Test
    void aPickKeepsTheHintPointItWasSavedAt() throws IOException {
        start("""
                hint-mode.toggle-save.pick-position-history=+s
                hint-mode.overlay.saved-hints=pick-position-history
                hint-mode.hint-points.nudge-distance=99
                """);
        switchMode("hint-mode");
        hintManager.nudgeHintPointsRight();
        select("a");
        hintManager.saveCurrentPosition("pick-position-history");
        switchMode("pick-mode");
        switchMode("hint-mode");
        hintManager.nudgeHintPointsLeft();
        hintManager.nudgeHintPointsLeft();
        assertEquals(new Point(299, 125), shownPoint("a"));
        assertEquals(new Point(451, 550), shownPoint("b"));
        assertEquals(List.of(shownHintMesh.hints().getFirst()), shownHintMesh.savedHints());
        hintManager.unsaveCurrentPosition("pick-position-history");
        assertEquals(new Point(101, 125), shownPoint("a"));
    }

    @Test
    void aMovedHintPointOnItsCenterIsStillAPoint() throws IOException {
        start("""
                hint-mode.hint-points.cell-y-percent=0
                hint-mode.hint-points.nudge-distance=25
                """);
        switchMode("hint-mode");
        hintManager.nudgeHintPointsDown();
        assertEquals(Set.of(List.of(Key.ofName("a")), List.of(Key.ofName("b"))),
                shownHintMesh.pointByKeySequence().keySet());
        assertEquals(new Point(200, 125), shownPoint("a"));
    }

    @Test
    void aHintSavedAtItsCenterShowsNoHintPoint() throws IOException {
        start("""
                hint-mode.toggle-save.pick-position-history=+s
                hint-mode.overlay.saved-hints=pick-position-history
                """);
        switchMode("hint-mode");
        select("a");
        hintManager.saveCurrentPosition("pick-position-history");
        switchMode("pick-mode");
        switchMode("hint-mode");
        assertEquals(List.of(shownHintMesh.hints().getFirst()), shownHintMesh.savedHints());
        assertEquals(Map.of(), shownHintMesh.pointByKeySequence());
    }

    @Test
    void aHintPointStaysOnTheScreenOfItsHint() throws IOException {
        uiElements = List.of(new UiElement(new Rectangle(-50, 100, 200, 50)));
        start("hint-mode.hint-points.cell-x-percent=0\n");
        switchMode("hint-mode");
        assertEquals(new Point(0, 125), shownPoint("a"));
    }

    @Test
    void theMouseAndTheTargetNudgeByTheirOwnDistancesInEveryDirection() throws IOException {
        screenScale = 2;
        start("""
                hint-mode.mouse.nudge-distance=3
                pick-mode.overlay.mouse-follows-target=false
                pick-mode.target.nudge-distance=5
                """);
        hintManager.mouseMoved(100, 100);
        switchMode("hint-mode");
        hintManager.nudgeRight();
        hintManager.nudgeDown();
        hintManager.nudgeLeft();
        hintManager.nudgeUp();
        hintManager.nudgeTargetDown();
        assertEquals(List.of(106, 100, 106, 106, 100, 106, 100, 100, 100, 106), mousePositions);
        switchMode("pick-mode");
        hintManager.nudgeTargetLeft();
        hintManager.nudgeTargetUp();
        assertEquals(new Point(90, 96), hintManager.targetPoint());
        hintManager.nudgeTargetRight();
        assertEquals(new Point(100, 96), hintManager.targetPoint());
        assertEquals(10, mousePositions.size());
    }

    @Test
    void aNudgeIsAWholeNumberOfPixelsBothWays() throws IOException {
        screenScale = 1.5;
        start("");
        hintManager.mouseMoved(100, 100);
        switchMode("hint-mode");
        hintManager.nudgeRight();
        hintManager.nudgeLeft();
        assertEquals(List.of(102, 100, 100, 100), mousePositions);
    }

    @Test
    void theDefaultsAreTheCenterAndOnePixel() throws IOException {
        start("");
        hintManager.mouseMoved(100, 100);
        switchMode("hint-mode");
        assertTrue(hintManager.hintPointsCentered());
        hintManager.nudgeRight();
        hintManager.nudgeTargetDown();
        assertEquals(List.of(101, 100, 101, 101), mousePositions);
        hintManager.nudgeHintPointsRight();
        assertEquals(new Point(201, 125), shownPoint("a"));
    }

    @Test
    void aCellOfAnOddSizeHasItsEdgesOnItsFirstAndLastPixels() throws IOException {
        uiElements = List.of(new UiElement(new Rectangle(100, 100, 201, 51)));
        start("""
                hint-mode.hint-points.cell-x-percent=0
                hint-mode.hint-points.cell-y-percent=1
                """);
        switchMode("hint-mode");
        select("a");
        assertEquals(List.of(100, 150), mousePositions);
    }

    @Test
    void aHintWithoutACellKeepsItsPointAtItsCenter() throws IOException {
        start("""
                hint-mode.save.pick-position-history=+s
                hint-mode.to.history-mode=+y
                history-mode.overlay.type=pick-position-history
                history-mode.overlay.selection-keys=a b
                history-mode.mouse.smooth-jump-enabled=false
                """);
        hintManager.mouseMoved(300, 300);
        switchMode("idle-mode");
        hintManager.saveCurrentPosition("pick-position-history");
        switchMode("history-mode");
        hintManager.nudgeHintPointsRight();
        assertEquals(Map.of(), shownHintMesh.pointByKeySequence());
        select("a");
        assertEquals(List.of(300, 300), mousePositions);
    }

    @Test
    void aGridWithoutLabelsHasNoPoints() throws IOException {
        start("""
                hint-mode.to.grid-mode=+g
                grid-mode.overlay.grid-cell-sizing=fit
                grid-mode.overlay.grid-max-row-count=2
                grid-mode.overlay.grid-max-column-count=2
                grid-mode.hint-points.cell-x-percent=0
                """);
        switchMode("grid-mode");
        assertEquals(Map.of(), shownHintMesh.pointByKeySequence());
    }

    @Test
    void theHintsAreNotShownAgainWhenThePointsMoveAfterASelection() throws IOException {
        start("""
                hint-mode.save.pick-position-history=+s
                hint-mode.to.history-mode=+y
                history-mode.overlay.type=pick-position-history
                history-mode.overlay.selection-keys=a b
                """);
        hintManager.mouseMoved(300, 300);
        switchMode("idle-mode");
        hintManager.saveCurrentPosition("pick-position-history");
        switchMode("history-mode");
        select("a");
        int shownHintMeshCount = shownHintMeshes.size();
        hintManager.nudgeHintPointsLeft();
        assertEquals(shownHintMeshCount, shownHintMeshes.size());
    }

    @Test
    void theHintPointsMoveInAModeThatShowsTheSameHintsWithAnotherZoom() throws IOException {
        start("pick-mode.zoom.center=mouse\n");
        switchMode("hint-mode");
        switchMode("pick-mode");
        hintManager.nudgeHintPointsLeft();
        assertEquals(new Point(199, 125), shownPoint("a"));
    }

    @Test
    void aPointPastTheZoomGoesWhereItsHintIsUnzoomed() throws IOException {
        screenRectangles = List.of(new Rectangle(0, 0, 1920, 1080),
                new Rectangle(1920, 0, 1920, 1080));
        start("""
                hint-mode.to.grid-mode=+g
                grid-mode.mouse.smooth-jump-enabled=false
                grid-mode.overlay.type=grid
                grid-mode.overlay.selection-keys=a b
                grid-mode.overlay.grid-cell-sizing=fit
                grid-mode.overlay.grid-max-row-count=1
                grid-mode.overlay.grid-max-column-count=2
                grid-mode.zoom.percent=2
                grid-mode.zoom.center=mouse
                grid-mode.hint-points.cell-x-percent=1
                grid-mode.hint-points.nudge-distance=4
                """);
        hintManager.mouseMoved(960, 540);
        switchMode("grid-mode");
        hintManager.nudgeHintPointsRight();
        select("b");
        assertEquals(List.of(1443, 540), mousePositions);
    }

    @Test
    void theFirstArrowTakesTheHintPointsToTheEdgeAndTheNextOnesNudgeThem() throws IOException {
        start("""
                hint-mode.hint-points.cell-x-percent=0.5 | _{ishintpointscentered} +rightarrow -> 1 \
                    | +q -> 0 | +w -> 0.25
                hint-mode.nudge-hint-points.right=^{ishintpointscentered} +rightarrow
                """);
        startComboWatcher();
        press("h");
        press("rightarrow");
        assertEquals(new Point(299, 125), shownPoint("a"));
        press("rightarrow");
        assertEquals(new Point(300, 125), shownPoint("a"));
        press("w");
        assertEquals(new Point(150, 125), shownPoint("a"));
        press("q");
        assertEquals(new Point(100, 125), shownPoint("a"));
    }

    @Test
    void aMutatedNudgeDistanceAppliesToTheNextNudge() throws IOException {
        start("""
                hint-mode.hint-points.nudge-distance=4 | _{leftctrl} -> 2
                hint-mode.nudge-hint-points.right=+rightarrow
                """);
        startComboWatcher();
        press("h");
        press("rightarrow");
        assertEquals(new Point(204, 125), shownPoint("a"));
        down("leftctrl");
        press("rightarrow");
        up("leftctrl");
        assertEquals(new Point(206, 125), shownPoint("a"));
    }

    @Test
    void aMoveMovesTheHintPointsUntilItStops() throws IOException {
        start("""
                hint-mode.hint-points.initial-velocity=100
                hint-mode.hint-points.max-velocity=100
                hint-mode.hint-points.deceleration=0
                hint-mode.start-move-hint-points.right=+rightarrow
                hint-mode.stop-move-hint-points.right=-rightarrow
                """);
        startComboWatcher();
        press("h");
        down("rightarrow");
        hintManager.update(0.1);
        assertEquals(new Point(210, 125), shownPoint("a"));
        hintManager.update(0.1);
        assertEquals(new Point(220, 125), shownPoint("a"));
        up("rightarrow");
        hintManager.update(0.1);
        assertEquals(new Point(220, 125), shownPoint("a"));
    }

    @Test
    void aHeldArrowNudgesThenMovesTheHintPointsAtTheirVelocity() throws IOException {
        start("""
                hint-mode.hint-points.nudge-distance=4
                hint-mode.hint-points.initial-velocity=120
                hint-mode.hint-points.max-velocity=120
                hint-mode.hint-points.deceleration=0
                hint-mode.nudge-hint-points.right=+rightarrow
                hint-mode.start-move-hint-points.right=+rightarrow-500
                hint-mode.stop-move-hint-points.right=-rightarrow
                """);
        startComboWatcher();
        press("h");
        down("rightarrow");
        assertEquals(new Point(204, 125), shownPoint("a"));
        now = now.plusMillis(400);
        comboWatcher.update(0.4);
        hintManager.update(0.4);
        assertEquals(new Point(204, 125), shownPoint("a"));
        now = now.plusMillis(50);
        comboWatcher.update(0.05);
        for (int frame = 0; frame < 50; frame++)
            hintManager.update(0.016);
        assertEquals(new Point(300, 125), shownPoint("a"));
        up("rightarrow");
        hintManager.update(0.1);
        assertEquals(new Point(300, 125), shownPoint("a"));
    }

    @Test
    void anArrowPressedWhileTheHintPointsMoveJoinsTheMove() throws IOException {
        start("""
                hint-mode.hint-points.nudge-distance=4
                hint-mode.hint-points.initial-velocity=100
                hint-mode.hint-points.max-velocity=100
                hint-mode.hint-points.deceleration=0
                hint-mode.nudge-hint-points.right=+rightarrow
                hint-mode.nudge-hint-points.down=^{ishintpointsmoving} +downarrow
                hint-mode.start-move-hint-points.right=+rightarrow-500
                hint-mode.start-move-hint-points.down=_{ishintpointsmoving} +downarrow
                """);
        startComboWatcher();
        press("h");
        down("rightarrow");
        now = now.plusMillis(450);
        comboWatcher.update(0.45);
        hintManager.update(0.1);
        assertEquals(new Point(214, 125), shownPoint("a"));
        down("downarrow");
        assertEquals(new Point(214, 125), shownPoint("a"));
        hintManager.update(0.1);
        assertEquals(new Point(221, 132), shownPoint("a"));
    }

    @Test
    void aModeWithoutHintsStopsAMove() throws IOException {
        start("""
                hint-mode.to.normal-mode=+n
                normal-mode.to.idle-mode=+esc
                hint-mode.hint-points.initial-velocity=100
                hint-mode.hint-points.max-velocity=100
                hint-mode.start-move-hint-points.right=+rightarrow
                """);
        startComboWatcher();
        press("h");
        down("rightarrow");
        hintManager.update(0.1);
        assertEquals(new Point(210, 125), shownPoint("a"));
        press("n");
        hintManager.update(0.1);
        assertTrue(hintManager.hintPointsCentered());
    }

    @Test
    void theHintPointsComeBackAtOnceAfterAMovePastTheScreen() throws IOException {
        start("""
                hint-mode.hint-points.initial-velocity=10000
                hint-mode.hint-points.max-velocity=10000
                hint-mode.hint-points.deceleration=0
                hint-mode.hint-points.nudge-distance=1000
                hint-mode.start-move-hint-points.right=+rightarrow
                hint-mode.stop-move-hint-points.right=-rightarrow
                hint-mode.nudge-hint-points.left=+leftarrow
                """);
        startComboWatcher();
        press("h");
        down("rightarrow");
        hintManager.update(1);
        up("rightarrow");
        assertEquals(new Point(1920, 125), shownPoint("a"));
        assertEquals(new Point(1920, 550), shownPoint("b"));
        press("leftarrow");
        assertEquals(new Point(920, 125), shownPoint("a"));
        assertEquals(new Point(1270, 550), shownPoint("b"));
    }

    private void startComboWatcher() {
        Set<Key> unpressedPreconditionKeys = new HashSet<>();
        Set<Key> pressedPreconditionKeys = new HashSet<>();
        for (Mode mode : modeMap.modes()) {
            for (Combo combo : mode.comboMap().commandsByCombo().keySet()) {
                unpressedPreconditionKeys.addAll(
                        combo.precondition().keyPrecondition().unpressedKeySet());
                pressedPreconditionKeys.addAll(combo.precondition()
                                                    .keyPrecondition()
                                                    .pressedKeyPrecondition()
                                                    .allKeys());
            }
        }
        CommandRunner commandRunner = new CommandRunner(mouseManager, hintManager, null) {
            @Override
            public void run(Command command, Key eventKey) {
                if (command instanceof Command.SwitchMode switchMode)
                    comboWatcher.modeChanged(modeMap.get(switchMode.modeName()));
                else
                    super.run(command, eventKey);
            }
        };
        comboWatcher = new ComboWatcher(commandRunner, hintManager, () -> new App("test.exe"),
                null, () -> now, unpressedPreconditionKeys, pressedPreconditionKeys,
                new KeyRedactor(KeyRedaction.NONE), modeMap,
                configuration.initiallySetVariables(), configuration.virtualKeys(),
                configuration.initiallyPressedVirtualKeys(),
                configuration.activeKeyboardLayout());
        comboWatcher.setModeListeners(List.of(mouseManager, hintManager));
        comboWatcher.modeChanged(modeMap.get(Mode.IDLE_MODE_NAME));
    }

    private void press(String keyName) {
        down(keyName);
        up(keyName);
    }

    private void down(String keyName) {
        comboWatcher.updateBuiltInVirtualKeys(new MouseState(mouseManager),
                new KeyboardState(null) {
                    @Override
                    public boolean pressingUnhandledKeyInCurrentMode() {
                        return false;
                    }
                }, new AnimationPlayer(Map.of()));
        comboWatcher.keyEvent(new KeyEvent.PressKeyEvent(now, Key.ofName(keyName)));
        now = now.plusMillis(50);
        hintManager.completePendingUiHintQuery();
    }

    private void up(String keyName) {
        comboWatcher.keyEvent(new KeyEvent.ReleaseKeyEvent(now, Key.ofName(keyName)));
        now = now.plusMillis(50);
    }

}
