package mousemaster;

import java.util.List;

public record CascadeRule(List<String> sourceFieldNames, List<String> targetFieldNames) {}
