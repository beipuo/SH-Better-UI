package com.dishanhai.sh_better_ui.client.api;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** 山海：按命名空间登记独立 API 工厂，实例由每个客户端上下文按需创建。 */
public final class UiExtensionRegistry {
    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /** 同一个 ID 同时只接受一个注册，关闭返回的句柄后可重新注册。 */
    public synchronized <T> Registration register(String id, Class<T> type,
                                                   Function<UiApiContext, ? extends T> factory) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(type);
        Objects.requireNonNull(factory);
        int separator = id.indexOf(':');
        if (separator <= 0 || separator == id.length() - 1 || ResourceLocation.tryParse(id) == null) {
            throw new IllegalArgumentException("Expected a namespaced UI extension ID: " + id);
        }
        if (type.isPrimitive()) throw new IllegalArgumentException("UI extension type must be a reference type");
        if (entries.containsKey(id)) throw new IllegalStateException("UI extension already registered: " + id);
        Entry entry = new Entry(type, factory);
        entries.put(id, entry);
        return () -> unregister(id, entry);
    }

    /** 供脚本和无需声明返回类型的工厂使用。 */
    public Registration register(String id, Function<UiApiContext, ?> factory) {
        return register(id, Object.class, factory);
    }

    public synchronized boolean contains(String id) {
        return entries.containsKey(Objects.requireNonNull(id));
    }

    /** 按注册顺序返回不可修改的当前 ID 快照。 */
    public synchronized List<String> ids() {
        return List.copyOf(entries.keySet());
    }

    synchronized Entry entry(String id) {
        Entry entry = entries.get(Objects.requireNonNull(id));
        if (entry == null) throw new IllegalArgumentException("Unknown UI extension: " + id);
        return entry;
    }

    private synchronized void unregister(String id, Entry entry) {
        if (entries.get(id) == entry) entries.remove(id);
    }

    static final class Entry {
        private final Class<?> type;
        private final Function<UiApiContext, ?> factory;

        private Entry(Class<?> type, Function<UiApiContext, ?> factory) {
            this.type = type;
            this.factory = factory;
        }

        Object create(UiApiContext context) {
            return type.cast(Objects.requireNonNull(factory.apply(context), "UI extension factory returned null"));
        }
    }
}
