package pl.selvin.android.syncframework.json.moshi;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import okio.Buffer;

final class NameCache {
	static final int MAX_NAME_LEN = 64;

	private final String[] values;
	private final byte[][] keys;
	private final int mask;

	NameCache(final String[] names) {
		int cap = 8;
		while (cap < names.length * 4) cap <<= 1;
		final String[] v = new String[cap];
		final byte[][] k = new byte[cap][];
		final int m = cap - 1;
		for (final String n : names) {
			if (n == null) continue;
			final byte[] b = n.getBytes(StandardCharsets.UTF_8);
			int i = hash(b, b.length) & m;
			while (v[i] != null) {
				if (Arrays.equals(k[i], b)) {
					i = -1;
					break;
				}
				i = (i + 1) & m;
			}
			if (i >= 0) {
				v[i] = n;
				k[i] = b;
			}
		}
		this.values = v;
		this.keys = k;
		this.mask = m;
	}

	String get(final Buffer buffer, final int len) {
		if (len == 0) return "";
		if (len > MAX_NAME_LEN) return null;
		int i = hashBuffer(buffer, len) & mask;
		for (; ; ) {
			final String v = values[i];
			if (v == null) return null;
			final byte[] b = keys[i];
			if (b.length == len && matches(b, buffer, len)) return v;
			i = (i + 1) & mask;
		}
	}

	private static int hash(final byte[] b, final int len) {
		int h = 0x811c9dc5;
		for (int i = 0; i < len; i++) {
			h ^= (b[i] & 0xff);
			h *= 0x01000193;
		}
		return h;
	}

	private static int hashBuffer(final Buffer buf, final int len) {
		int h = 0x811c9dc5;
		for (int i = 0; i < len; i++) {
			h ^= (buf.getByte(i) & 0xff);
			h *= 0x01000193;
		}
		return h;
	}

	private static boolean matches(final byte[] b, final Buffer buf, final int len) {
		for (int i = 0; i < len; i++) if (b[i] != buf.getByte(i)) return false;
		return true;
	}
}