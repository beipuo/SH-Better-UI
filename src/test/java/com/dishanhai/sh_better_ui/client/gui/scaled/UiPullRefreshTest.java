package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证刷新手势和异步回执。 */
class UiPullRefreshTest {
    @Test
    void graphicStatesFollowTheGestureAndAcknowledgment() {
        var requests = new java.util.ArrayList<Integer>();
        var refresh = new UiPullRefresh(requests::add);
        var preferences = UiPreferences.DEFAULT;
        assertEquals(GraphicIcon.PULL_DOWN, refresh.sample(0, preferences).icon());
        assertTrue(refresh.begin(0, 10, 10, true, 10));
        refresh.drag(0, 10, 30, 20);
        assertEquals(GraphicIcon.PULL_DOWN, refresh.sample(20, preferences).icon());
        refresh.drag(0, 10, 65, 30);
        assertEquals(GraphicIcon.REFRESH, refresh.sample(30, preferences).icon());
        refresh.release(0, 10, 65, true, 40);
        assertEquals(GraphicIcon.REFRESH, refresh.sample(40, preferences).icon());
        assertTrue(refresh.complete(requests.getFirst(), ButtonFeedback.Tone.FAILED, 50));
        assertEquals(GraphicIcon.ERROR, refresh.sample(50, preferences).icon());
    }

    @Test
    void usesTheFinalReleasePositionAndDispatchesOnlyOnce() {
        var requests = new ArrayList<Integer>();
        var refresh = new UiPullRefresh(requests::add);

        assertTrue(refresh.begin(0, 10, 10, true, 0));
        assertTrue(refresh.drag(0, 10, 57, 10));
        assertEquals(UiPullRefresh.Phase.PULLING, refresh.sample(10, UiPreferences.DEFAULT).phase());
        assertTrue(requests.isEmpty());

        assertTrue(refresh.release(0, 10, 58, true, 11));
        assertFalse(refresh.release(0, 10, 59, true, 12));
        assertEquals(1, requests.size());
        assertEquals(ButtonFeedback.Status.PENDING, refresh.sample(12, UiPreferences.DEFAULT).status());
    }

    @Test
    void onlyCapturesAPrimaryPressAtTheTopAndRequiresADownwardPull() {
        var requests = new ArrayList<Integer>();
        var refresh = new UiPullRefresh(requests::add);

        assertFalse(refresh.begin(1, 10, 10, true, 0));
        assertFalse(refresh.begin(0, 10, 10, false, 1));
        assertFalse(refresh.begin(0, Double.NaN, 10, true, 2));
        assertTrue(refresh.begin(0, 10, 10, true, 3));
        assertFalse(refresh.drag(1, 10, 80, 4));
        assertFalse(refresh.release(1, 10, 80, true, 5));
        assertTrue(refresh.drag(0, 60, 12, 6));
        assertTrue(refresh.release(0, 60, 100, true, 7));
        assertTrue(requests.isEmpty());
    }

    @Test
    void reversesOrCancelsBeforeSendingAndChecksTheViewportAtRelease() {
        var requests = new ArrayList<Integer>();
        var refresh = new UiPullRefresh(requests::add);

        assertTrue(refresh.begin(0, 10, 10, true, 0));
        assertTrue(refresh.drag(0, 10, 80, 1));
        assertEquals(UiPullRefresh.Phase.ARMED, refresh.sample(1, UiPreferences.DEFAULT).phase());
        assertTrue(refresh.release(0, 10, Math.nextDown(58d), true, 2));

        assertTrue(refresh.begin(0, 10, 10, true, 3));
        assertTrue(refresh.release(0, 10, 80, false, 4));

        assertTrue(refresh.begin(0, 10, 10, true, 5));
        assertTrue(refresh.drag(0, 10, 80, 6));
        refresh.cancelGesture(7);
        assertFalse(refresh.isDragging());
        assertFalse(refresh.release(0, 10, 80, true, 8));

        assertTrue(refresh.begin(0, 10, 10, true, 9));
        assertTrue(refresh.release(0, 10, Double.NaN, true, 10));
        assertFalse(refresh.isDragging());
        assertTrue(requests.isEmpty());
    }

    @Test
    void previewsOnlyAValidDropButUsesTheFinalReleaseLocation() {
        var requests = new ArrayList<Integer>();
        var refresh = new UiPullRefresh(requests::add);
        assertTrue(refresh.begin(0, 0, 0, true, 0));
        assertTrue(refresh.drag(0, 0, 60, true, 1));
        assertEquals(UiPullRefresh.Phase.ARMED, refresh.sample(1, UiPreferences.DEFAULT).phase());
        assertTrue(refresh.drag(0, 12, 60, false, 2));
        assertEquals(UiPullRefresh.Phase.PULLING, refresh.sample(2, UiPreferences.DEFAULT).phase());
        assertTrue(refresh.release(0, 0, 60, true, 3));
        assertEquals(1, requests.size());
    }

    @Test
    void acceptsSynchronousCompletionAndDebouncesRepeatedActivation() {
        var refreshRef = new AtomicReference<UiPullRefresh>();
        var requests = new AtomicInteger();
        var refresh = new UiPullRefresh(id -> {
            requests.incrementAndGet();
            assertTrue(refreshRef.get().complete(id, ButtonFeedback.Tone.SUCCESS, 1));
            assertFalse(refreshRef.get().complete(id, ButtonFeedback.Tone.FAILED, 1));
        });
        refreshRef.set(refresh);

        assertTrue(refresh.requestRefresh(0));
        assertFalse(refresh.requestRefresh(2));
        assertEquals(1, requests.get());
        assertEquals(ButtonFeedback.Status.SUCCEEDED, refresh.sample(2, UiPreferences.DEFAULT).status());
    }

    @Test
    void waitsForTheCurrentRequestAndAllowsRetryAfterTimeout() {
        var requests = new ArrayList<Integer>();
        var refresh = new UiPullRefresh(new UiPullRefresh.Config(4, 30, 20, 500), requests::add);

        assertTrue(refresh.requestRefresh(0));
        assertFalse(refresh.begin(0, 0, 0, true, 10));
        assertFalse(refresh.requestRefresh(499));
        refresh.cancelGesture(499);
        assertEquals(ButtonFeedback.Status.PENDING, refresh.sample(499, UiPreferences.DEFAULT).status());
        assertFalse(refresh.complete(requests.getFirst(), ButtonFeedback.Tone.SUCCESS, 500));
        assertEquals(ButtonFeedback.Status.TIMED_OUT, refresh.sample(500, UiPreferences.DEFAULT).status());

        assertTrue(refresh.requestRefresh(501));
        assertFalse(refresh.complete(requests.getFirst(), ButtonFeedback.Tone.SUCCESS, 502));
        assertTrue(refresh.complete(requests.getLast(), ButtonFeedback.Tone.FAILED, 503));
        assertEquals(ButtonFeedback.Status.FAILED, refresh.sample(503, UiPreferences.DEFAULT).status());
    }

    @Test
    void invalidatesOldRequestsWhenTheListIsResetOrReplaced() {
        var requests = new ArrayList<Integer>();
        var refresh = new UiPullRefresh(requests::add);
        var other = new UiPullRefresh(requests::add);

        assertTrue(refresh.requestRefresh(0));
        refresh.reset(1);
        assertEquals(ButtonFeedback.Status.READY, refresh.sample(1, UiPreferences.DEFAULT).status());
        assertTrue(refresh.requestRefresh(2));
        assertTrue(other.requestRefresh(2));
        assertFalse(refresh.complete(requests.getFirst(), ButtonFeedback.Tone.SUCCESS, 3));
        assertFalse(refresh.complete(requests.getLast(), ButtonFeedback.Tone.SUCCESS, 3));
        assertTrue(refresh.complete(requests.get(1), ButtonFeedback.Tone.SUCCESS, 3));
        assertEquals(ButtonFeedback.Status.PENDING, other.sample(3, UiPreferences.DEFAULT).status());
    }

    @Test
    void reducedMotionKeepsGestureFeedbackWithoutMovingTheIndicator() {
        var refresh = new UiPullRefresh(id -> {});
        assertTrue(refresh.begin(0, 0, 0, true, 0));
        assertTrue(refresh.drag(0, 0, 48, 1));

        assertEquals(32, refresh.sample(1, UiPreferences.DEFAULT).reveal());
        var frame = refresh.sample(1, new UiPreferences(true, true, false));
        assertEquals(0, frame.reveal());
        assertEquals(1, frame.progress());
        assertEquals(UiPullRefresh.Phase.ARMED, frame.phase());
    }
}
