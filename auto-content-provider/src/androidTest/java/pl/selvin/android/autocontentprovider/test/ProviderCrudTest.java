/*
 Copyright (c) 2017-2026 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */
package pl.selvin.android.autocontentprovider.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import pl.selvin.android.autocontentprovider.content.ContentHelper;

/**
 * The provider's CRUD contract as seen through a ContentResolver: insert/query/update/delete,
 * dir vs item Uris, readonly tables, projections, limit and Uri validation.
 * <p>
 * Cascade delete lives in {@link CascadeDeleteTest}.
 */
@RunWith(AndroidJUnit4.class)
public class ProviderCrudTest {

	private Context appContext;
	private ContentResolver resolver;

	@Before
	public void setUp() {
		appContext = ApplicationProvider.getApplicationContext();
		resolver = appContext.getContentResolver();
		resolver.delete(dirUri(DatabaseTest.Status.TABLE_NAME), null, null);
	}

	// ---- insert -------------------------------------------------------------------------

	@Test
	public void insertReturnsRowIdItemUri() {
		final Uri uri = resolver.insert(dirUri(DatabaseTest.Status.TABLE_NAME), statusValues(1, "Test"));

		assertNotNull(uri);
		assertEquals(1L, ContentUris.parseId(uri));
	}

	@Test
	public void insertThenQueryReturnsRow() {
		final Uri dirUri = dirUri(DatabaseTest.Status.TABLE_NAME);
		final Uri uri = resolver.insert(dirUri, statusValues(1, "Test"));
		assertNotNull(uri);
		assertEquals(1L, ContentUris.parseId(uri));

		try (Cursor cursor = resolver.query(dirUri,
				new String[]{DatabaseTest.Status.ID, DatabaseTest.Status.NAME}, null, null, null)) {
			assertNotNull(cursor);
			assertEquals(1, cursor.getCount());
			assertEquals(DatabaseTest.Status.ID, cursor.getColumnName(0));
			assertEquals(DatabaseTest.Status.NAME, cursor.getColumnName(1));
			assertTrue(cursor.moveToFirst());
			assertEquals(1, cursor.getInt(0));
			assertEquals("Test", cursor.getString(1));
		}
	}

	@Test
	public void insertWithItemUriIsRejected() {
		final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> resolver.insert(
						TestProvider.CONTENT_HELPER.getItemUri(DatabaseTest.Status.TABLE_NAME, "1"),
						statusValues(1, "Test")));
		assertEquals("Can not insert with Item type Uri.", ex.getMessage());
	}

	// ---- readonly -----------------------------------------------------------------------

	@Test
	public void readonlyTableRejectsWrites() {
		final String errMsg = "Table " + DatabaseTest.StatusReadonly.TABLE_NAME + " is readonly.";
		try {
			resolver.insert(dirUri(DatabaseTest.StatusReadonly.TABLE_NAME), new ContentValues());
			fail();
		} catch (IllegalArgumentException ex) {
			assertEquals(errMsg, ex.getMessage());
		}
		final Uri itemUri = TestProvider.CONTENT_HELPER.getItemUri(
				DatabaseTest.StatusReadonly.TABLE_NAME, 1);
		try {
			resolver.update(itemUri, new ContentValues(), null, null);
			fail();
		} catch (IllegalArgumentException ex) {
			assertEquals(errMsg, ex.getMessage());
		}
		try {
			resolver.delete(itemUri, null, null);
			fail();
		} catch (IllegalArgumentException ex) {
			assertEquals(errMsg, ex.getMessage());
		}
	}

	/** readonly blocks writes only - reads must still work. */
	@Test
	public void readonlyTableIsStillQueryable() {
		try (Cursor cursor = resolver.query(dirUri(DatabaseTest.StatusReadonly.TABLE_NAME),
				null, null, null, null)) {
			assertNotNull(cursor);
			assertEquals(0, cursor.getCount());
		}
	}

	// ---- update / delete by item Uri ----------------------------------------------------

	@Test
	public void updateByItemUriTouchesOnlyThatRow() {
		insertStatus(1, "one");
		insertStatus(2, "two");

		final ContentValues values = new ContentValues();
		values.put(DatabaseTest.Status.NAME, "changed");
		assertEquals(1, resolver.update(
				TestProvider.CONTENT_HELPER.getItemUri(DatabaseTest.Status.TABLE_NAME, "1"),
				values, null, null));

		assertEquals("changed", nameOf(1));
		assertEquals("two", nameOf(2));
	}

	@Test
	public void deleteByItemUriTouchesOnlyThatRow() {
		insertStatus(1, "one");
		insertStatus(2, "two");

		assertEquals(1, resolver.delete(
				TestProvider.CONTENT_HELPER.getItemUri(DatabaseTest.Status.TABLE_NAME, "1"),
				null, null));

		assertEquals(1, count(DatabaseTest.Status.TABLE_NAME));
		assertEquals("two", nameOf(2));
	}

	/** A caller supplied selection has to be ANDed with the one the item Uri implies. */
	@Test
	public void itemUriSelectionIsCombinedWithCallerSelection() {
		insertStatus(1, "one");

		final ContentValues values = new ContentValues();
		values.put(DatabaseTest.Status.NAME, "changed");
		// row 1 exists, but not with this name - so nothing should match
		assertEquals(0, resolver.update(
				TestProvider.CONTENT_HELPER.getItemUri(DatabaseTest.Status.TABLE_NAME, "1"),
				values, DatabaseTest.Status.NAME + "=?", new String[]{"nope"}));

		assertEquals("one", nameOf(1));
	}

	@Test
	public void deleteByRowIdItemUri() {
		final long rowId = insertStatus(1, "one");
		insertStatus(2, "two");

		assertEquals(1, resolver.delete(
				TestProvider.CONTENT_HELPER.getItemUri(DatabaseTest.Status.TABLE_NAME, rowId),
				null, null));

		assertEquals(1, count(DatabaseTest.Status.TABLE_NAME));
		assertEquals("two", nameOf(2));
	}

	// ---- projection and limit -----------------------------------------------------------

	/** _id is an alias the provider adds, not a real column. */
	@Test
	public void queryExposesUnderscoreIdAlias() {
		final long rowId = insertStatus(1, "one");

		try (Cursor cursor = resolver.query(dirUri(DatabaseTest.Status.TABLE_NAME),
				new String[]{"_id", DatabaseTest.Status.ID}, null, null, null)) {
			assertNotNull(cursor);
			assertTrue(cursor.moveToFirst());
			assertEquals(rowId, cursor.getLong(0));
			assertEquals(1, cursor.getInt(1));
		}
	}

	@Test
	public void queryHonoursLimitParameter() {
		insertStatus(1, "one");
		insertStatus(2, "two");
		insertStatus(3, "three");

		final Uri limited = TestProvider.CONTENT_HELPER
				.getDirUriBuilder(DatabaseTest.Status.TABLE_NAME)
				.appendQueryParameter(ContentHelper.PARAMETER_LIMIT, "2").build();
		try (Cursor cursor = resolver.query(limited, null, null, null, null)) {
			assertNotNull(cursor);
			assertEquals(2, cursor.getCount());
		}
	}

	// ---- Uri validation -----------------------------------------------------------------

	@Test
	public void mimeTypesForDirAndItemUris() {
		final TestProvider provider = TestProvider.getRunning(appContext);

		assertEquals("vnd.android.cursor.dir/" + TestProvider.AUTHORITY + "."
						+ DatabaseTest.Status.TABLE_NAME,
				provider.getType(dirUri(DatabaseTest.Status.TABLE_NAME)));
		assertEquals("vnd.android.cursor.item/" + TestProvider.AUTHORITY + "."
						+ DatabaseTest.Status.TABLE_NAME,
				provider.getType(TestProvider.CONTENT_HELPER
						.getItemUri(DatabaseTest.Status.TABLE_NAME, "1")));
	}

	@Test
	public void unknownUriIsRejected() {
		final Uri unknown = Uri.parse("content://" + TestProvider.AUTHORITY + "/NoSuchTable");
		final TestProvider provider = TestProvider.getRunning(appContext);

		assertEquals("Unknown Uri " + unknown,
				assertThrows(IllegalArgumentException.class, () -> provider.getType(unknown))
						.getMessage());
		assertThrows(IllegalArgumentException.class,
				() -> resolver.query(unknown, null, null, null, null));
		assertThrows(IllegalArgumentException.class,
				() -> resolver.delete(unknown, null, null));
		assertThrows(IllegalArgumentException.class,
				() -> resolver.update(unknown, new ContentValues(), null, null));
	}

	/**
	 * ContentResolver.getType swallows provider exceptions and returns null, so the throw
	 * above is only visible when calling the provider directly. Pinned so the difference
	 * is not mistaken for the provider accepting the Uri.
	 */
	@Test
	public void unknownUriThroughResolverGetTypeIsNull() {
		assertNull(resolver.getType(
				Uri.parse("content://" + TestProvider.AUTHORITY + "/NoSuchTable")));
	}

	// ---- helpers ------------------------------------------------------------------------

	private static Uri dirUri(String tableName) {
		return TestProvider.CONTENT_HELPER.getDirUri(tableName);
	}

	private static ContentValues statusValues(int id, String name) {
		final ContentValues values = new ContentValues();
		values.put(DatabaseTest.Status.ID, id);
		values.put(DatabaseTest.Status.NAME, name);
		return values;
	}

	private long insertStatus(int id, String name) {
		final Uri uri = resolver.insert(dirUri(DatabaseTest.Status.TABLE_NAME), statusValues(id, name));
		assertNotNull(uri);
		return ContentUris.parseId(uri);
	}

	private String nameOf(int id) {
		try (Cursor cursor = resolver.query(dirUri(DatabaseTest.Status.TABLE_NAME),
				new String[]{DatabaseTest.Status.NAME}, DatabaseTest.Status.ID + "=?",
				new String[]{Integer.toString(id)}, null)) {
			assertNotNull(cursor);
			assertTrue("no Status row with ID " + id, cursor.moveToFirst());
			return cursor.getString(0);
		}
	}

	private int count(String tableName) {
		try (Cursor cursor = resolver.query(dirUri(tableName), null, null, null, null)) {
			assertNotNull(cursor);
			return cursor.getCount();
		}
	}
}
