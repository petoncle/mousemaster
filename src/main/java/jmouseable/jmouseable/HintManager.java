package jmouseable.jmouseable;

import jmouseable.jmouseable.HintMesh.HintMeshBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class HintManager implements ModeListener {

    private final MonitorManager monitorManager;
    private final MouseController mouseController;
    private ModeController modeController;
    private HintMesh hintMesh;
    private Set<Key> selectionKeySubset;
    private Mode currentMode;

    public HintManager(MonitorManager monitorManager, MouseController mouseController) {
        this.monitorManager = monitorManager;
        this.mouseController = mouseController;
    }

    public void setModeController(ModeController modeController) {
        this.modeController = modeController;
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
        if (currentMode != null) {
            HintMeshConfiguration oldHintMeshConfiguration = currentMode.hintMesh();
            if (oldHintMeshConfiguration.enabled() &&
                oldHintMeshConfiguration.type().equals(hintMeshConfiguration.type()) &&
                oldHintMeshConfiguration.selectionKeys()
                                        .equals(hintMeshConfiguration.selectionKeys())) {
                // Keep the old focusedKeySequence.
                // This is useful for hint-then-click-mode that extends hint-mode.
                hintMesh.hints(this.hintMesh.hints())
                        .focusedKeySequence(this.hintMesh.focusedKeySequence());
                return hintMesh.build();
            }
        }
        HintMeshType type = hintMeshConfiguration.type();
        if (type instanceof HintMeshType.ActiveScreen ||
            type instanceof HintMeshType.ActiveWindow) {
            int hintMeshX, hintMeshY, hintMeshWidth, hintMeshHeight, rowCount,
                    columnCount;
            if (type instanceof HintMeshType.ActiveScreen activeScreen) {
                Monitor monitor = monitorManager.activeMonitor();
                hintMeshX = monitor.x();
                hintMeshY = monitor.y();
                hintMeshWidth =
                        (int) (monitor.width() * activeScreen.screenWidthPercent());
                hintMeshHeight =
                        (int) (monitor.height() * activeScreen.screenHeightPercent());
                rowCount = activeScreen.rowCount();
                columnCount = activeScreen.columnCount();
            }
            else if (type instanceof HintMeshType.ActiveWindow activeWindow) {
                Rectangle activeWindowRectangle = WindowsOverlay.activeWindowRectangle(
                        activeWindow.windowWidthPercent(),
                        activeWindow.windowHeightPercent());
                hintMeshX = activeWindowRectangle.x();
                hintMeshY = activeWindowRectangle.y();
                hintMeshWidth = activeWindowRectangle.width();
                hintMeshHeight = activeWindowRectangle.height();
                rowCount = activeWindow.rowCount();
                columnCount = activeWindow.columnCount();
            }
            else
                throw new IllegalStateException();
            List<Key> selectionKeySubset =
                    gridSelectionKeySubset(hintMeshConfiguration.selectionKeys(), rowCount,
                            columnCount);
            int hintCount = rowCount * columnCount;
            // Find hintLength such that hintKeyCount^hintLength >= rowCount*columnCount
            int hintLength = (int) Math.ceil(
                    Math.log(hintCount) / Math.log(selectionKeySubset.size()));
            int cellWidth = hintMeshWidth / rowCount;
            int cellHeight = hintMeshHeight / columnCount;
            List<Hint> hints = new ArrayList<>(hintCount);
            int cellIndex = 0;
            for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                    List<Key> keySequence = new ArrayList<>();
                    // We want the hints to look like this:
                    // aa, ba, ..., za
                    // ab, bb, ..., zb
                    // az, bz, ..., zz
                    // The ideal situation is when rowCount = columnCount = hintKeys.size().
                    for (int i = 0; i < hintLength; i++)
                        keySequence.add(selectionKeySubset.get((int) (cellIndex /
                                                                      Math.pow(
                                                                              selectionKeySubset.size(),
                                                                              i) %
                                                                      selectionKeySubset.size())));
                    cellIndex++;
                    int hintCenterX = hintMeshX + columnIndex * cellWidth + cellWidth / 2;
                    int hintCenterY = hintMeshY + rowIndex * cellHeight + cellHeight / 2;
                    hints.add(new Hint(hintCenterX, hintCenterY, keySequence));
                }
            }
            hintMesh.hints(hints);
        }
        else {
            // TODO
        }
        return hintMesh.build();
    }

    private static List<Key> gridSelectionKeySubset(List<Key> keys, int rowCount,
                                                    int columnCount) {
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

}
