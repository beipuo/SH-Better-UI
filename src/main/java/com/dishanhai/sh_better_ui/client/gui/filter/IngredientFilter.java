package com.dishanhai.sh_better_ui.client.gui.filter;

import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Ingredient;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Kind;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** 山海：供 JEI 与自建素材库共用的不可变筛选条件。同类取并集，不同类取交集。 */
public final class IngredientFilter implements Predicate<Ingredient> {
    private final Set<Kind> kinds;
    private final Set<ResourceLocation> ids;
    private final Set<String> mods;
    private final Set<ResourceLocation> tags;

    private IngredientFilter(Set<Kind> kinds, Set<ResourceLocation> ids,
                             Set<String> mods, Set<ResourceLocation> tags) {
        this.kinds = Set.copyOf(kinds);
        this.ids = Set.copyOf(ids);
        this.mods = mods.stream().map(namespace -> {
            String normalized = namespace.strip().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) throw new IllegalArgumentException("namespace must not be blank");
            return normalized;
        }).collect(Collectors.toUnmodifiableSet());
        this.tags = Set.copyOf(tags);
    }

    public static IngredientFilter all() {
        return new IngredientFilter(Set.of(), Set.of(), Set.of(), Set.of());
    }

    public static IngredientFilter items() {
        return all().withKinds(Set.of(Kind.ITEM));
    }

    public static IngredientFilter fluids() {
        return all().withKinds(Set.of(Kind.FLUID));
    }

    public IngredientFilter withKinds(Set<Kind> kinds) {
        return new IngredientFilter(kinds, ids, mods, tags);
    }

    public IngredientFilter withIds(Set<ResourceLocation> ids) {
        return new IngredientFilter(kinds, ids, mods, tags);
    }

    /** namespace 忽略两端空白与大小写，匹配时使用完整 namespace。 */
    public IngredientFilter withMods(Set<String> namespaces) {
        return new IngredientFilter(kinds, ids, namespaces, tags);
    }

    public IngredientFilter withTags(Set<ResourceLocation> tags) {
        return new IngredientFilter(kinds, ids, mods, tags);
    }

    public Set<Kind> kinds() {
        return kinds;
    }

    public Set<ResourceLocation> ids() {
        return ids;
    }

    public Set<String> mods() {
        return mods;
    }

    public Set<ResourceLocation> tags() {
        return tags;
    }

    /** 空集合表示该类条件不限；名称和 ID 文本查询由 SearchIndex 处理。 */
    @Override
    public boolean test(Ingredient ingredient) {
        Objects.requireNonNull(ingredient, "ingredient");
        return (kinds.isEmpty() || kinds.contains(ingredient.kind()))
                && (ids.isEmpty() || ids.contains(ingredient.id()))
                && (mods.isEmpty() || mods.contains(ingredient.id().getNamespace()))
                && (tags.isEmpty() || ingredient.tags().stream().anyMatch(tags::contains));
    }
}
