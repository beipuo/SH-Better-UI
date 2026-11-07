package com.dishanhai.sh_better_ui.client.gui.selection;

import com.dishanhai.sh_better_ui.client.jei.JeiIntegration;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Ingredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.locale.Language;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.List;

/** 山海：客户端素材来源。动态来源应在内容、语言或标签变化后递增版本。 */
public interface IngredientCatalog {
    long revision();
    List<Ingredient> ingredients();

    default boolean isAvailable() { return true; }

    /** JEI 尚未提供当前世界的数据时不可用，素材列表为空。 */
    static IngredientCatalog jei() { return Catalogs.JEI; }

    /** 原生注册表来源包含物品默认形态和源流体。 */
    static IngredientCatalog registry() { return RegistryCatalog.INSTANCE; }

    /** JEI 可用时采用其可见素材，其余时间采用原生注册表。 */
    static IngredientCatalog automatic() { return Catalogs.AUTOMATIC; }

    /** 固定候选集的不可变快照。带 NBT 或组件的形态可通过 IngredientValues 构造。 */
    static IngredientCatalog of(Collection<? extends Ingredient> ingredients) {
        List<Ingredient> snapshot = List.copyOf(ingredients);
        return new IngredientCatalog() {
            @Override
            public long revision() { return IngredientValues.registryRevision(); }

            @Override
            public List<Ingredient> ingredients() { return snapshot; }
        };
    }
}

/** 山海：来源单例按需取得注册表，避免固定来源触发原生注册表初始化。 */
final class Catalogs {
    static final IngredientCatalog JEI = new IngredientCatalog() {
        @Override
        public long revision() { return JeiIntegration.revision() + IngredientValues.registryRevision(); }

        @Override
        public List<Ingredient> ingredients() { return JeiIntegration.ingredients(); }

        @Override
        public boolean isAvailable() { return JeiIntegration.isAvailable(); }
    };
    static final IngredientCatalog AUTOMATIC = new Automatic();

    private Catalogs() {}

    private static final class Automatic implements IngredientCatalog {
        private IngredientCatalog source;
        private long sourceRevision;
        private long revision;

        @Override
        public long revision() {
            IngredientCatalog current = JeiIntegration.isAvailable() ? JEI : IngredientCatalog.registry();
            long currentRevision = current.revision();
            if (source != current || sourceRevision != currentRevision) {
                source = current;
                sourceRevision = currentRevision;
                revision++;
            }
            return revision;
        }

        @Override
        public List<Ingredient> ingredients() {
            revision();
            return source.ingredients();
        }
    }
}

/** 山海：仅在来源版本变化时重新列举，日常查询保持常数时间。 */
final class RegistryCatalog implements IngredientCatalog {
    static final RegistryCatalog INSTANCE = new RegistryCatalog();
    private WeakReference<ClientLevel> level = new WeakReference<>(null);
    private Language language;
    private long changes = -1;
    private long revision;
    private long snapshotRevision = -1;
    private List<JeiIntegration.Ingredient> snapshot = List.of();

    private RegistryCatalog() {}

    @Override
    public long revision() {
        Minecraft client = Minecraft.getInstance();
        ClientLevel currentLevel = client == null ? null : client.level;
        Language currentLanguage = Language.getInstance();
        long currentChanges = IngredientValues.registryRevision();
        if (level.get() != currentLevel || language != currentLanguage || changes != currentChanges) {
            level = new WeakReference<>(currentLevel);
            language = currentLanguage;
            changes = currentChanges;
            revision++;
        }
        return revision;
    }

    @Override
    public List<JeiIntegration.Ingredient> ingredients() {
        long currentRevision = revision();
        if (snapshotRevision != currentRevision) {
            snapshot = IngredientValues.registryIngredients();
            snapshotRevision = currentRevision;
        }
        return snapshot;
    }
}
