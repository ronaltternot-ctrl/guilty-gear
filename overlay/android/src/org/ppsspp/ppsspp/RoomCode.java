package org.ppsspp.ppsspp;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Room codes for local multiplayer: the host's IPv4 address plus a 3-bit check, written as
 * 7 characters of Crockford base32 ("K7M2-QXA"). Easy to read aloud, and typos are caught.
 */
final class RoomCode {
	private RoomCode() {}

	private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
	static final int LENGTH = 7;

	private static int check(int ip) {
		int b0 = (ip >>> 24) & 0xFF;
		int b1 = (ip >>> 16) & 0xFF;
		int b2 = (ip >>> 8) & 0xFF;
		int b3 = ip & 0xFF;
		return (b0 + 3 * b1 + 5 * b2 + 7 * b3 + 1) & 7;
	}

	/** "192.168.1.37" -> "XXXX-XXX". */
	static String encode(byte[] ipv4) {
		int ip = ((ipv4[0] & 0xFF) << 24) | ((ipv4[1] & 0xFF) << 16) | ((ipv4[2] & 0xFF) << 8) | (ipv4[3] & 0xFF);
		long value = ((ip & 0xFFFFFFFFL) << 3) | check(ip);   // 35 bits
		char[] out = new char[LENGTH];
		for (int i = LENGTH - 1; i >= 0; i--) {
			out[i] = ALPHABET.charAt((int) (value & 31));
			value >>>= 5;
		}
		String s = new String(out);
		return s.substring(0, 4) + "-" + s.substring(4);
	}

	/** Returns the IPv4 address as text, or null if the code is not valid. */
	static String decode(String code) {
		if (code == null) {
			return null;
		}
		StringBuilder clean = new StringBuilder();
		for (char c : code.toUpperCase(Locale.ROOT).toCharArray()) {
			if (c == ' ' || c == '-') {
				continue;
			}
			// Common confusions when reading a code aloud or from a screen.
			if (c == 'O') c = '0';
			if (c == 'I' || c == 'L') c = '1';
			if (c == 'U') c = 'V';
			clean.append(c);
		}
		if (clean.length() != LENGTH) {
			return null;
		}
		long value = 0;
		for (int i = 0; i < LENGTH; i++) {
			int d = ALPHABET.indexOf(clean.charAt(i));
			if (d < 0) {
				return null;
			}
			value = (value << 5) | d;
		}
		int ip = (int) (value >>> 3);
		if ((value & 7) != check(ip)) {
			return null;
		}
		return ((ip >>> 24) & 0xFF) + "." + ((ip >>> 16) & 0xFF) + "." + ((ip >>> 8) & 0xFF) + "." + (ip & 0xFF);
	}

	/** One local network this device is on. */
	static final class LocalAddress {
		final String iface;
		final String ip;
		final byte[] bytes;

		LocalAddress(String iface, String ip, byte[] bytes) {
			this.iface = iface;
			this.ip = ip;
			this.bytes = bytes;
		}

		/** Wi-Fi and hotspot interfaces first. */
		int rank() {
			String n = iface.toLowerCase(Locale.ROOT);
			if (n.startsWith("wlan0")) return 0;
			if (n.startsWith("wlan") || n.startsWith("swlan") || n.startsWith("ap") || n.startsWith("softap")) return 1;
			if (n.startsWith("eth") || n.startsWith("rndis") || n.startsWith("usb")) return 2;
			return 3;
		}

		boolean isHotspot() {
			String n = iface.toLowerCase(Locale.ROOT);
			return n.startsWith("swlan") || n.startsWith("ap") || n.startsWith("softap") || n.startsWith("wlan1");
		}
	}

	/** Private (LAN) IPv4 addresses of this device, best candidate first. */
	static List<LocalAddress> localAddresses() {
		List<LocalAddress> result = new ArrayList<>();
		try {
			for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (!ni.isUp() || ni.isLoopback()) {
					continue;
				}
				for (InetAddress a : Collections.list(ni.getInetAddresses())) {
					if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
						result.add(new LocalAddress(ni.getName(), a.getHostAddress(), a.getAddress()));
					}
				}
			}
		} catch (Exception e) {
			// No network information available.
		}
		Collections.sort(result, (x, y) -> Integer.compare(x.rank(), y.rank()));
		return result;
	}
}
