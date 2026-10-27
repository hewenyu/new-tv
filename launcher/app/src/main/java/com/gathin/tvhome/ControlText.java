package com.gathin.tvhome;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;

/** The Control Center's status words, decided without Android so JUnit can test them. */
final class ControlText {
    private ControlText() {
    }

    /** The network that answers for the TV: Ethernet first, as the network pill does, then Wi-Fi, then any other. */
    enum Transport { NONE, ETHERNET, WIFI, OTHER }

    /** Which line the Network tile shows. */
    enum Network {
        /** 「有线 · 192.168.1.50」 */ WIRED,
        /** 「无线 · 192.168.1.50」 */ WIRELESS,
        /** 「已连接 · 192.168.1.50」: a transport that is neither (USB tethering, a phone network). */ CONNECTED,
        /** 「已连接，无法上网」: Android could not reach the internet through it (a captive portal, a dead uplink); only
         *  with wifi_status_warnings, since China firmware probes Google and leaves a working network unvalidated. */ NO_INTERNET,
        /** 「未连接」 */ OFFLINE
    }

    /** Whether candidate answers for the TV ahead of current: Ethernet, then Wi-Fi, then any other, then none. */
    static boolean preferred(Transport candidate, Transport current) {
        return rank(candidate) > rank(current);
    }

    private static int rank(Transport t) {
        if (t == Transport.ETHERNET) return 3;
        if (t == Transport.WIFI) return 2;
        return t == Transport.OTHER ? 1 : 0;
    }

    /** warnUnvalidated (wifi_status_warnings) off: an unvalidated network reads like any other connection. */
    static Network network(Transport transport, boolean validated, boolean warnUnvalidated) {
        if (transport == null || transport == Transport.NONE) return Network.OFFLINE;
        if (!validated && warnUnvalidated) return Network.NO_INTERNET;
        switch (transport) {
            case ETHERNET: return Network.WIRED;
            case WIFI: return Network.WIRELESS;
            default: return Network.CONNECTED;
        }
    }

    /** "label · address", or the label alone while the network has no IPv4 address yet. */
    static String withAddress(String label, String ipv4) {
        return ipv4 == null || ipv4.isEmpty() ? label : label + " · " + ipv4;
    }

    /** The first IPv4 address that is not loopback or link-local (169.254.x.x), or null. IPv6 is left out: too long for a tile. */
    static String ipv4(List<InetAddress> addresses) {
        if (addresses == null) return null;
        for (InetAddress a : addresses) {
            if (a instanceof Inet4Address && !a.isLoopbackAddress() && !a.isLinkLocalAddress()) return a.getHostAddress();
        }
        return null;
    }

    /** The volume row's value: "12 / 15", clamped to 0..max, or the muted word while muted (zero still reads "0 / 15"). */
    static String volume(int level, int max, boolean muted, String mutedWord) {
        if (muted) return mutedWord;
        int top = Math.max(0, max);
        return Math.max(0, Math.min(level, top)) + " / " + top;
    }

    /** Presses from silent to full, whatever the stream's range: the MiTV-ASTP0's music stream runs 0..100, and one
     *  index a press took a hundred presses there. */
    static final int VOLUME_PRESSES = 15;

    /** The level one press up (up true) or down leads to: max / VOLUME_PRESSES rounded up, at least 1, kept in 0..max. */
    static int volumeStep(int level, int max, boolean up) {
        int top = Math.max(0, max);
        int step = Math.max(1, (top + VOLUME_PRESSES - 1) / VOLUME_PRESSES);
        return Math.max(0, Math.min(top, level + (up ? step : -step)));
    }

    /** The volume bar's fill, 0 to max; empty while muted so the bar agrees with the word. */
    static int volumeFill(int level, int max, boolean muted) {
        return muted ? 0 : Math.max(0, Math.min(level, Math.max(0, max)));
    }
}
