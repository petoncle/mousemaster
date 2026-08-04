package mousemaster;

import mousemaster.PositionHistoryIsolationKey.NonePositionHistoryIsolationKey;

public record PositionHistoryKey(String positionHistoryName,
                                 PositionHistoryIsolationKey isolationKey) {

    @Override
    public String toString() {
        return isolationKey instanceof NonePositionHistoryIsolationKey ?
                positionHistoryName : positionHistoryName + " (" + isolationKey + ")";
    }

}
