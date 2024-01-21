package mousemaster;

import java.util.Set;

/**
 * alias1=key11 key12
 * alias2=key21 key22
 * +alias1 -alias1 +alias2 = +key11 -key11 +key21 | +key11 -key11 +key22 | +key12 ...
 * ^{alias} = ^{key1 key2} (all must be unpressed)
 * _{alias} = _{key1*key2} (any combination)
 * _{rightshift alias} = _{rightshift key1*key2}. If alias is not single-key, then it counts as the "any combination of"
 * _{alias1*alias2} allowed only if single-key aliases
 * _{alias1 alias2} allowed only one of them is not a single-key alias
 */
public record Alias(String name, Set<Key> keys) {
}
