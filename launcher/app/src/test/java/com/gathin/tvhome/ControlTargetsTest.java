package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ControlTargetsTest {
    private static final String XIAOMI = "com.xiaomi.mitv.settings/com.xiaomi.mitv.settings.entry.MainActivity";

    @Test public void readsKeyAndComponent() {
        Map<String, String> t = ControlTargets.parse(Collections.singletonList("adb=" + XIAOMI));
        assertEquals(XIAOMI, t.get(ControlTargets.ADB));
        assertEquals(1, t.size());
    }

    @Test public void trimsWhitespaceAndLowerCasesTheKey() {
        Map<String, String> t = ControlTargets.parse(Collections.singletonList("  Picture =  com.v.s/.Picture  "));
        assertEquals("com.v.s/.Picture", t.get(ControlTargets.PICTURE));
    }

    @Test public void leavesOutMalformedLines() {
        Map<String, String> t = ControlTargets.parse(Arrays.asList(
                "no equals sign", "=com.v.s/.A", "sound=", "sound=com.v.s", "sound=/.A", "sound=com.v.s/",
                "sound=com.v.s/.A/.B", "sound=com.v s/.A", null));
        assertTrue(t.isEmpty());
    }

    @Test public void lastLineForAKeyWins() {
        Map<String, String> t = ControlTargets.parse(Arrays.asList("sound=a.b/.One", "sound=a.b/.Two"));
        assertEquals("a.b/.Two", t.get(ControlTargets.SOUND));
    }

    @Test public void keepsUnknownKeys() {
        assertEquals("a.b/.C", ControlTargets.parse(Collections.singletonList("someday=a.b/.C")).get("someday"));
    }

    @Test public void nullOrEmptyListIsNoOverrides() {
        assertTrue(ControlTargets.parse(null).isEmpty());
        assertTrue(ControlTargets.parse(Collections.emptyList()).isEmpty());
    }

    @Test public void componentShape() {
        assertTrue(ControlTargets.isComponent(XIAOMI));
        assertTrue(ControlTargets.isComponent("a/.B"));
        assertFalse(ControlTargets.isComponent("a"));
        assertFalse(ControlTargets.isComponent(""));
    }
}
