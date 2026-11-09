package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 山海：由客户端线程持有的 API 协作上下文，缓存各自的扩展并负责资源释放。
 * 已创建的扩展保持到上下文关闭，注册变更由尚未解析该扩展的上下文取得。
 */
public final class UiApiContext implements UiApi, AutoCloseable {
    private final UiExtensionRegistry extensions;
    private final Map<String, Object> instances = new LinkedHashMap<>();
    private final Set<String> resolving = new LinkedHashSet<>();
    private final List<AutoCloseable> resources = new ArrayList<>();
    private final Set<AutoCloseable> managed = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean closed;
    private ThemeScope themeScope;

    public UiApiContext() {
        this(ShBetterUiApi.extensions());
    }

    public UiApiContext(UiExtensionRegistry extensions) {
        this.extensions = Objects.requireNonNull(extensions);
    }

    @Override
    public UiExtensionRegistry extensions() {
        requireOpen();
        return extensions;
    }

    /** 工厂可通过同一个上下文取得依赖，循环依赖会报告完整的解析路径。 */
    public Object extension(String id) {
        requireOpen();
        Objects.requireNonNull(id);
        Object cached = instances.get(id);
        if (cached != null) return cached;
        if (!resolving.add(id)) {
            throw new IllegalStateException("Circular UI extension dependency: "
                    + String.join(" -> ", resolving) + " -> " + id);
        }
        try {
            Object instance = extensions.entry(id).create(this);
            if (closed) {
                var failure = new IllegalStateException("UI API context closed during extension creation");
                if (instance instanceof AutoCloseable resource && managed.add(resource)) {
                    try {
                        resource.close();
                    } catch (Throwable error) {
                        failure.addSuppressed(error);
                    }
                }
                throw failure;
            }
            if (instance instanceof AutoCloseable resource) manage(resource);
            instances.put(id, instance);
            return instance;
        } finally {
            resolving.remove(id);
            if (closed && resolving.isEmpty()) managed.clear();
        }
    }

    public <T> T extension(String id, Class<T> type) {
        return Objects.requireNonNull(type).cast(extension(id));
    }

    /** 同一个资源对象只登记一次，关闭时按创建依赖的相反顺序释放。 */
    public <T extends AutoCloseable> T manage(T resource) {
        requireOpen();
        Objects.requireNonNull(resource);
        if (managed.add(resource)) resources.add(resource);
        return resource;
    }

    /** 登记释放回调，Java 和脚本均可传入无参函数。 */
    public void onClose(Runnable cleanup) {
        Objects.requireNonNull(cleanup);
        manage(cleanup::run);
    }

    /** 创建随本次页面打开释放的独立属性轨道。GUI 重布局时可以复用。 */
    public InterruptibleAnimation animation(Spring.Preset preset, float initial) {
        requireOpen();
        return manage(motion().animation(preset, initial));
    }

    /** 创建随本次页面打开释放的碰撞布局，视口重建时可保留布局并重绑控件。 */
    public UiCollisionLayout collisionLayout(Bounds viewport) {
        requireOpen();
        return manage(new UiCollisionLayout(viewport));
    }

    /** 每个独立查询槽创建一个实例，离开页面时自动失效。 */
    public <T> UiRequest<T> query() {
        requireOpen();
        return manage(UiClientApi.request(UiRequest.Mode.QUERY));
    }

    public UiPreferences preferences() {
        requireOpen();
        var base = settings().preferences();
        if (themeScope == null || themeScope.isClosed()) return base;
        var dynamic = themeScope.preferences();
        return new UiPreferences(base.highContrast() || dynamic.highContrast(),
                base.reducedMotion() || dynamic.reducedMotion(),
                base.backgroundBlur() && !dynamic.lowPerformance(), base.feedback());
    }

    /** Opens the context's opt-in screen scope. It survives resize and closes with this context. */
    public ThemeScope themeScope() {
        requireOpen();
        if (themeScope == null || themeScope.isClosed()) {
            var scope = themes().global().child("screen");
            manage(scope);
            themeScope = scope;
        }
        return themeScope;
    }

    /** 每次绘制采样一次，将返回值传给本帧协作的多个组件。 */
    public UiFrame frame(GuiGraphics graphics, Bounds viewport, float partialTick) {
        requireOpen();
        Objects.requireNonNull(graphics);
        Objects.requireNonNull(viewport);
        return new UiFrame(graphics, viewport, interactions().mouseState(), preferences(), motion().nowMs(), partialTick);
    }

    public boolean isClosed() {
        return closed;
    }

    /** 先标记关闭，释放全部资源后再传播清理异常，重复关闭不执行清理。 */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        instances.clear();
        Throwable failure = null;
        for (int index = resources.size() - 1; index >= 0; index--) {
            try {
                resources.get(index).close();
            } catch (Throwable error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        resources.clear();
        if (resolving.isEmpty()) managed.clear();
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
        if (failure != null) throw new IllegalStateException("Could not close UI resources", failure);
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("UI API context is closed");
    }
}
