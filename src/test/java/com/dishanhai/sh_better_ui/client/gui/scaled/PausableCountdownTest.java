package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：验证通知倒计时在多个暂停条件、重置及长时间间隔下的行为。 */
class PausableCountdownTest {
    @Test
    void overlappingPauseConditionsResumeOnlyAfterBothClear() {
        var countdown = new PausableCountdown(400L, 100L);

        countdown.update(200L, true); // 开始悬停
        assertEquals(0.75f, countdown.remainingFraction());
        countdown.update(500L, true); // 获得键盘焦点
        countdown.update(800L, true); // 离开悬停，仍有键盘焦点
        countdown.update(1_100L, false); // 焦点离开
        assertEquals(0.75f, countdown.remainingFraction());

        countdown.update(1_399L, false);
        assertFalse(countdown.expired());
        countdown.update(1_400L, false);
        assertTrue(countdown.expired());
        assertEquals(0f, countdown.remainingFraction());
    }

    @Test
    void resetRearmsWhileKeepingPauseAndZeroDurationNeverExpires() {
        var countdown = new PausableCountdown(0L, 0L);
        countdown.update(5_000L, true);
        assertFalse(countdown.expired());
        assertEquals(0f, countdown.remainingFraction());

        countdown.reset(100L, 5_000L);
        countdown.update(9_000L, false);
        assertEquals(1f, countdown.remainingFraction());
        countdown.update(9_100L, false);
        assertTrue(countdown.expired());
        assertThrows(IllegalArgumentException.class, () -> countdown.reset(-1L, 9_100L));
    }

    @Test
    void backwardSamplesDoNotChargeTimeTwiceAndLongIntervalsSaturate() {
        var countdown = new PausableCountdown(100L, 100L);
        countdown.update(150L, false);
        countdown.update(125L, false);
        countdown.update(150L, false);
        assertEquals(0.5f, countdown.remainingFraction());

        countdown.reset(Long.MAX_VALUE, Long.MIN_VALUE);
        countdown.update(Long.MAX_VALUE, false);
        assertTrue(countdown.expired());
        assertEquals(0f, countdown.remainingFraction());
    }
}
