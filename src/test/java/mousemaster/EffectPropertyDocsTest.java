package mousemaster;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the configuration reference in step with the {@link EffectProperty} table:
 * a property added to the table must be documented, or this test fails.
 */
class EffectPropertyDocsTest {

    @Test
    void everyEffectPropertyIsDocumented() throws IOException {
        String reference = Files.readString(Path.of("docs/configuration-reference.md"));
        List<String> missing = new ArrayList<>();
        for (EffectProperty property : EffectProperty.values()) {
            String documentedAs = switch (property) {
                case WIDTH, HEIGHT -> "`size`";
                case PIVOT_X, PIVOT_Y -> "`pivot`";
                case VISIBLE -> "`show`";
                default -> "`" + property.key + "`";
            };
            if (!reference.contains(documentedAs))
                missing.add(property.key);
        }
        assertTrue(missing.isEmpty(),
                "Effect properties missing from docs/configuration-reference.md: " + missing);
    }

}
