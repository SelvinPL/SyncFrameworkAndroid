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
package pl.selvin.android.syncframework.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Base64;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;

/**
 * Column values crossing the wire in both directions: JSON to SQLite on the way down
 * (SyncTableInfo.SyncJSON) and back to JSON on the way up (SyncTableInfo.getChanges).
 * <p>
 * DATETIME and null live in {@link SyncUploadTest}, alongside the flows they came from.
 */
@RunWith(AndroidJUnit4.class)
public class SyncValueConversionTest extends SyncTestCase {

	private static final String URI_ONE = "http://server/Item('one')";

	/** Downloads one Item carrying the given extra fields. */
	private void download(String extraFields) {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", false,
				SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
						"\"" + SyncDatabase.Item.ID + "\":\"one\"" + extraFields)));
		syncDefaultScope();
	}

	/** Marks the row dirty and uploads, returning the request body. */
	private String uploadBody() {
		final ContentValues values = new ContentValues();
		values.put(SyncDatabase.Item.NAME, "touched");
		resolver.update(dirUri(SyncDatabase.Item.TABLE_NAME), values,
				SyncDatabase.Item.ID + "=?", new String[]{"one"});

		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-2", false));
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-3", false));
		syncDefaultScope();

		return SyncTestProvider.EXECUTOR.requests().get(1).body;
	}

	private byte[] blobValue(String column) {
		try (Cursor cursor = db().query("SELECT " + column + " FROM "
				+ SyncDatabase.Item.TABLE_NAME + " WHERE ID=?", new Object[]{"one"})) {
			assertTrue(cursor.moveToFirst());
			return cursor.getBlob(0);
		}
	}

	// ---- INTEGER ------------------------------------------------------------------------

	@Test
	public void integerRoundTrips() {
		download(",\"" + SyncDatabase.Item.QUANTITY + "\":42");

		assertEquals("42", columnValue(SyncDatabase.Item.TABLE_NAME, "one",
				SyncDatabase.Item.QUANTITY));
		assertTrue(uploadBody().contains("\"" + SyncDatabase.Item.QUANTITY + "\":42"));
	}

	/** Large values have to stay exact - they are read and written as long, not int. */
	@Test
	public void largeIntegerKeepsPrecision() {
		final long large = 9007199254740993L;
		download(",\"" + SyncDatabase.Item.QUANTITY + "\":" + large);

		assertEquals(Long.toString(large), columnValue(SyncDatabase.Item.TABLE_NAME, "one",
				SyncDatabase.Item.QUANTITY));
		assertTrue(uploadBody().contains("\"" + SyncDatabase.Item.QUANTITY + "\":" + large));
	}

	// ---- BOOLEAN ------------------------------------------------------------------------

	/** JSON true becomes 1 in SQLite, and 1 becomes true again on the way back. */
	@Test
	public void booleanTrueRoundTrips() {
		download(",\"" + SyncDatabase.Item.DONE + "\":true");

		assertEquals("1", columnValue(SyncDatabase.Item.TABLE_NAME, "one",
				SyncDatabase.Item.DONE));
		assertTrue(uploadBody().contains("\"" + SyncDatabase.Item.DONE + "\":true"));
	}

	@Test
	public void booleanFalseRoundTrips() {
		download(",\"" + SyncDatabase.Item.DONE + "\":false");

		assertEquals("0", columnValue(SyncDatabase.Item.TABLE_NAME, "one",
				SyncDatabase.Item.DONE));
		assertTrue(uploadBody().contains("\"" + SyncDatabase.Item.DONE + "\":false"));
	}

	/** Anything other than 1 reads back as false - the check is c.getLong(i) == 1. */
	@Test
	public void booleanStoredAsOtherNumberReadsBackFalse() {
		download(",\"" + SyncDatabase.Item.DONE + "\":true");
		db().execSQL("UPDATE " + SyncDatabase.Item.TABLE_NAME + " SET "
				+ SyncDatabase.Item.DONE + "=2 WHERE ID='one'");

		assertTrue(uploadBody().contains("\"" + SyncDatabase.Item.DONE + "\":false"));
	}

	// ---- DECIMAL ------------------------------------------------------------------------

	@Test
	public void decimalRoundTrips() {
		download(",\"" + SyncDatabase.Item.PRICE + "\":12.5");

		assertEquals(12.5d, Double.parseDouble(columnValue(SyncDatabase.Item.TABLE_NAME, "one",
				SyncDatabase.Item.PRICE)), 0.0001d);
		assertTrue(uploadBody().contains("\"" + SyncDatabase.Item.PRICE + "\":12.5"));
	}

	/**
	 * A DECIMAL column can legitimately arrive as a JSON integer. SyncJSON branches on the
	 * parsed type rather than the column type, so the whole number path has to work too.
	 */
	@Test
	public void decimalArrivingAsWholeNumberIsAccepted() {
		download(",\"" + SyncDatabase.Item.PRICE + "\":13");

		assertEquals(13d, Double.parseDouble(columnValue(SyncDatabase.Item.TABLE_NAME, "one",
				SyncDatabase.Item.PRICE)), 0.0001d);
		assertTrue("goes back up as a real, since the column is DECIMAL",
				uploadBody().contains("\"" + SyncDatabase.Item.PRICE + "\":13.0"));
	}

	// ---- BLOB ---------------------------------------------------------------------------

	/**
	 * BLOBs travel as Base64. The decode uses android.util.Base64, the encode is hand rolled
	 * in JsonUtf8Writer - so the two have to agree on padding and alphabet.
	 */
	@Test
	public void blobRoundTripsThroughBase64() {
		final byte[] original = "some binary content".getBytes(StandardCharsets.UTF_8);
		final String encoded = Base64.encodeToString(original, Base64.NO_WRAP);
		download(",\"" + SyncDatabase.Item.DATA + "\":\"" + encoded + "\"");

		assertArrayEquals("stored bytes should match what was encoded", original,
				blobValue(SyncDatabase.Item.DATA));

		final String body = uploadBody();
		assertTrue("blob should go back up as Base64: " + body,
				body.contains("\"" + SyncDatabase.Item.DATA + "\":\"" + encoded + "\""));
	}

	/** Lengths either side of a 3 byte boundary, where Base64 padding changes. */
	@Test
	public void blobPaddingIsCorrectForEveryLengthModulo() {
		for (int length = 1; length <= 4; length++) {
			resetDatabaseAndExecutor();

			final byte[] original = new byte[length];
			for (int i = 0; i < length; i++)
				original[i] = (byte) (0xF0 + i);
			final String encoded = Base64.encodeToString(original, Base64.NO_WRAP);

			download(",\"" + SyncDatabase.Item.DATA + "\":\"" + encoded + "\"");
			assertArrayEquals("length " + length, original, blobValue(SyncDatabase.Item.DATA));

			final String body = uploadBody();
			assertTrue("length " + length + " should re-encode identically: " + body,
					body.contains("\"" + SyncDatabase.Item.DATA + "\":\"" + encoded + "\""));
		}
	}

	@Test
	public void emptyBlobRoundTrips() {
		download(",\"" + SyncDatabase.Item.DATA + "\":\"\"");

		assertEquals(0, blobValue(SyncDatabase.Item.DATA).length);
		assertTrue(uploadBody().contains("\"" + SyncDatabase.Item.DATA + "\":\"\""));
	}

	// ---- strings ------------------------------------------------------------------------

	/** Characters that have to be escaped on the way out and unescaped on the way in. */
	@Test
	public void stringWithJsonSpecialCharactersRoundTrips() {
		final String awkward = "quote \\\" backslash \\\\ newline \\n tab \\t";
		download(",\"" + SyncDatabase.Item.NAME + "\":\"" + awkward + "\"");

		assertEquals("quote \" backslash \\ newline \n tab \t",
				columnValue(SyncDatabase.Item.TABLE_NAME, "one", SyncDatabase.Item.NAME));
	}

	@Test
	public void unicodeStringRoundTrips() {
		download(",\"" + SyncDatabase.Item.NAME + "\":\"zażółć gęślą jaźń\"");

		assertEquals("zażółć gęślą jaźń",
				columnValue(SyncDatabase.Item.TABLE_NAME, "one", SyncDatabase.Item.NAME));

		final ContentValues values = new ContentValues();
		values.put(SyncDatabase.Item.NAME, "zażółć gęślą jaźń");
		resolver.update(dirUri(SyncDatabase.Item.TABLE_NAME), values,
				SyncDatabase.Item.ID + "=?", new String[]{"one"});
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-2", false));
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-3", false));
		syncDefaultScope();

		final String body = SyncTestProvider.EXECUTOR.requests().get(1).body;
		assertTrue("non ascii should survive the upload: " + body,
				body.contains("zażółć gęślą jaźń"));
	}
}
