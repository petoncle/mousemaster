package mousemaster.renderer;

import mousemaster.Hint;
import mousemaster.HintMesh;
import mousemaster.Key;
import mousemaster.Rectangle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavedHintTest {

    @Test
    void aSavedHintStaysWhenTheSelectedKeysHideIt() {
        Hint savedHint = new Hint(150, 120, -1, -1, List.of(Key.ofName("b")),
                new Rectangle(100, 100, 100, 40));
        Hint hint = new Hint(450, 120, -1, -1, List.of(Key.ofName("c")),
                new Rectangle(400, 100, 100, 40));
        HintMesh hintMesh = new HintMesh.HintMeshBuilder()
                .hints(List.of(savedHint, hint))
                .selectedKeySequence(List.of(Key.ofName("a")))
                .savedHints(List.of(savedHint))
                .build();
        assertTrue(HintMeshRenderer.visible(savedHint, hintMesh));
        assertFalse(HintMeshRenderer.visible(hint, hintMesh));
    }
}
