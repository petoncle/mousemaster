package mousemaster;

import java.util.List;

/** The target extends the source: it takes the source's value unless it has its own. */
public record CascadeRule(List<String> sourceFieldNames, List<String> targetFieldNames) {}
