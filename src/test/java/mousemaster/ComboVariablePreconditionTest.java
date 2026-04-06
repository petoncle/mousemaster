package mousemaster;

import mousemaster.ComboPrecondition.ComboVariablePrecondition;
import mousemaster.ComboPrecondition.ComboVariablePrecondition.VariableCondition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ComboVariablePreconditionTest {

    static final ComboMoveDuration defaultDuration =
            new ComboMoveDuration(Duration.ZERO, null);

    static final KeyResolver identityKeyResolver = new KeyResolver(
            new KeyboardLayout("test", "test", "test", "test", List.of()),
            new KeyboardLayout("test", "test", "test", "test", List.of()));

    private static List<Combo> parse(String comboString, Set<String> allVariableNames) {
        return Combo.of("test", comboString, defaultDuration, Map.of(), Map.of(),
                identityKeyResolver, allVariableNames);
    }

    private static Combo parseSingle(String comboString, Set<String> allVariableNames) {
        List<Combo> combos = parse(comboString, allVariableNames);
        assertEquals(1, combos.size());
        return combos.getFirst();
    }

    // --- Variable block ---

    @Test
    void singleVariable() {
        Combo combo = parseSingle("_{islclick} +a", Set.of("islclick"));
        ComboVariablePrecondition varPre = combo.precondition().variablePrecondition();
        assertFalse(varPre.isEmpty());
        assertEquals(1, varPre.conditions().size());
        VariableCondition condition = varPre.conditions().getFirst();
        assertEquals("islclick", condition.variableName());
        assertFalse(condition.negated());
    }

    @Test
    void negatedVariable() {
        Combo combo = parseSingle("_{!islclick} +a", Set.of("islclick"));
        ComboVariablePrecondition varPre = combo.precondition().variablePrecondition();
        assertEquals(1, varPre.conditions().size());
        VariableCondition condition = varPre.conditions().getFirst();
        assertEquals("islclick", condition.variableName());
        assertTrue(condition.negated());
    }

    @Test
    void multipleVariables() {
        Combo combo = parseSingle("_{islclick !isnomove} +a",
                Set.of("islclick", "isnomove"));
        ComboVariablePrecondition varPre = combo.precondition().variablePrecondition();
        assertEquals(2, varPre.conditions().size());
        assertEquals("islclick", varPre.conditions().get(0).variableName());
        assertFalse(varPre.conditions().get(0).negated());
        assertEquals("isnomove", varPre.conditions().get(1).variableName());
        assertTrue(varPre.conditions().get(1).negated());
        assertTrue(combo.precondition().keyPrecondition().pressedKeyPrecondition().isEmpty());
    }

    @Test
    void variableOrGroups() {
        Combo combo = parseSingle("_{islclick | ismclick | isrclick} +a",
                Set.of("islclick", "ismclick", "isrclick"));
        ComboVariablePrecondition varPre = combo.precondition().variablePrecondition();
        assertEquals(3, varPre.groups().size());
        assertEquals(1, varPre.groups().get(0).size());
        assertEquals("islclick", varPre.groups().get(0).getFirst().variableName());
        assertEquals("ismclick", varPre.groups().get(1).getFirst().variableName());
        assertEquals("isrclick", varPre.groups().get(2).getFirst().variableName());
        // OR semantics: satisfied if any one group matches
        assertTrue(varPre.satisfiedBy(Set.of("islclick")));
        assertTrue(varPre.satisfiedBy(Set.of("ismclick")));
        assertTrue(varPre.satisfiedBy(Set.of("isrclick")));
        assertFalse(varPre.satisfiedBy(Set.of()));
        assertFalse(varPre.satisfiedBy(Set.of("other")));
    }

    @Test
    void variableOrGroupsWithAnd() {
        // _{islclick !isnomove | ismclick} means: (islclick AND !isnomove) OR ismclick
        Combo combo = parseSingle("_{islclick !isnomove | ismclick} +a",
                Set.of("islclick", "isnomove", "ismclick"));
        ComboVariablePrecondition varPre = combo.precondition().variablePrecondition();
        assertEquals(2, varPre.groups().size());
        assertEquals(2, varPre.groups().get(0).size());
        assertEquals(1, varPre.groups().get(1).size());
        // First group: islclick set AND isnomove not set
        assertTrue(varPre.satisfiedBy(Set.of("islclick")));
        assertFalse(varPre.satisfiedBy(Set.of("islclick", "isnomove")));
        // Second group: ismclick set
        assertTrue(varPre.satisfiedBy(Set.of("ismclick")));
        // Neither group
        assertFalse(varPre.satisfiedBy(Set.of()));
        assertFalse(varPre.satisfiedBy(Set.of("isnomove")));
    }

    @Test
    void variableBlockWithNoSequence() {
        Combo combo = parseSingle("_{islclick}", Set.of("islclick"));
        ComboVariablePrecondition varPre = combo.precondition().variablePrecondition();
        assertEquals(1, varPre.conditions().size());
        assertTrue(combo.sequence().isEmpty());
    }

    // --- Variable block combined with key block ---

    @Test
    void separateVariableAndKeyBlocks() {
        Combo combo = parseSingle("_{islclick} _{leftctrl} +a", Set.of("islclick"));
        ComboVariablePrecondition varPre = combo.precondition().variablePrecondition();
        assertEquals(1, varPre.conditions().size());
        assertEquals("islclick", varPre.conditions().getFirst().variableName());
        assertFalse(combo.precondition().keyPrecondition().pressedKeyPrecondition().isEmpty());
        Set<Key> pressedKeys = combo.precondition().keyPrecondition()
                .pressedKeyPrecondition().allKeys();
        assertEquals(Set.of(Key.ofName("leftctrl")), pressedKeys);
    }

    @Test
    void separateVariableAndPipeKeyBlocks() {
        Combo combo = parseSingle("_{!ismclick !isrclick} _{none | leftctrl} +a",
                Set.of("ismclick", "isrclick"));
        ComboVariablePrecondition varPre = combo.precondition().variablePrecondition();
        assertEquals(2, varPre.conditions().size());
        assertEquals("ismclick", varPre.conditions().get(0).variableName());
        assertTrue(varPre.conditions().get(0).negated());
        assertEquals("isrclick", varPre.conditions().get(1).variableName());
        assertTrue(varPre.conditions().get(1).negated());
        assertEquals(2, combo.precondition().keyPrecondition()
                .pressedKeyPrecondition().groups().size());
    }

    @Test
    void unpressedKeyAndVariableBlocks() {
        Combo combo = parseSingle("^{leftctrl} _{islclick} +a", Set.of("islclick"));
        assertFalse(combo.precondition().variablePrecondition().isEmpty());
        assertFalse(combo.precondition().keyPrecondition().unpressedKeySet().isEmpty());
        assertEquals(Set.of(Key.ofName("leftctrl")),
                combo.precondition().keyPrecondition().unpressedKeySet());
    }

    // --- Key block without variables (backward compatibility) ---

    @Test
    void noVariablesDefined() {
        Combo combo = parseSingle("_{leftctrl} +a", Set.of());
        assertTrue(combo.precondition().variablePrecondition().isEmpty());
        assertFalse(combo.precondition().keyPrecondition().pressedKeyPrecondition().isEmpty());
    }

    @Test
    void keyBlockWhenVariablesExistButNotReferenced() {
        Combo combo = parseSingle("_{leftctrl} +a", Set.of("islclick"));
        assertTrue(combo.precondition().variablePrecondition().isEmpty());
        assertFalse(combo.precondition().keyPrecondition().pressedKeyPrecondition().isEmpty());
    }

    // --- multiCombo ---

    @Test
    void multiComboWithVariables() {
        List<Combo> combos = Combo.multiCombo("test",
                "_{islclick} +a | _{!islclick} +b",
                defaultDuration, Map.of(), Map.of(), identityKeyResolver,
                Set.of("islclick"));
        assertEquals(2, combos.size());
        assertEquals("islclick",
                combos.get(0).precondition().variablePrecondition()
                        .conditions().getFirst().variableName());
        assertFalse(combos.get(0).precondition().variablePrecondition()
                .conditions().getFirst().negated());
        assertEquals("islclick",
                combos.get(1).precondition().variablePrecondition()
                        .conditions().getFirst().variableName());
        assertTrue(combos.get(1).precondition().variablePrecondition()
                .conditions().getFirst().negated());
    }

    // --- Error cases ---

    @Test
    void negatedNonVariableThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> parseSingle("_{!leftctrl} +a", Set.of("islclick")));
    }

    @Test
    void mixedVariablesAndKeysInSameBlockThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> parseSingle("_{islclick leftctrl} +a", Set.of("islclick")));
    }

}
