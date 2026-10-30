package mousemaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PositionHistory {

    private static final Logger logger = LoggerFactory.getLogger(PositionHistory.class);

    private final PositionHistoryKey key;
    private final int maxSize;
    private final List<Point> positions = new ArrayList<>();
    /**
     * Used for deterministic hint key sequences.
     */
    private final Map<Point, Integer> idByPosition = new HashMap<>();
    /**
     * The cells of the hints the positions were saved at: see {@link HintRectangles}.
     */
    private final Map<Point, Rectangle> hintCellByPosition = new HashMap<>();
    private int cycleIndex = 0;

    public PositionHistory(PositionHistoryKey key, int maxSize) {
        this.key = key;
        this.maxSize = maxSize;
    }

    public List<Point> positions() {
        return positions;
    }

    public int id(Point position) {
        return idByPosition.get(position);
    }

    /**
     * The cell of the hint the position was saved at, null if it was not saved at a hint.
     */
    public Rectangle hintCell(Point position) {
        return hintCellByPosition.get(position);
    }

    public Collection<Rectangle> hintCells() {
        return hintCellByPosition.values();
    }

    public void save(Point position, Rectangle hintCell) {
        if (positions.contains(position))
            return;
        if (positions.size() == maxSize)
            unsave(positions.getFirst());
        idByPosition.put(position, positions.size());
        positions.add(position);
        if (hintCell != null)
            hintCellByPosition.put(position, hintCell);
        cycleIndex = positions.size() - 1;
        logger.debug("Saved position (" + position.x() + ", " + position.y() + ") to " +
                     key);
    }

    public void unsave(Point position) {
        if (!positions.remove(position))
            return;
        int id = idByPosition.remove(position);
        hintCellByPosition.remove(position);
        idByPosition.replaceAll(
                (otherPosition, otherId) -> otherId < id ? otherId : otherId - 1);
        cycleIndex = positions.size() - 1;
    }

    public void clear() {
        positions.clear();
        idByPosition.clear();
        hintCellByPosition.clear();
        cycleIndex = 0;
        logger.debug("Reset " + key);
    }

    /**
     * The position to cycle to, null if there is none.
     */
    public Point cycle(int offset, Point from) {
        if (positions.isEmpty())
            return null;
        for (int positionIndex = 0; positionIndex < positions.size(); positionIndex++) {
            Point position = positions.get(positionIndex);
            if (Math.round(position.x()) == Math.round(from.x()) &&
                Math.round(position.y()) == Math.round(from.y())) {
                cycleIndex = positionIndex;
                break;
            }
        }
        cycleIndex = (cycleIndex + offset + positions.size()) % positions.size();
        return positions.get(cycleIndex);
    }

}
