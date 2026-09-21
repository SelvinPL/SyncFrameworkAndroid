/*
 Copyright (c) 2026 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */
package pl.selvin.android.syncframework.json.moshi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;

import okio.Buffer;

/**
 * {@link NameCache} is a local addition to the Moshi fork: it lets the reader hand back an
 * already interned String for a known field name instead of decoding one per occurrence.
 * <p>
 * Lives in the library's own package because the class is package private. A miss is not a
 * failure - JsonUtf8Reader falls back to readUtf8 - so the contract being pinned here is
 * that a hit returns the right instance and a miss returns null rather than something wrong.
 */
@RunWith(AndroidJUnit4.class)
public class NameCacheTest {

	private static String lookup(NameCache cache, String name) {
		final byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
		final Buffer buffer = new Buffer().write(bytes);
		return cache.get(buffer, bytes.length);
	}

	/** Same FNV-1a the cache uses, so collisions can be produced on purpose. */
	private static int fnv(String value) {
		int h = 0x811c9dc5;
		for (final byte b : value.getBytes(StandardCharsets.UTF_8)) {
			h ^= (b & 0xff);
			h *= 0x01000193;
		}
		return h;
	}

	@Test
	public void knownNameReturnsTheInternedInstance() {
		final String[] names = {"uri", "type", "isDeleted"};
		final NameCache cache = new NameCache(names);

		// not merely equal - the point of the cache is to avoid allocating a new String
		assertSame(names[0], lookup(cache, "uri"));
		assertSame(names[1], lookup(cache, "type"));
		assertSame(names[2], lookup(cache, "isDeleted"));
	}

	@Test
	public void unknownNameMisses() {
		final NameCache cache = new NameCache(new String[]{"uri", "type"});

		assertNull(lookup(cache, "somethingElse"));
	}

	/** A known name's prefix is a different name, and the length check has to catch that. */
	@Test
	public void prefixOfAKnownNameMisses() {
		final NameCache cache = new NameCache(new String[]{"isDeleted"});

		assertNull(lookup(cache, "isDelete"));
		assertNull(lookup(cache, "is"));
		assertNotNull(lookup(cache, "isDeleted"));
	}

	@Test
	public void emptyNameReturnsEmptyString() {
		final NameCache cache = new NameCache(new String[]{"uri"});

		assertEquals("", cache.get(new Buffer(), 0));
	}

	/**
	 * Names longer than MAX_NAME_LEN are refused outright - JsonUtf8Reader does not even
	 * consult the cache for them. A column name that long simply never gets interned.
	 */
	@Test
	public void nameLongerThanMaxIsRefused() {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < NameCache.MAX_NAME_LEN + 1; i++)
			sb.append('a');
		final String tooLong = sb.toString();
		final NameCache cache = new NameCache(new String[]{tooLong});

		assertNull("even though it was offered to the constructor", lookup(cache, tooLong));
	}

	@Test
	public void nameOfExactlyMaxLengthIsAccepted() {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < NameCache.MAX_NAME_LEN; i++)
			sb.append('a');
		final String atLimit = sb.toString();
		final NameCache cache = new NameCache(new String[]{atLimit});

		assertSame(atLimit, lookup(cache, atLimit));
	}

	/**
	 * Two names landing in the same bucket. The cache probes linearly on insert and on
	 * lookup, so both have to remain findable.
	 */
	@Test
	public void collidingNamesBothResolve() {
		final String[] pair = findCollidingPair();
		final NameCache cache = new NameCache(pair);

		assertSame(pair[0], lookup(cache, pair[0]));
		assertSame(pair[1], lookup(cache, pair[1]));
	}

	/** Probing must also terminate on a miss when the colliding bucket is occupied. */
	@Test
	public void missAmongCollidingNamesStillReturnsNull() {
		final String[] pair = findCollidingPair();
		final NameCache cache = new NameCache(pair);

		assertNull(lookup(cache, "definitelyNotInTheCache"));
	}

	/** The constructor skips a name it has already stored rather than looping forever. */
	@Test
	public void duplicateNamesAreTolerated() {
		final String[] names = {"uri", "uri", "type"};
		final NameCache cache = new NameCache(names);

		assertSame(names[0], lookup(cache, "uri"));
		assertSame(names[2], lookup(cache, "type"));
	}

	@Test
	public void nullEntriesAreSkipped() {
		final String[] names = {"uri", null, "type"};
		final NameCache cache = new NameCache(names);

		assertSame(names[0], lookup(cache, "uri"));
		assertSame(names[2], lookup(cache, "type"));
	}

	/** Keys are bytes, so a name whose UTF-8 length differs from its char count still works. */
	@Test
	public void multiByteNameResolves() {
		final String name = "zażółć";
		final NameCache cache = new NameCache(new String[]{name});

		assertTrue("fixture should actually be multi byte",
				name.getBytes(StandardCharsets.UTF_8).length > name.length());
		assertSame(name, lookup(cache, name));
	}

	/** Everything offered to a realistically sized cache has to come back. */
	@Test
	public void everyNameInALargerCacheResolves() {
		final String[] names = new String[64];
		for (int i = 0; i < names.length; i++)
			names[i] = "column_" + i;
		final NameCache cache = new NameCache(names);

		for (final String name : names)
			assertSame(name, lookup(cache, name));
		assertNull(lookup(cache, "column_64"));
	}

	/**
	 * Two names that hash into the same bucket of an 8 slot table, which is the capacity a
	 * two name cache gets.
	 */
	private static String[] findCollidingPair() {
		final int mask = 7;
		for (int i = 0; i < 2000; i++) {
			for (int j = i + 1; j < 2000; j++) {
				final String a = "n" + i;
				final String b = "n" + j;
				if ((fnv(a) & mask) == (fnv(b) & mask))
					return new String[]{a, b};
			}
		}
		throw new AssertionError("no colliding pair found");
	}
}
