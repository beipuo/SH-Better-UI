package com.dishanhai.sh_better_ui.client.gui.selection;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiHistory;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Ingredient;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** 山海：独立于画面的选取草稿，按素材变体键去重，确认或取消只能结束一次。 */
public final class IngredientSelection {
    private final int maxSelections;
    private final LinkedHashMap<String, Ingredient> selected = new LinkedHashMap<>();
    private final UiHistory<List<Ingredient>> history = new UiHistory<>(32);
    private boolean finished;

    public IngredientSelection(int maxSelections, Collection<? extends Ingredient> initial) {
        if (maxSelections < 1) throw new IllegalArgumentException("maxSelections must be positive");
        this.maxSelections = maxSelections;
        for (Ingredient ingredient : Objects.requireNonNull(initial)) {
            selected.put(Objects.requireNonNull(ingredient.key()), ingredient);
        }
        if (selected.size() > maxSelections) throw new IllegalArgumentException("initial selection exceeds limit");
    }

    public int maxSelections() { return maxSelections; }

    public int size() { return selected.size(); }

    public boolean isAtLimit() { return size() == maxSelections; }

    public boolean isFinished() { return finished; }

    public boolean contains(Ingredient ingredient) { return selected.containsKey(ingredient.key()); }

    /** 返回独立且不可修改的列表；修改草稿不会改变先前返回的列表。 */
    public List<Ingredient> values() { return List.copyOf(selected.values()); }

    /** 已选素材再次选用时移除；单选替换旧值，多选达到上限时拒绝添加。 */
    public boolean toggle(Ingredient ingredient) {
        Objects.requireNonNull(ingredient);
        if (finished) return false;
        String key = Objects.requireNonNull(ingredient.key());
        if (maxSelections > 1 && isAtLimit() && !selected.containsKey(key)) return false;
        history.remember(values());
        if (selected.remove(key) != null) return true;
        if (maxSelections == 1) selected.clear();
        selected.put(key, ingredient);
        return true;
    }

    public void clear() {
        if (finished || selected.isEmpty()) return;
        history.remember(values());
        selected.clear();
    }

    public boolean canUndo() { return history.canRestore(); }

    /** 恢复最近一次本地编辑前的草稿；可用来源由调用方重新核对。 */
    public boolean undo() {
        List<Ingredient> snapshot = history.restore().orElse(null);
        if (snapshot == null) return false;
        selected.clear();
        for (Ingredient ingredient : snapshot) selected.put(ingredient.key(), ingredient);
        return true;
    }

    /** 仅按完整来源和业务条件核对草稿，调用方不应传入搜索或当前分类条件。 */
    public boolean reconcile(Collection<? extends Ingredient> source, Predicate<? super Ingredient> allowed) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(allowed);
        if (finished || selected.isEmpty()) return false;
        var current = new HashMap<String, Ingredient>();
        for (Ingredient ingredient : source) {
            if (selected.containsKey(ingredient.key()) && ingredient.visible() && allowed.test(ingredient)) {
                current.putIfAbsent(ingredient.key(), ingredient);
            }
        }
        boolean changed = false;
        var entries = selected.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            Ingredient replacement = current.get(entry.getKey());
            if (replacement == null) {
                entries.remove();
                changed = true;
            } else if (entry.getValue() != replacement) {
                entry.setValue(replacement);
                changed = true;
            }
        }
        return changed;
    }

    /** 空列表也可确认，用于清空调用方的筛选；取消和重复结束均不产生提交值。 */
    public Optional<List<Ingredient>> finish(boolean confirmed) {
        if (finished) return Optional.empty();
        finished = true;
        history.clear();
        return confirmed ? Optional.of(values()) : Optional.empty();
    }
}
