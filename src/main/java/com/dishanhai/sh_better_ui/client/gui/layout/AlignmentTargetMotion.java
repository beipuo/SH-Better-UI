package com.dishanhai.sh_better_ui.client.gui.layout;

import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;

/** 山海：一个节点的目标对齐补偿。动画轨道只写位移补偿，控件尺寸与姿态仍由宿主更新。 */
final class AlignmentTargetMotion implements AutoCloseable {
    private final Spring.Preset preset;
    private InterruptibleAnimation horizontal;
    private InterruptibleAnimation vertical;

    AlignmentTargetMotion(Spring.Preset preset) { this.preset = preset; }

    double place(AlignmentLayout.Axis axis, double desired, boolean interpolate, long nowMs) {
        if (!Double.isFinite(desired)) return desired;
        if (Math.abs(desired) > Float.MAX_VALUE) {
            setTrack(axis, null);
            return desired;
        }
        float target = (float) desired;
        var track = axis == AlignmentLayout.Axis.HORIZONTAL ? horizontal : vertical;
        if (track == null) {
            setTrack(axis, new InterruptibleAnimation(preset, target));
            return desired;
        }
        if (!interpolate) {
            track.snapTo(target);
            return desired;
        }
        if (track.target() != target) track.animateTo(target, nowMs);
        double value = track.sample(nowMs);
        return track.running() ? value : desired;
    }

    private void setTrack(AlignmentLayout.Axis axis, InterruptibleAnimation track) {
        if (axis == AlignmentLayout.Axis.HORIZONTAL) horizontal = track;
        else vertical = track;
    }

    boolean running() {
        return (horizontal != null && horizontal.running()) || (vertical != null && vertical.running());
    }

    @Override
    public void close() {
        if (horizontal != null) horizontal.close();
        if (vertical != null) vertical.close();
    }
}
