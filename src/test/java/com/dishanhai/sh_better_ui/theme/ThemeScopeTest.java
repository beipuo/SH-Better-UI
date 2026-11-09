// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.style.StyleInvalidation;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import com.dishanhai.sh_better_ui.theme.token.TokenRegistry;
import com.dishanhai.sh_better_ui.theme.token.TokenType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ThemeScopeTest {
    private final ThemeToken<Float> size = new ThemeToken<>("example:size", TokenType.FLOAT, 3f);
    private final ThemeToken<Float> total = new ThemeToken<>("example:total", TokenType.FLOAT, 0f);
    private final TokenRegistry tokens = new TokenRegistry();
    private final ThemeResolver resolver = new ThemeResolver();
    private final ScopeManager scopes;

    ThemeScopeTest() {
        tokens.register(size);
        tokens.register(total);
        var theme = Theme.builder("example:base").derive(total, v -> v.resolve(size) * 2).build();
        var base = resolver.resolve(new ThemeRegistry().with(theme), tokens.snapshot(), List.of(theme.id()), null);
        scopes = new ScopeManager(resolver, tokens::snapshot, base, error -> fail(error));
    }

    @Test
    void descendantBindingsRecomputeAndCoalesceWithoutUpdatingSibling() {
        var panelA = scopes.root().child("a");
        var panelB = scopes.root().child("b");
        var buttonA = panelA.child("button-a");
        var buttonB = panelA.child("button-b");
        var buttonC = panelB.child("button-c");
        var updates = new ArrayList<Set<StyleInvalidation>>();
        var owner = buttonA.invalidationOwner(updates::add);
        buttonA.bind(ThemeBinding.of(size), owner, StyleInvalidation.PAINT);
        var binding = buttonA.bind(ThemeBinding.of(total), owner, StyleInvalidation.LAYOUT);
        buttonB.bind(ThemeBinding.of(total), buttonB.invalidationOwner(updates::add), StyleInvalidation.PAINT);
        buttonC.bind(ThemeBinding.of(total), buttonC.invalidationOwner(t -> fail("Sibling invalidated")), StyleInvalidation.PAINT);

        panelA.configure(builder -> builder.override(size, 8f));
        panelA.configure(builder -> builder.override(size, 9f));
        assertEquals(18f, binding.value());
        assertEquals(6f, buttonC.resolve(total));
        assertTrue(updates.isEmpty());
        scopes.flush();
        assertEquals(2, updates.size());
        assertTrue(updates.contains(Set.of(StyleInvalidation.PAINT, StyleInvalidation.LAYOUT)));
        assertTrue(updates.contains(Set.of(StyleInvalidation.PAINT)));
        assertEquals(2, scopes.lastInvalidatedOwners());
    }

    @Test
    void reparentAndBlockingUseGlobalFallbackAndCloseReleasesPendingWork() {
        var panel = scopes.root().child("panel");
        panel.configure(builder -> builder.override(size, 8f));
        var nested = panel.child("nested");
        assertEquals(16f, nested.resolve(total));
        assertThrows(IllegalArgumentException.class, () -> panel.reparent(nested));
        nested.configure(builder -> builder.block(size));
        assertEquals(6f, nested.resolve(total));
        nested.configure(builder -> builder.allow(size));
        nested.reparent(scopes.root());
        assertEquals(6f, nested.resolve(total));
        var owner = nested.invalidationOwner(types -> fail("Closed scope delivered " + types));
        var binding = nested.bind(ThemeBinding.of(size), owner, StyleInvalidation.LAYOUT);
        nested.configure(builder -> builder.override(size, 9f));
        nested.close();
        scopes.flush();
        assertTrue(binding.isClosed());
        assertEquals(0, scopes.lastInvalidatedOwners());
    }

    @Test
    void invalidOverrideDoesNotLeavePartiallyUpdatedSubtree() {
        var panel = scopes.root().child("panel");
        var child = panel.child("child");
        child.configure(builder -> builder.override(total, 4f).derive(size, values -> values.resolve(total)));
        assertThrows(ThemeResolutionException.class,
                () -> panel.configure(builder -> builder.derive(total, values -> values.resolve(total))));
        assertEquals(3f, panel.resolve(size));
        assertEquals(4f, child.resolve(size));
    }
}
