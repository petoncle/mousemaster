package mousemaster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ComboMap(Map<Combo, List<Command>> commandsByCombo) {

    public static class ComboMapBuilder {

        private Map<Combo, List<Command>> to = new HashMap<>();
        private Map<Combo, List<Command>> startMove = new HashMap<>();
        private Map<Combo, List<Command>> stopMove = new HashMap<>();
        private Map<Combo, List<Command>> press = new HashMap<>();
        private Map<Combo, List<Command>> release = new HashMap<>();
        private Map<Combo, List<Command>> toggle = new HashMap<>();
        private Map<Combo, List<Command>> startWheel = new HashMap<>();
        private Map<Combo, List<Command>> stopWheel = new HashMap<>();
        private Map<Combo, List<Command>> snap = new HashMap<>();
        private Map<Combo, List<Command>> shrinkGrid = new HashMap<>();
        private Map<Combo, List<Command>> moveGrid = new HashMap<>();
        private Map<Combo, List<Command>> moveToGridCenter = new HashMap<>();
        private Map<Combo, List<Command>> savePosition = new HashMap<>();
        private Map<Combo, List<Command>> clearPositionHistory = new HashMap<>();
        private Map<Combo, List<Command>> cycleNextPosition = new HashMap<>();
        private Map<Combo, List<Command>> cyclePreviousPosition = new HashMap<>();
        private Map<Combo, List<Command>> remap = new HashMap<>();

        public ComboMapBuilder add(Combo combo, Command command) {
            Map<Combo, List<Command>> map = switch (command) {
                // @formatter:off
                case Command.SwitchMode switchMode -> to;

                case Command.StartMoveUp startMoveUp -> startMove;
                case Command.StartMoveDown startMoveDown -> startMove;
                case Command.StartMoveLeft startMoveLeft -> startMove;
                case Command.StartMoveRight startMoveRight -> startMove;

                case Command.StopMoveUp stopMoveUp -> stopMove;
                case Command.StopMoveDown stopMoveDown -> stopMove;
                case Command.StopMoveLeft stopMoveLeft -> stopMove;
                case Command.StopMoveRight stopMoveRight -> stopMove;

                case Command.PressLeft pressLeft -> press;
                case Command.PressMiddle pressMiddle -> press;
                case Command.PressRight pressRight -> press;

                case Command.ReleaseLeft releaseLeft -> release;
                case Command.ReleaseMiddle releaseMiddle -> release;
                case Command.ReleaseRight releaseRight -> release;

                case Command.ToggleLeft toggleLeft -> toggle;
                case Command.ToggleMiddle toggleMiddle -> toggle;
                case Command.ToggleRight toggleRight -> toggle;

                case Command.StartWheelUp startWheelUp -> startWheel;
                case Command.StartWheelDown startWheelDown -> startWheel;
                case Command.StartWheelLeft startWheelLeft -> startWheel;
                case Command.StartWheelRight startWheelRight -> startWheel;

                case Command.StopWheelUp stopWheelUp -> stopWheel;
                case Command.StopWheelDown stopWheelDown -> stopWheel;
                case Command.StopWheelLeft stopWheelLeft -> stopWheel;
                case Command.StopWheelRight stopWheelRight -> stopWheel;

                case Command.SnapUp snapUp -> snap;
                case Command.SnapDown snapDown -> snap;
                case Command.SnapLeft snapLeft -> snap;
                case Command.SnapRight snapRight -> snap;

                case Command.ShrinkGridUp shrinkGridUp -> shrinkGrid;
                case Command.ShrinkGridDown shrinkGridDown -> shrinkGrid;
                case Command.ShrinkGridLeft shrinkGridLeft -> shrinkGrid;
                case Command.ShrinkGridRight shrinkGridRight -> shrinkGrid;

                case Command.MoveGridUp moveGridUp -> moveGrid;
                case Command.MoveGridDown moveGridDown -> moveGrid;
                case Command.MoveGridLeft moveGridLeft -> moveGrid;
                case Command.MoveGridRight moveGridRight -> moveGrid;

                case Command.MoveToGridCenter moveToGridCenter_ -> moveToGridCenter;

                case Command.SavePosition savePosition_ -> savePosition;
                case Command.ClearPositionHistory clearPositionHistory_ -> clearPositionHistory;
                case Command.CycleNextPosition cycleNextPosition_ -> cycleNextPosition;
                case Command.CyclePreviousPosition cyclePreviousPosition_ -> cyclePreviousPosition;

                case Command.RemapCommand remap_ -> remap;
                // @formatter:on
            };
            map.computeIfAbsent(combo, combo1 -> new ArrayList<>()).add(command);
            return this;
        }

        public Map<Combo, List<Command>> commandsByCombo() {
            Map<Combo, List<Command>> commandsByCombo = new HashMap<>();
            add(commandsByCombo, to);
            add(commandsByCombo, startMove);
            add(commandsByCombo, stopMove);
            add(commandsByCombo, press);
            add(commandsByCombo, release);
            add(commandsByCombo, startWheel);
            add(commandsByCombo, stopWheel);
            add(commandsByCombo, snap);
            add(commandsByCombo, shrinkGrid);
            add(commandsByCombo, moveGrid);
            add(commandsByCombo, moveToGridCenter);
            add(commandsByCombo, savePosition);
            add(commandsByCombo, clearPositionHistory);
            add(commandsByCombo, cycleNextPosition);
            add(commandsByCombo, cyclePreviousPosition);
            add(commandsByCombo, remap);
            return commandsByCombo;
        }

        private void add(Map<Combo, List<Command>> commandsByCombo,
                         Map<Combo, List<Command>> otherCommandsByCombo) {
            for (Map.Entry<Combo, List<Command>> entry : otherCommandsByCombo.entrySet()) {
                Combo combo = entry.getKey();
                List<Command> commands = entry.getValue();
                commandsByCombo.computeIfAbsent(combo, combo1 -> new ArrayList<>())
                               .addAll(commands);
            }
        }

        public ComboMap build() {
            return new ComboMap(commandsByCombo());
        }

    }

}
