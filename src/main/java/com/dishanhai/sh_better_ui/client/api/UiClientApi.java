package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.UiFrameScheduler;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** 山海：可传给 Java 或脚本的客户端门面，复用相同的分组、上下文和界面构建器。 */
public class UiClientApi implements UiApi {
    public UiApiContext context() {
        return new UiApiContext(extensions());
    }

    /** 登记后由开发者面板与按 ID 打开的入口共同发现。 */
    public PreviewRegistry.Registration registerDeveloperPreview(PreviewSpec spec) {
        return ShBetterUiApi.developerPreviews().register(spec);
    }

    /** 每个逻辑操作持有一个实例，跨页面保留未决操作。 */
    public <T> UiRequest<T> operation() {
        return request(UiRequest.Mode.OPERATION);
    }

    static <T> UiRequest<T> request(UiRequest.Mode mode) {
        var client = Minecraft.getInstance();
        return new UiRequest<>(mode, client::execute, () -> {
            var listener = client.getConnection();
            return listener != null && listener.getConnection().isConnected() ? listener : null;
        }, UiFrameScheduler::rawNowMs);
    }

    public UiScreenBuilder screen(String titleKey) {
        return screen(text(titleKey));
    }

    public UiScreenBuilder screen(Component title) {
        return new UiScreenBuilder(Objects.requireNonNull(title), this::context, this::onScreenCreated);
    }

    /** 在客户端执行阶段获取当前界面，适用于脚本回调或异步业务回执。 */
    public void open(Screen screen) {
        Objects.requireNonNull(screen);
        run(() -> {
            if (screen instanceof UiScreen composed) composed.open();
            else Minecraft.getInstance().setScreen(screen);
        });
    }

    public void run(Runnable task) {
        Minecraft.getInstance().execute(Objects.requireNonNull(task));
    }

    public Component text(String translationKey, Object... arguments) {
        return Component.translatable(Objects.requireNonNull(translationKey), arguments);
    }

    /** 扩展门面可在此将界面加入自己的生命周期。 */
    protected void onScreenCreated(UiScreen screen) {}
}
