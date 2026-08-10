package mousemaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ModePropertyPath(List<String> fieldNames, ScreenFilter screenFilter) {

    public ModePropertyPath(List<String> fieldNames) {
        this(fieldNames, null);
    }

    public ModePropertyPath append(String... fieldNames) {
        List<String> newFieldNames = new ArrayList<>(this.fieldNames);
        Collections.addAll(newFieldNames, fieldNames);
        return new ModePropertyPath(List.copyOf(newFieldNames), screenFilter);
    }

    public ModePropertyPath withScreenFilter(ScreenFilter screenFilter) {
        return new ModePropertyPath(fieldNames, screenFilter);
    }

    public boolean equalsIgnoringScreenFilter(ModePropertyPath other) {
        return fieldNames.equals(other.fieldNames);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModePropertyPath that)) return false;
        return fieldNames.equals(that.fieldNames) &&
               Objects.equals(screenFilter, that.screenFilter);
    }

    @Override
    public int hashCode() {
        return 31 * fieldNames.hashCode() + Objects.hashCode(screenFilter);
    }

    @Override
    public String toString() {
        String base = String.join(".", fieldNames);
        return screenFilter == null ? base : base + "[" + screenFilter + "]";
    }

}
