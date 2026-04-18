package mousemaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ModePropertyPath(List<String> fieldNames, ViewportFilter viewportFilter) {

    public ModePropertyPath(List<String> fieldNames) {
        this(fieldNames, null);
    }

    public ModePropertyPath append(String... fieldNames) {
        List<String> newFieldNames = new ArrayList<>(this.fieldNames);
        Collections.addAll(newFieldNames, fieldNames);
        return new ModePropertyPath(List.copyOf(newFieldNames), viewportFilter);
    }

    public ModePropertyPath withViewportFilter(ViewportFilter viewportFilter) {
        return new ModePropertyPath(fieldNames, viewportFilter);
    }

    public boolean equalsIgnoringViewportFilter(ModePropertyPath other) {
        return fieldNames.equals(other.fieldNames);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModePropertyPath that)) return false;
        return fieldNames.equals(that.fieldNames) &&
               Objects.equals(viewportFilter, that.viewportFilter);
    }

    @Override
    public int hashCode() {
        return 31 * fieldNames.hashCode() + Objects.hashCode(viewportFilter);
    }

    @Override
    public String toString() {
        String base = String.join(".", fieldNames);
        return viewportFilter == null ? base : base + "[" + viewportFilter + "]";
    }

}
