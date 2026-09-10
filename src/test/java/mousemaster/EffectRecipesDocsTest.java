package mousemaster;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every effect example in the configuration reference must parse: the recipes are
 * what people copy, so the docs may not drift from the parser.
 */
class EffectRecipesDocsTest {

    @Test
    void everyEffectRecipeInTheReferenceParses() throws IOException {
        String reference = Files.readString(Path.of("docs/configuration-reference.md"));
        Matcher blocks = Pattern.compile("```properties\\n(.*?)```", Pattern.DOTALL).matcher(reference);
        int effectBlocks = 0;
        List<String> failures = new ArrayList<>();
        while (blocks.find()) {
            String block = blocks.group(1);
            if (!block.contains(".effect."))
                continue;
            effectBlocks++;
            List<String> lines = new ArrayList<>();
            Set<String> effects = new LinkedHashSet<>();
            for (String line : block.split("\n")) {
                String stripped = line.replaceAll("\\s+#.*$", "").trim();
                if (stripped.isEmpty() || stripped.startsWith("#"))
                    continue;
                lines.add(stripped);
                Matcher name = Pattern.compile("^([a-z0-9-]+)\\.effect\\.([a-z0-9-]+)\\.").matcher(stripped);
                if (name.find())
                    effects.add(name.group(1) + "|" + name.group(2));
            }
            // A recipe may leave out its start-effect and the way into its mode for brevity.
            int key = 0;
            for (String effect : effects) {
                String[] modeAndName = effect.split("\\|");
                if (lines.stream().noneMatch(l -> l.startsWith(modeAndName[0] + ".start-effect." + modeAndName[1] + "=")))
                    lines.add(modeAndName[0] + ".start-effect." + modeAndName[1] + "=+f" + (++key));
                if (!modeAndName[0].equals(Mode.IDLE_MODE_NAME) &&
                    lines.stream().noneMatch(l -> l.startsWith(Mode.IDLE_MODE_NAME + ".to." + modeAndName[0])))
                    lines.add(Mode.IDLE_MODE_NAME + ".to." + modeAndName[0] + "=+z");
            }
            try {
                ConfigurationParser.parse(lines, KeyboardLayout.keyboardLayout("00000409", null));
            } catch (IllegalArgumentException e) {
                failures.add("block " + effectBlocks + ": " + e.getMessage());
            }
        }
        assertTrue(effectBlocks > 0, "no effect recipe found in docs/configuration-reference.md");
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

}
