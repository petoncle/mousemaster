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
class HotspotTest {

    private final List<Object> mousePositions = new ArrayList<>();
    private final List<List<Object>> mouseMoveDirections = new ArrayList<>();
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
                            if (method.getName().equals("moveBy"))
                                mouseMoveDirections.add(List.of(args[0], args[2]));
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

    private Point shownHotspot(String keyName) {
        return shownHintMesh.hints()
                            .stream()
                            .filter(hint -> hint.keySequence().equals(List.of(Key.ofName(keyName))))
                            .map(shownHintMesh::hotspot)
                            .findFirst()
                            .orElseThrow();
    }

    @Test
    void aSelectionPutsTheTargetOnTheHotspot() throws IOException {
        start("");
        switchMode("hint-mode");
        assertEquals(Map.of(), shownHintMesh.hotspotByKeySequence());
        select("a");
        assertEquals(List.of(200, 125), mousePositions);
        assertEquals(new Point(200, 125), hintManager.lastSelectedHotspot());
    }

    @Test
    void theHotspotsGoToTheEdgesOfTheirOwnCells() throws IOException {
        start("""
                hint-mode.hotspots.cell-x-percent=0.5 | +q -> 0 | +w -> 1
                hint-mode.hotspots.cell-y-percent=0.5 | +e -> 0 | +r -> 1
                """);
        startComboWatcher();
        press("h");
        press("q");
        assertEquals(new Point(100, 125), shownHotspot("a"));
        assertEquals(new Point(500, 550), shownHotspot("b"));
        press("e");
        assertEquals(new Point(100, 100), shownHotspot("a"));
        assertEquals(new Point(500, 500), shownHotspot("b"));
        press("w");
        assertEquals(new Point(299, 100), shownHotspot("a"));
        assertEquals(new Point(599, 500), shownHotspot("b"));
        press("r");
        assertEquals(new Point(299, 149), shownHotspot("a"));
        assertEquals(new Point(599, 599), shownHotspot("b"));
        select("b");
        assertEquals(List.of(599, 599), mousePositions);
        assertEquals(new Point(599, 599), hintManager.lastSelectedHotspot());
    }

    @Test
    void theHotspotsAreAtThePercentsOfTheirCells() throws IOException {
        start("""
                hint-mode.hotspots.cell-x-percent=0.25
                hint-mode.hotspots.cell-y-percent=0.75
                """);
        switchMode("hint-mode");
        assertEquals(new Point(150, 138), shownHotspot("a"));
        assertEquals(new Point(525, 575), shownHotspot("b"));
        select("a");
        assertEquals(List.of(150, 138), mousePositions);
    }

    @Test
    void aNewPercentUndoesTheNudgesOnItsAxisOnly() throws IOException {
        start("""
                hint-mode.hotspots.nudge-distance=4
                hint-mode.hotspots.cell-x-percent=0.5 | +q -> 0 | +w -> 0.5
                hint-mode.nudge-hotspots.right=+rightarrow
                hint-mode.nudge-hotspots.down=+downarrow
                """);
        startComboWatcher();
        press("h");
        press("rightarrow");
        press("downarrow");
        assertEquals(new Point(204, 129), shownHotspot("a"));
        press("q");
        assertEquals(new Point(100, 129), shownHotspot("a"));
        press("rightarrow");
        press("w");
        assertEquals(new Point(200, 129), shownHotspot("a"));
    }

    @Test
    void aNudgeMovesTheHotspotsByTheirNudgeDistanceAtTheScreenScale() throws IOException {
        screenScale = 2;
        start("hint-mode.hotspots.nudge-distance=4\n");
        switchMode("hint-mode");
        hintManager.nudgeHotspotsRight();
        hintManager.nudgeHotspotsRight();
        hintManager.nudgeHotspotsUp();
        assertEquals(new Point(216, 117), shownHotspot("a"));
        assertFalse(hintManager.hotspotsCentered());
        hintManager.nudgeHotspotsLeft();
        hintManager.nudgeHotspotsLeft();
        hintManager.nudgeHotspotsDown();
        assertTrue(hintManager.hotspotsCentered());
    }

    @Test
    void aHotspotIsOnAWholePixel() throws IOException {
        screenScale = 1.25;
        start("hint-mode.hotspots.nudge-distance=2\n");
        switchMode("hint-mode");
        hintManager.nudgeHotspotsRight();
        assertEquals(new Point(203, 125), shownHotspot("a"));
    }

    @Test
    void aModeWithoutHintsUndoesTheNudges() throws IOException {
        start("");
        switchMode("hint-mode");
        hintManager.nudgeHotspotsRight();
        assertEquals(new Point(201, 125), shownHotspot("a"));
        switchMode("idle-mode");
        assertTrue(hintManager.hotspotsCentered());
        switchMode("hint-mode");
        assertEquals(Map.of(), shownHintMesh.hotspotByKeySequence());
    }

    @Test
    void aModeWithOtherHintsUndoesTheNudges() throws IOException {
        start("""
                hint-mode.to.grid-mode=+g
                grid-mode.overlay.type=grid
                grid-mode.overlay.selection-keys=a b
                """);
        switchMode("hint-mode");
        hintManager.nudgeHotspotsRight();
        switchMode("grid-mode");
        assertTrue(hintManager.hotspotsCentered());
    }

    @Test
    void aNudgeBeforeTheUiHintsAreFoundMovesTheirHotspots() throws IOException {
        start("");
        switchMode("idle-mode");
        mouseManager.modeChanged(modeMap.get("hint-mode"));
        hintManager.modeChanged(modeMap.get("hint-mode"));
        hintManager.nudgeHotspotsRight();
        hintManager.completePendingUiHintQuery();
        assertEquals(new Point(201, 125), shownHotspot("a"));
    }

    @Test
    void aNudgeInAModeWithoutHintsMovesNoHotspot() throws IOException {
        start("");
        switchMode("idle-mode");
        hintManager.nudgeHotspotsRight();
        switchMode("hint-mode");
        assertEquals(Map.of(), shownHintMesh.hotspotByKeySequence());
    }

    @Test
    void aModeWithTheSameHotspotsKeepsThem() throws IOException {
        start("""
                hint-mode.hotspots.cell-x-percent=0.5 | +q -> 0
                hint-mode.nudge-hotspots.right=+rightarrow
                pick-mode.hotspots=hint-mode.hotspots
                """);
        startComboWatcher();
        press("h");
        press("q");
        press("rightarrow");
        press("p");
        assertEquals(new Point(101, 125), shownHotspot("a"));
        assertFalse(hintManager.hotspotsCentered());
    }

    @Test
    void aPickKeepsTheHotspotItWasSavedAt() throws IOException {
        start("""
                hint-mode.toggle-save.pick-position-history=+s
                hint-mode.overlay.saved-hints=pick-position-history
                hint-mode.hotspots.nudge-distance=99
                """);
        switchMode("hint-mode");
        hintManager.nudgeHotspotsRight();
        select("a");
        hintManager.saveCurrentPosition("pick-position-history");
        switchMode("pick-mode");
        switchMode("hint-mode");
        hintManager.nudgeHotspotsLeft();
        hintManager.nudgeHotspotsLeft();
        assertEquals(new Point(299, 125), shownHotspot("a"));
        assertEquals(new Point(451, 550), shownHotspot("b"));
        assertEquals(List.of(shownHintMesh.hints().getFirst()), shownHintMesh.savedHints());
        hintManager.unsaveCurrentPosition("pick-position-history");
        assertEquals(new Point(101, 125), shownHotspot("a"));
    }

    @Test
    void aMovedHotspotOnItsCenterIsStillAHotspot() throws IOException {
        start("""
                hint-mode.hotspots.cell-y-percent=0
                hint-mode.hotspots.nudge-distance=25
                """);
        switchMode("hint-mode");
        hintManager.nudgeHotspotsDown();
        assertEquals(Set.of(List.of(Key.ofName("a")), List.of(Key.ofName("b"))),
                shownHintMesh.hotspotByKeySequence().keySet());
        assertEquals(new Point(200, 125), shownHotspot("a"));
    }

    @Test
    void aHintSavedAtItsCenterShowsNoHotspot() throws IOException {
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
        assertEquals(Map.of(), shownHintMesh.hotspotByKeySequence());
    }

    @Test
    void aHotspotStaysOnTheScreenOfItsHint() throws IOException {
        uiElements = List.of(new UiElement(new Rectangle(-50, 100, 200, 50)));
        start("hint-mode.hotspots.cell-x-percent=0\n");
        switchMode("hint-mode");
        assertEquals(new Point(0, 125), shownHotspot("a"));
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
        assertTrue(hintManager.hotspotsCentered());
        hintManager.nudgeRight();
        hintManager.nudgeTargetDown();
        assertEquals(List.of(101, 100, 101, 101), mousePositions);
        hintManager.nudgeHotspotsRight();
        assertEquals(new Point(201, 125), shownHotspot("a"));
    }

    @Test
    void aCellOfAnOddSizeHasItsEdgesOnItsFirstAndLastPixels() throws IOException {
        uiElements = List.of(new UiElement(new Rectangle(100, 100, 201, 51)));
        start("""
                hint-mode.hotspots.cell-x-percent=0
                hint-mode.hotspots.cell-y-percent=1
                """);
        switchMode("hint-mode");
        select("a");
        assertEquals(List.of(100, 150), mousePositions);
    }

    @Test
    void aHintWithoutACellKeepsItsHotspotAtItsCenter() throws IOException {
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
        hintManager.nudgeHotspotsRight();
        assertEquals(Map.of(), shownHintMesh.hotspotByKeySequence());
        select("a");
        assertEquals(List.of(300, 300), mousePositions);
    }

    @Test
    void aGridWithoutLabelsHasNoHotspots() throws IOException {
        start("""
                hint-mode.to.grid-mode=+g
                grid-mode.overlay.grid-cell-sizing=fit
                grid-mode.overlay.grid-max-row-count=2
                grid-mode.overlay.grid-max-column-count=2
                grid-mode.hotspots.cell-x-percent=0
                """);
        switchMode("grid-mode");
        assertEquals(Map.of(), shownHintMesh.hotspotByKeySequence());
    }

    @Test
    void theHintsAreNotShownAgainWhenTheHotspotsMoveAfterASelection() throws IOException {
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
        hintManager.nudgeHotspotsLeft();
        assertEquals(shownHintMeshCount, shownHintMeshes.size());
    }

    @Test
    void theHotspotsMoveInAModeThatShowsTheSameHintsWithAnotherZoom() throws IOException {
        start("pick-mode.zoom.center=mouse\n");
        switchMode("hint-mode");
        switchMode("pick-mode");
        hintManager.nudgeHotspotsLeft();
        assertEquals(new Point(199, 125), shownHotspot("a"));
    }

    @Test
    void aHotspotPastTheZoomGoesWhereItsHintIsUnzoomed() throws IOException {
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
                grid-mode.hotspots.cell-x-percent=1
                grid-mode.hotspots.nudge-distance=4
                """);
        hintManager.mouseMoved(960, 540);
        switchMode("grid-mode");
        hintManager.nudgeHotspotsRight();
        select("b");
        assertEquals(List.of(1443, 540), mousePositions);
    }

    @Test
    void theFirstArrowTakesTheHotspotsToTheEdgeAndTheNextOnesNudgeThem() throws IOException {
        start("""
                hint-mode.hotspots.cell-x-percent=0.5 | _{ishotspotscentered} +rightarrow -> 1 \
                    | +q -> 0 | +w -> 0.25
                hint-mode.nudge-hotspots.right=^{ishotspotscentered} +rightarrow
                """);
        startComboWatcher();
        press("h");
        press("rightarrow");
        assertEquals(new Point(299, 125), shownHotspot("a"));
        press("rightarrow");
        assertEquals(new Point(300, 125), shownHotspot("a"));
        press("w");
        assertEquals(new Point(150, 125), shownHotspot("a"));
        press("q");
        assertEquals(new Point(100, 125), shownHotspot("a"));
    }

    @Test
    void aMutatedNudgeDistanceAppliesToTheNextNudge() throws IOException {
        start("""
                hint-mode.hotspots.nudge-distance=4 | _{leftctrl} -> 2
                hint-mode.nudge-hotspots.right=+rightarrow
                """);
        startComboWatcher();
        press("h");
        press("rightarrow");
        assertEquals(new Point(204, 125), shownHotspot("a"));
        down("leftctrl");
        press("rightarrow");
        up("leftctrl");
        assertEquals(new Point(206, 125), shownHotspot("a"));
    }

    @Test
    void aMoveMovesTheHotspotsUntilItStops() throws IOException {
        start("""
                hint-mode.hotspots.initial-velocity=100
                hint-mode.hotspots.max-velocity=100
                hint-mode.hotspots.deceleration=0
                hint-mode.start-move-hotspots.right=+rightarrow
                hint-mode.stop-move-hotspots.right=-rightarrow
                """);
        startComboWatcher();
        press("h");
        down("rightarrow");
        hintManager.update(0.1);
        assertEquals(new Point(210, 125), shownHotspot("a"));
        hintManager.update(0.1);
        assertEquals(new Point(220, 125), shownHotspot("a"));
        up("rightarrow");
        hintManager.update(0.1);
        assertEquals(new Point(220, 125), shownHotspot("a"));
    }

    @Test
    void aHeldArrowNudgesThenMovesTheHotspotsAtTheirVelocity() throws IOException {
        start("""
                hint-mode.hotspots.nudge-distance=4
                hint-mode.hotspots.initial-velocity=120
                hint-mode.hotspots.max-velocity=120
                hint-mode.hotspots.deceleration=0
                hint-mode.nudge-hotspots.right=+rightarrow
                hint-mode.start-move-hotspots.right=+rightarrow-500
                hint-mode.stop-move-hotspots.right=-rightarrow
                """);
        startComboWatcher();
        press("h");
        down("rightarrow");
        assertEquals(new Point(204, 125), shownHotspot("a"));
        now = now.plusMillis(400);
        comboWatcher.update(0.4);
        hintManager.update(0.4);
        assertEquals(new Point(204, 125), shownHotspot("a"));
        now = now.plusMillis(50);
        comboWatcher.update(0.05);
        for (int frame = 0; frame < 50; frame++)
            hintManager.update(0.016);
        assertEquals(new Point(300, 125), shownHotspot("a"));
        up("rightarrow");
        hintManager.update(0.1);
        assertEquals(new Point(300, 125), shownHotspot("a"));
    }

    @Test
    void anArrowPressedWhileTheHotspotsMoveJoinsTheMove() throws IOException {
        start("""
                hint-mode.hotspots.nudge-distance=4
                hint-mode.hotspots.initial-velocity=100
                hint-mode.hotspots.max-velocity=100
                hint-mode.hotspots.deceleration=0
                hint-mode.nudge-hotspots.right=+rightarrow
                hint-mode.nudge-hotspots.down=^{ishotspotsmoving} +downarrow
                hint-mode.start-move-hotspots.right=+rightarrow-500
                hint-mode.start-move-hotspots.down=_{ishotspotsmoving} +downarrow
                """);
        startComboWatcher();
        press("h");
        down("rightarrow");
        now = now.plusMillis(450);
        comboWatcher.update(0.45);
        hintManager.update(0.1);
        assertEquals(new Point(214, 125), shownHotspot("a"));
        down("downarrow");
        assertEquals(new Point(214, 125), shownHotspot("a"));
        hintManager.update(0.1);
        assertEquals(new Point(221, 132), shownHotspot("a"));
    }

    @Test
    void anArrowPressedWhileTheTargetMovesJoinsTheMove() throws IOException {
        start("""
                hint-mode.overlay.mouse-follows-target=false
                hint-mode.target.initial-velocity=100
                hint-mode.target.max-velocity=100
                hint-mode.target.deceleration=0
                hint-mode.start-move-target.right=+rightarrow
                hint-mode.start-move-target.down=_{istargetmoving} +downarrow
                hint-mode.nudge-target.down=^{istargetmoving} +downarrow
                """);
        hintManager.mouseMoved(100, 100);
        startComboWatcher();
        press("h");
        down("rightarrow");
        hintManager.update(0.1);
        assertEquals(new Point(110, 100), hintManager.targetPoint());
        down("downarrow");
        assertEquals(new Point(110, 100), hintManager.targetPoint());
        hintManager.update(0.1);
        assertEquals(new Point(117, 107), hintManager.targetPoint());
    }

    @Test
    void anArrowPressedWhileTheMouseMovesTheTargetJoinsTheMove() throws IOException {
        start("""
                hint-mode.mouse.initial-velocity=100
                hint-mode.mouse.max-velocity=100
                hint-mode.start-move-target.right=+rightarrow
                hint-mode.start-move-target.down=_{istargetmoving} +downarrow
                hint-mode.nudge-target.down=^{istargetmoving} +downarrow
                """);
        startComboWatcher();
        press("h");
        down("rightarrow");
        mouseManager.update(0.1);
        down("downarrow");
        mouseManager.update(0.1);
        assertEquals(List.of(List.of(true, false), List.of(true, true)), mouseMoveDirections);
        assertEquals(List.of(), mousePositions);
    }

    @Test
    void aModeWithoutHintsStopsAMove() throws IOException {
        start("""
                hint-mode.to.normal-mode=+n
                normal-mode.to.idle-mode=+esc
                hint-mode.hotspots.initial-velocity=100
                hint-mode.hotspots.max-velocity=100
                hint-mode.start-move-hotspots.right=+rightarrow
                """);
        startComboWatcher();
        press("h");
        down("rightarrow");
        hintManager.update(0.1);
        assertEquals(new Point(210, 125), shownHotspot("a"));
        press("n");
        hintManager.update(0.1);
        assertTrue(hintManager.hotspotsCentered());
    }

    @Test
    void theHotspotsComeBackAtOnceAfterAMovePastTheScreen() throws IOException {
        start("""
                hint-mode.hotspots.initial-velocity=10000
                hint-mode.hotspots.max-velocity=10000
                hint-mode.hotspots.deceleration=0
                hint-mode.hotspots.nudge-distance=1000
                hint-mode.start-move-hotspots.right=+rightarrow
                hint-mode.stop-move-hotspots.right=-rightarrow
                hint-mode.nudge-hotspots.left=+leftarrow
                """);
        startComboWatcher();
        press("h");
        down("rightarrow");
        hintManager.update(1);
        up("rightarrow");
        assertEquals(new Point(1920, 125), shownHotspot("a"));
        assertEquals(new Point(1920, 550), shownHotspot("b"));
        press("leftarrow");
        assertEquals(new Point(920, 125), shownHotspot("a"));
        assertEquals(new Point(1270, 550), shownHotspot("b"));
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
