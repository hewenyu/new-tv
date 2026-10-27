package com.gathin.tvhome;

import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ControlTextTest {
    private static InetAddress ip(String literal) throws UnknownHostException {
        return InetAddress.getByName(literal);   // a literal never touches DNS
    }

    @Test public void ethernetIsWiredAndWifiWireless() {
        assertEquals(ControlText.Network.WIRED, ControlText.network(ControlText.Transport.ETHERNET, true, true));
        assertEquals(ControlText.Network.WIRELESS, ControlText.network(ControlText.Transport.WIFI, true, true));
        assertEquals(ControlText.Network.CONNECTED, ControlText.network(ControlText.Transport.OTHER, true, true));
    }

    @Test public void unvalidatedIsNoInternetWithWarningsOn() {
        assertEquals(ControlText.Network.NO_INTERNET, ControlText.network(ControlText.Transport.ETHERNET, false, true));
        assertEquals(ControlText.Network.NO_INTERNET, ControlText.network(ControlText.Transport.WIFI, false, true));
    }

    @Test public void unvalidatedReadsAsConnectedWithWarningsOff() {
        assertEquals(ControlText.Network.WIRED, ControlText.network(ControlText.Transport.ETHERNET, false, false));
        assertEquals(ControlText.Network.WIRELESS, ControlText.network(ControlText.Transport.WIFI, false, false));
        assertEquals(ControlText.Network.CONNECTED, ControlText.network(ControlText.Transport.OTHER, false, false));
    }

    @Test public void noNetworkIsOfflineEitherWay() {
        assertEquals(ControlText.Network.OFFLINE, ControlText.network(ControlText.Transport.NONE, true, false));
        assertEquals(ControlText.Network.OFFLINE, ControlText.network(null, false, true));
    }

    @Test public void ethernetBeatsWifiBeatsOther() {
        assertTrue(ControlText.preferred(ControlText.Transport.ETHERNET, ControlText.Transport.WIFI));
        assertTrue(ControlText.preferred(ControlText.Transport.WIFI, ControlText.Transport.OTHER));
        assertTrue(ControlText.preferred(ControlText.Transport.OTHER, ControlText.Transport.NONE));
        assertFalse(ControlText.preferred(ControlText.Transport.WIFI, ControlText.Transport.ETHERNET));
        assertFalse(ControlText.preferred(ControlText.Transport.WIFI, ControlText.Transport.WIFI));
    }

    @Test public void addressFollowsTheLabel() {
        assertEquals("有线 · 192.168.1.50", ControlText.withAddress("有线", "192.168.1.50"));
        assertEquals("有线", ControlText.withAddress("有线", null));
        assertEquals("有线", ControlText.withAddress("有线", ""));
    }

    @Test public void picksTheFirstUsableIpv4() throws UnknownHostException {
        assertEquals("192.168.1.50", ControlText.ipv4(Arrays.asList(
                ip("fe80::1"), ip("2001:db8::5"), ip("127.0.0.1"), ip("169.254.3.4"), ip("192.168.1.50"), ip("10.0.0.2"))));
    }

    @Test public void noIpv4IsNull() throws UnknownHostException {
        assertNull(ControlText.ipv4(Collections.singletonList(ip("2001:db8::5"))));
        assertNull(ControlText.ipv4(Collections.emptyList()));
        assertNull(ControlText.ipv4(null));
    }

    @Test public void volumeReadsLevelOverMax() {
        assertEquals("12 / 15", ControlText.volume(12, 15, false, "静音"));
        assertEquals("0 / 15", ControlText.volume(0, 15, false, "静音"));
    }

    @Test public void mutedVolumeReadsTheWord() {
        assertEquals("静音", ControlText.volume(12, 15, true, "静音"));
        assertEquals(0, ControlText.volumeFill(12, 15, true));
    }

    @Test public void aPressIsAFifteenthOfTheRange() {
        // MiTV-ASTP0: 0..100 steps by 7, so 15 presses go from silent to full.
        assertEquals(21, ControlText.volumeStep(14, 100, true));
        assertEquals(7, ControlText.volumeStep(14, 100, false));
        assertEquals(100, ControlText.volumeStep(98, 100, true));
        assertEquals(0, ControlText.volumeStep(3, 100, false));
        // A 0..15 stream keeps one index a press.
        assertEquals(6, ControlText.volumeStep(5, 15, true));
        assertEquals(4, ControlText.volumeStep(5, 15, false));
        assertEquals(15, ControlText.volumeStep(15, 15, true));
        assertEquals(0, ControlText.volumeStep(0, 0, true));
    }

    @Test public void volumeIsClamped() {
        assertEquals("15 / 15", ControlText.volume(20, 15, false, "静音"));
        assertEquals("0 / 15", ControlText.volume(-3, 15, false, "静音"));
        assertEquals(15, ControlText.volumeFill(20, 15, false));
        assertEquals(7, ControlText.volumeFill(7, 15, false));
    }
}
