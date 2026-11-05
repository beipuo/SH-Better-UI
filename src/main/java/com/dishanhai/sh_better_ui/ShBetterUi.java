package com.dishanhai.sh_better_ui;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** 山海：共用 UI 庫的共同入口，不載入任何客戶端或可選整合類別。 */
@Mod(ShBetterUi.MOD_ID)
public final class ShBetterUi {
    public static final String MOD_ID = "sh_better_ui";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ShBetterUi() {
    }
}
