// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import com.dishanhai.sh_better_ui.theme.token.TokenRegistry;
import com.dishanhai.sh_better_ui.theme.token.TokenType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ThemeResolverTest {
    private final TokenRegistry tokens = new TokenRegistry();
    private final ThemeToken<Float> size = tokens.register(new ThemeToken<>("example:size", TokenType.FLOAT, 3f));
    private final ThemeToken<Float> total = tokens.register(new ThemeToken<>("example:total", TokenType.FLOAT, 0f));
    private final ThemeToken<Float> unrelated = tokens.register(new ThemeToken<>("example:unrelated", TokenType.FLOAT, 7f));
    private final ThemeResolver resolver = new ThemeResolver();

    @Test
    void resolvesInheritanceAndOnlyRecomputesAffectedExpressions() {
        var evaluations = new AtomicInteger();
        var root = Theme.builder("example:base")
                .derive(total, lookup -> lookup.resolve(size) * 2)
                .derive(unrelated, lookup -> { evaluations.incrementAndGet(); return 5f; })
                .build();
        var child = Theme.builder("example:child").parent(root.id()).token(size, 8f).build();
        var registry = new ThemeRegistry().with(root).with(child);
        var first = resolver.resolve(registry, tokens.snapshot(), List.of(child.id()), null);
        assertEquals(16f, first.resolve(total));
        assertEquals("example:child", first.source(size));
        assertEquals("example:base", first.source(total));
        assertEquals(1, evaluations.get());
        var changed = Theme.builder(child.id()).parent(root.id()).token(size, 11f).build();
        var second = resolver.resolve(registry.with(changed, ThemeRegistry.DuplicatePolicy.REPLACE_CODE),
                tokens.snapshot(), List.of(child.id()), first);
        assertEquals(22f, second.resolve(total));
        assertEquals(5f, second.resolve(unrelated));
        assertEquals(16f, first.resolve(total));
        assertEquals(1, evaluations.get());
    }

    @Test
    void keepsLocalValuesAndDefaultsWhenParentIsMissing() {
        var child = Theme.builder("example:child").parent("example:missing").reference(total, size).build();
        var resolved = resolver.resolve(new ThemeRegistry().with(child), tokens.snapshot(), List.of(child.id()), null);
        assertEquals(3f, resolved.resolve(total));
        assertEquals(7f, resolved.resolve(unrelated));
        assertTrue(resolved.diagnostics().get(0).contains("example:missing"));
    }

    @Test
    void reportsBothKindsOfCycleWithPaths() {
        var child = Theme.builder("example:child").parent("example:base").build();
        var root = Theme.builder("example:base").parent(child.id()).build();
        var ancestry = assertThrows(ThemeResolutionException.class,
                () -> resolver.resolve(new ThemeRegistry().with(root).with(child),
                        tokens.snapshot(), List.of(child.id()), null));
        assertTrue(ancestry.getMessage().contains("example:child -> example:base -> example:child"));
        var cyclic = Theme.builder("example:tokens").reference(size, total).reference(total, size).build();
        var dependency = assertThrows(ThemeResolutionException.class,
                () -> resolver.resolve(new ThemeRegistry().with(cyclic), tokens.snapshot(), List.of(cyclic.id()), null));
        assertTrue(dependency.getMessage().contains("example:size -> example:total -> example:size"));
    }

    @Test
    void rejectsDuplicateIdsAndIncompatibleAliases() {
        var base = Theme.builder("example:base").build();
        assertThrows(IllegalArgumentException.class, () -> new ThemeRegistry().with(base).with(base));
        assertThrows(IllegalArgumentException.class,
                () -> tokens.register(new ThemeToken<>("example:size", TokenType.STRING, "wrong")));
        assertThrows(IllegalArgumentException.class, () -> Theme.builder("invalid id"));
        assertThrows(IllegalArgumentException.class, () -> Theme.builder("example:bad").token(size, Float.NaN));
    }
}
