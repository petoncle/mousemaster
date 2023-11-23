package jmouseable.jmouseable;

public record Attach(int gridRowCount, int gridColumnCount) {

    public static class AttachBuilder {
        private Integer gridRowCount;
        private Integer gridColumnCount;

        public AttachBuilder gridRowCount(int gridRowCount) {
            this.gridRowCount = gridRowCount;
            return this;
        }

        public AttachBuilder gridColumnCount(int gridColumnCount) {
            this.gridColumnCount = gridColumnCount;
            return this;
        }

        public Integer gridRowCount() {
            return gridRowCount;
        }

        public Integer gridColumnCount() {
            return gridColumnCount;
        }

        public Attach build() {
            return new Attach(gridRowCount, gridColumnCount);
        }
    }

}
