// Adapted by dishanhai from Link-Lyric (beipuo).
// Source: https://github.com/beipuo/Link-Lyric/tree/1a7cd274c3e7afbb33c08c66414ec0def944643b
// See META-INF/third-party/link-lyric for licenses and upstream notices.
package com.dishanhai.sh_better_ui.client.lyrics.common.hud.effect;

/**
 * 一帧内的全局特效参数。
 *
 * <p>由渲染器每帧填写一次，所有单元共享。特效只读。
 */
public final class EffectParam {

    /** 整行宽度。 */
    public float lineWidth;

    /** 当前已唱宽度。 */
    public float highlightWidth;

    /** 当前字号，像素。 */
    public float textSize;

    /** 当前播放位置，毫秒，与单元的起止时间同一时间轴。 */
    public long positionMs;

    /** 动画强度 0..1，空间受限时整体收缩幅度。 */
    public float strength = 1f;

    /** 行进度 0..1。 */
    public float progress() {
        return lineWidth > 0f ? Math.min(1f, Math.max(0f, highlightWidth / lineWidth)) : 0f;
    }
}
