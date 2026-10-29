package mousemaster.renderer;

import mousemaster.Hint;
import mousemaster.HintMesh;
import mousemaster.Key;
import mousemaster.Point;
import mousemaster.Rectangle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavedHintTest {

    @Test
    void aHintIsSavedWhenASavedPositionIsInItsElement() {
        Hint hint = new Hint(150, 120, -1, -1, List.of(), new Rectangle(100, 100, 100, 40));
        assertTrue(HintMeshRenderer.saved(hint, List.of(new Point(151, 121))));
        assertFalse(HintMeshRenderer.saved(hint, List.of(new Point(150, 141))));
    }

    @Test
    void aHintIsSavedWhenASavedPositionIsInItsCell() {
        Hint hint = new Hint(480, 270, 960, 540, List.of(), null);
        assertTrue(HintMeshRenderer.saved(hint, List.of(new Point(959, 539))));
        assertFalse(HintMeshRenderer.saved(hint, List.of(new Point(961, 270))));
    }

    @Test
    void aHintWithoutCellIsSavedWhenASavedPositionIsOnItsCenter() {
        Hint hint = new Hint(100.4, 200.6, -1, -1, List.of(), null);
        assertTrue(HintMeshRenderer.saved(hint, List.of(new Point(100, 201))));
        assertFalse(HintMeshRenderer.saved(hint, List.of(new Point(101, 201))));
    }

    @Test
    void aSavedHintStaysWhenTheSelectedKeysHideIt() {
        Hint savedHint = new Hint(150, 120, -1, -1, List.of(Key.ofName("b")),
                new Rectangle(100, 100, 100, 40));
        Hint hint = new Hint(450, 120, -1, -1, List.of(Key.ofName("c")),
                new Rectangle(400, 100, 100, 40));
        HintMesh hintMesh = new HintMesh.HintMeshBuilder()
                .hints(List.of(savedHint, hint))
                .selectedKeySequence(List.of(Key.ofName("a")))
                .savedPositions(List.of(new Point(150, 120)))
                .build();
        assertTrue(HintMeshRenderer.visible(savedHint, hintMesh));
        assertFalse(HintMeshRenderer.visible(hint, hintMesh));
    }
}
