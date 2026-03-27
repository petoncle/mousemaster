package mousemaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ModePropertyPath(List<String> fieldNames) {

    public ModePropertyPath append(String... fieldNames) {
        List<String> newFieldNames = new ArrayList<>(this.fieldNames);
        Collections.addAll(newFieldNames, fieldNames);
        return new ModePropertyPath(List.copyOf(newFieldNames));
    }

    @Override
    public String toString() {
        return String.join(".", fieldNames);
    }

}
