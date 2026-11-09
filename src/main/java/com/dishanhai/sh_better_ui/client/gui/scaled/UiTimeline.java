package com.dishanhai.sh_better_ui.client.gui.scaled;

/**
 * 山海：按帧取样的时间轴。画面只依赖 Frame，跳帧和反向查看不需要重放动画。
 * <p>每个播放器持有一个实例，在客户端线程调用。所有时间使用同一单调毫秒时钟，
 * 例如 PageTransition.nowMs()。帧从 0 开始，总帧数为 N 时末帧为 N-1。
 * 本 API 提供原生 UI 动画，不包含 React 执行环境或视频编码。</p>
 */
public final class UiTimeline {
    public enum State { READY, PLAYING, PAUSED, ENDED }

    /** 不可变帧快照；插值不受播放器历史和渲染 FPS 影响。 */
    public record Frame(int index, int fps, int durationFrames, State state) {
        public Frame {
            if (fps <= 0 || durationFrames <= 0 || index < 0 || index >= durationFrames || state == null) {
                throw new IllegalArgumentException();
            }
        }

        public double seconds() { return index / (double) fps; }

        /** 线性插值，区间外固定为两端值；允许输出递减，输入帧区间必须递增。 */
        public float interpolate(int fromFrame, int toFrame, float fromValue, float toValue) {
            if (fromFrame >= toFrame || !Float.isFinite(fromValue) || !Float.isFinite(toValue)) {
                throw new IllegalArgumentException();
            }
            double progress = UiMath.clamp(((double) index - fromFrame) / ((double) toFrame - fromFrame), 0d, 1d);
            return (float) (fromValue + ((double) toValue - fromValue) * progress);
        }
    }

    /** 在 [from, from+durationFrames) 显示内容。局部帧可为负数，便于判断尚未开始。 */
    public record Sequence(int from, int durationFrames) {
        public Sequence {
            if (from < 0 || durationFrames <= 0 || (long) from + durationFrames > Integer.MAX_VALUE) {
                throw new IllegalArgumentException();
            }
        }

        public int localFrame(Frame frame) { return frame.index() - from; }

        public boolean contains(Frame frame) {
            int local = localFrame(frame);
            return local >= 0 && local < durationFrames;
        }
    }

    private final int fps;
    private final int durationFrames;
    private double position;
    private double anchorFrame;
    private long anchorMs;
    private State state = State.READY;

    public UiTimeline(int fps, int durationFrames) {
        if (fps <= 0 || durationFrames <= 0) throw new IllegalArgumentException();
        this.fps = fps;
        this.durationFrames = durationFrames;
    }

    public int fps() { return fps; }
    public int durationFrames() { return durationFrames; }

    /** 续播；结束后从第 0 帧重播。重复 play 不重置时钟。 */
    public void play(long nowMs) {
        if (state == State.PLAYING) return;
        if (state == State.ENDED) position = 0d;
        anchorFrame = position;
        anchorMs = nowMs;
        state = durationFrames == 1 ? State.ENDED : State.PLAYING;
    }

    /** 先取样命令时刻再暂停，保留不足一帧的时间。就绪／结束状态保持原样。 */
    public void pause(long nowMs) {
        advance(nowMs);
        if (state == State.PLAYING) state = State.PAUSED;
    }

    /** 跳到指定帧并暂停；超界取端点，末帧标记为结束。 */
    public void seek(int index) {
        position = UiMath.clamp(index, 0, durationFrames - 1);
        state = position == durationFrames - 1 ? State.ENDED : State.PAUSED;
    }

    /** 恢复就绪第 0 帧，下次播放重新建立时钟。 */
    public void reset() {
        position = 0d;
        state = State.READY;
    }

    public Frame sample(long nowMs) {
        advance(nowMs);
        return new Frame((int) position, fps, durationFrames, state);
    }

    private void advance(long nowMs) {
        if (state != State.PLAYING) return;
        double elapsed = Math.max(0d, (double) nowMs - anchorMs);
        position = Math.max(position, Math.min(durationFrames - 1d, anchorFrame + elapsed * fps / 1000d));
        if (position >= durationFrames - 1d) state = State.ENDED;
    }
}
