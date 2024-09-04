package mousemaster;

import java.util.Set;

/**
 * ^{alias} = ^{app1 app2} (all apps must not be active)
 * _{alias} = _{app1 | app2} (any one app must be active)
 */
public record AppAlias(String name, Set<App> apps) {
}
