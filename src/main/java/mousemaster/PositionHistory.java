package mousemaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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
    private int idCount = 0;
    private final Map<Point, Integer> idByPosition = new HashMap<>();
    private int cycleIndex = 0;

    public PositionHistory(PositionHistoryKey key, int maxSize) {
        this.key = key;
        this.maxSize = maxSize;
    }

    public List<Point> positions() {
        return positions;
    }

    public int id(Point position) {
        return idByPosition.get(position) % maxSize;
    }

    public void save(Point position) {
        if (positions.contains(position))
            return;
        idByPosition.put(position, idCount);
        if (idCount == Integer.MAX_VALUE)
            idCount = 0;
        else
            idCount++;
        if (positions.size() == maxSize)
            positions.removeFirst();
        positions.add(position);
        cycleIndex = positions.size() - 1;
        logger.debug("Saved position (" + position.x() + ", " + position.y() + ") to " +
                     key);
    }

    public void unsave(Point position) {
        if (!positions.remove(position))
            return;
        int id = idByPosition.remove(position);
        Map<Point, Integer> newIdByPosition = new HashMap<>();
        for (Map.Entry<Point, Integer> entry : idByPosition.entrySet()) {
            int otherId = entry.getValue();
            newIdByPosition.put(entry.getKey(), otherId < id ? otherId : otherId - 1);
        }
        idByPosition.clear();
        idByPosition.putAll(newIdByPosition);
        idCount--;
        cycleIndex = positions.size() - 1;
    }

    public void clear() {
        positions.clear();
        idByPosition.clear();
        idCount = 0;
        cycleIndex = 0;
        logger.debug("Reset " + key);
    }

    /**
     * The position to cycle to, null if there is none.
     */
    public Point cycle(int offset, int mouseX, int mouseY) {
        if (positions.isEmpty())
            return null;
        for (int positionIndex = 0; positionIndex < positions.size(); positionIndex++) {
            Point position = positions.get(positionIndex);
            if (Math.round(position.x()) == mouseX &&
                Math.round(position.y()) == mouseY) {
                cycleIndex = positionIndex;
                break;
            }
        }
        cycleIndex = (cycleIndex + offset + positions.size()) % positions.size();
        return positions.get(cycleIndex);
    }

}
