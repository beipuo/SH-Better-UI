package com.dishanhai.sh_better_ui.client.api.preview;

import com.dishanhai.sh_better_ui.ShBetterUi;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 山海：开发者图库、查询及全屏入口共用的预览目录。 */
public final class PreviewRegistry {
    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }

    private static final String BUILTIN_NAMESPACE = ShBetterUi.MOD_ID + ":";
    private static final Comparator<PreviewSpec> ORDER = Comparator.comparing(PreviewSpec::category)
            .thenComparingInt(PreviewSpec::order).thenComparing(PreviewSpec::id);

    private final Map<String, Entry> byId = new HashMap<>();
    private volatile List<PreviewSpec> snapshot = List.of();

    public synchronized Registration register(PreviewSpec spec) {
        Objects.requireNonNull(spec);
        var entry = new Entry(spec);
        if (byId.putIfAbsent(spec.id(), entry) != null) {
            throw new IllegalStateException("Developer preview already registered: " + spec.id());
        }
        refreshSnapshot();
        return entry;
    }

    /** 按分类、顺序和 ID 排列，注册变化前复用同一个只读快照。 */
    public List<PreviewSpec> entries() {
        return snapshot;
    }

    public synchronized Optional<PreviewSpec> find(String id) {
        Objects.requireNonNull(id);
        var entry = byId.get(id.indexOf(':') < 0 ? BUILTIN_NAMESPACE + id : id);
        return entry == null ? Optional.empty() : Optional.of(entry.spec);
    }

    /** 兼容原有内置短 ID，其他模组的条目返回完整命名空间。 */
    public List<String> ids() {
        return entries().stream().map(PreviewSpec::id)
                .map(id -> id.startsWith(BUILTIN_NAMESPACE) ? id.substring(BUILTIN_NAMESPACE.length()) : id)
                .toList();
    }

    private void refreshSnapshot() {
        snapshot = byId.values().stream().map(entry -> entry.spec).sorted(ORDER).toList();
    }

    private synchronized void unregister(Entry entry) {
        if (byId.remove(entry.spec.id(), entry)) refreshSnapshot();
    }

    private final class Entry implements Registration {
        private final PreviewSpec spec;

        private Entry(PreviewSpec spec) {
            // 同一个声明撤销再登记时，活动图库也需要识别新的注册世代。
            this.spec = new PreviewSpec(spec.id(), spec.category(), spec.order(), spec.titleKey(),
                    spec.preferredHeight(), spec.toolbarRightInset(), spec.code(), spec.factory());
        }

        @Override
        public void close() {
            unregister(this);
        }
    }
}
