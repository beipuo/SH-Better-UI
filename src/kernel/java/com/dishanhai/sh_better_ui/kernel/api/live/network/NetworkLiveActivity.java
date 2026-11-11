// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.live.network;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Server-published activity data. Text fields are plain, single-line UTF-8 strings. */
public record NetworkLiveActivity(UiId id, String title, String detail, Status status, float progress,
                                  ActionOffer action) {
    public static final int MAX_ID_BYTES = 96;
    public static final int MAX_TITLE_BYTES = 192;
    public static final int MAX_DETAIL_BYTES = 384;
    public static final int MAX_ACTION_LABEL_BYTES = 96;
    public static final float INDETERMINATE = -1;

    public enum Status { RUNNING, SUCCEEDED, FAILED, CANCELLED }

    /** A single action bound to a server-side permission check and handler when published. */
    public record ActionOffer(UiId id, String label) {
        public ActionOffer {
            requireId(id);
            requireText(label, MAX_ACTION_LABEL_BYTES, false);
        }
    }

    public NetworkLiveActivity {
        requireId(id);
        requireText(title, MAX_TITLE_BYTES, false);
        requireText(detail, MAX_DETAIL_BYTES, true);
        Objects.requireNonNull(status, "status");
        if (!Float.isFinite(progress) || progress != INDETERMINATE && (progress < 0 || progress > 1)) {
            throw new IllegalArgumentException("Progress must be -1 or between 0 and 1");
        }
        if (action != null && !id.namespace().equals(action.id().namespace())) {
            throw new IllegalArgumentException("Activity and action must share a namespace");
        }
    }

    private static void requireId(UiId id) {
        Objects.requireNonNull(id, "id");
        if ((long) id.namespace().length() + id.path().length() + 1 > MAX_ID_BYTES) {
            throw new IllegalArgumentException("Network ID exceeds " + MAX_ID_BYTES + " bytes");
        }
    }

    private static void requireText(String value, int maxBytes, boolean allowEmpty) {
        Objects.requireNonNull(value, "text");
        if (value.length() > maxBytes || !allowEmpty && value.isBlank()
                || value.getBytes(StandardCharsets.UTF_8).length > maxBytes
                || value.codePoints().anyMatch(NetworkLiveActivity::invalidTextCodePoint)) {
            throw new IllegalArgumentException("Expected plain text of at most " + maxBytes + " UTF-8 bytes");
        }
    }

    private static boolean invalidTextCodePoint(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint) || codePoint == 0xA7 || type == Character.SURROGATE
                || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }
}
