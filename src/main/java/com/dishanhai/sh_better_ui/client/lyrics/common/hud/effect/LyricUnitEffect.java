// Adapted by dishanhai from Link-Lyric (beipuo).
// Source: https://github.com/beipuo/Link-Lyric/tree/1a7cd274c3e7afbb33c08c66414ec0def944643b
// See META-INF/third-party/link-lyric for licenses and upstream notices.
package com.dishanhai.sh_better_ui.client.lyrics.common.hud.effect;

/**
 * 逐单元特效契约。
 *
 * <p>渲染器把一行切成单元，每个单元依次经过已注册的特效，得到最终呈现变换。
 * 新增特效只需实现本接口并注册，不必改动绘制内核。
 *
 * <p>约定：特效无外部副作用，同参数下输出确定。
 */
public interface LyricUnitEffect {

    /** 特效名，用于增删与调试。 */
    String name();

    /**
     * 施加本特效。
     *
     * @param unit  单元描述，只读
     * @param param 本帧全局参数，只读
     * @param out   待叠加的变换，特效直接写入
     */
    void apply(EffectUnit unit, EffectParam param, UnitTransform out);
}
