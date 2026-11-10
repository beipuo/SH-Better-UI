// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel;

import com.dishanhai.sh_better_ui.kernel.api.CapabilityKey;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.VersionRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class IdentifiersAndVersionsTest {
    @Test
    void identifierRoundTripsAndOrdersByNamespaceThenPath() {
        UiId id = UiId.parse("example_mod:layout/dense.v2");
        assertEquals(new UiId("example_mod", "layout/dense.v2"), id);
        assertEquals("example_mod:layout/dense.v2", id.toString());
        assertTrue(id.compareTo(UiId.parse("example_mod:layout/grid")) < 0);
        assertTrue(id.compareTo(UiId.parse("other:layout/dense.v2")) < 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "layout", ":layout", "example:", "example:a:b", "Example:layout",
            "example:Layout", "example:has space", "example/path:id", "example:path\\"})
    void invalidIdsAreRejected(String value) {
        assertThrows(IllegalArgumentException.class, () -> UiId.parse(value));
    }

    @Test
    void capabilityIdentityIsIndependentOfProviderVersion() {
        UiId id = UiId.parse("example:layout");
        assertEquals(new CapabilityKey(id), new CapabilityKey(UiId.parse(id.toString())));
        assertNotEquals(new CapabilityKey(id), new CapabilityKey(UiId.parse("other:layout")));
        assertThrows(NullPointerException.class, () -> new CapabilityKey(null));
    }

    @Test
    void versionsCompareNumericallyAndRangesHaveExplicitBoundaries() {
        UiVersion start = UiVersion.parse("1.9.9");
        UiVersion end = UiVersion.parse("1.10.0");
        assertEquals(new UiVersion(1, 9, 9), start);
        assertEquals("1.9.9", start.toString());
        assertTrue(start.compareTo(end) < 0);
        VersionRange range = VersionRange.between(start, end);
        assertTrue(range.contains(start));
        assertFalse(range.contains(end));
        assertFalse(range.contains(UiVersion.parse("1.9.8")));
        assertTrue(VersionRange.atLeast(start).contains(end));
        assertTrue(VersionRange.ANY.contains(UiVersion.parse("0.0.0")));
        assertThrows(IllegalArgumentException.class, () -> VersionRange.between(end, start));
        assertThrows(IllegalArgumentException.class, () -> VersionRange.between(start, start));
        assertThrows(IllegalArgumentException.class, () -> new UiVersion(1, -1, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.2", "1.2.3.4", "v1.2.3", "01.2.3", "-1.2.3", " 1.2.3",
            "1.2.3-beta", "2147483648.0.0"})
    void invalidReleaseVersionsAreRejected(String value) {
        assertThrows(IllegalArgumentException.class, () -> UiVersion.parse(value));
    }
}
