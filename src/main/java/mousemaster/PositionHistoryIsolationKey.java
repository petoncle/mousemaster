package mousemaster;

public sealed interface PositionHistoryIsolationKey {

    record NonePositionHistoryIsolationKey() implements PositionHistoryIsolationKey {

    }

    record ActiveAppPositionHistoryIsolationKey(App activeApp)
            implements PositionHistoryIsolationKey {
        @Override
        public String toString() {
            return activeApp.executableName();
        }
    }

}
