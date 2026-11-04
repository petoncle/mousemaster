package mousemaster;

import java.util.List;

/** The target takes each value it does not set from the first of its sources that sets it. */
public record CascadeRule(List<String> targetFieldNames, List<List<String>> sourceFieldNames) {}
