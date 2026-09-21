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
package pl.selvin.android.autocontentprovider.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * Database lifecycle: the clear Uri, and the create/upgrade/downgrade/corruption callbacks.
 * <p>
 * The callback tests run against a throwaway database rather than the provider's own, since
 * both upgrade and corruption destroy what they are pointed at.
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseLifecycleTest {

	private static final String SCRATCH_DB = "lifecycle_scratch_db";

	private Context appContext;
	private ContentResolver resolver;
	private SupportSQLiteOpenHelper scratchHelper;

	@Before
	public void setUp() {
		appContext = ApplicationProvider.getApplicationContext();
		resolver = appContext.getContentResolver();
		appContext.deleteDatabase(SCRATCH_DB);
	}

	@After
	public void tearDown() {
		if (scratchHelper != null) {
			scratchHelper.close();
			scratchHelper = null;
		}
		appContext.deleteDatabase(SCRATCH_DB);
	}

	// ---- clear Uri ----------------------------------------------------------------------

	/**
	 * Deleting the clear Uri empties every table but leaves the schema in place, so the
	 * provider stays usable afterwards.
	 */
	@Test
	public void clearUriEmptiesTablesButKeepsSchema() {
		insertStatus(1, "one");
		insertStatus(2, "two");
		assertEquals(2, count(DatabaseTest.Status.TABLE_NAME));

		assertEquals(0, resolver.delete(TestProvider.CONTENT_HELPER.CLEAR_URI, null, null));

		// tables still queryable - a dropped table would throw instead of returning empty
		assertEquals(0, count(DatabaseTest.Status.TABLE_NAME));
		assertEquals(0, count(DatabaseTest.AllTypes.TABLE_NAME));
		assertEquals(0, count(DatabaseTest.Composite.TABLE_NAME));
	}

	@Test
	public void providerStillWritableAfterClear() {
		insertStatus(1, "one");
		resolver.delete(TestProvider.CONTENT_HELPER.CLEAR_URI, null, null);

		insertStatus(1, "again");

		assertEquals(1, count(DatabaseTest.Status.TABLE_NAME));
	}

	/** Indexes are recreated too - clear goes through onCreateDatabase, not just DELETE. */
	@Test
	public void clearRecreatesIndexes() {
		resolver.delete(TestProvider.CONTENT_HELPER.CLEAR_URI, null, null);

		final SupportSQLiteDatabase db = TestProvider.getRunning(appContext).getReadableDatabase();
		try (Cursor cursor = db.query("SELECT count(*) FROM sqlite_master WHERE type='index'"
				+ " AND tbl_name='" + DatabaseTest.AllTypes.TABLE_NAME + "' AND sql IS NOT NULL")) {
			assertTrue(cursor.moveToFirst());
			assertEquals(2, cursor.getInt(0));
		}
	}

	// ---- callbacks ----------------------------------------------------------------------

	@Test
	public void onCreateBuildsEveryTable() {
		final SupportSQLiteDatabase db = openScratch();

		for (final Object table : TestProvider.CONTENT_HELPER.getAllTables()) {
			final String name = ((pl.selvin.android.autocontentprovider.db.TableInfo) table).name;
			try (Cursor cursor = db.query("SELECT count(*) FROM sqlite_master"
					+ " WHERE type='table' AND name='" + name + "'")) {
				assertTrue(cursor.moveToFirst());
				assertEquals("missing table " + name, 1, cursor.getInt(0));
			}
		}
	}

	/** Upgrade drops everything and rebuilds - this is also what clearDatabase relies on. */
	@Test
	public void onUpgradeDropsAndRecreates() {
		final SupportSQLiteDatabase db = openScratch();
		final ContentValues values = new ContentValues();
		values.put(DatabaseTest.Status.ID, 1);
		values.put(DatabaseTest.Status.NAME, "one");
		db.insert(DatabaseTest.Status.TABLE_NAME, 0, values);
		assertEquals(1, scratchCount(db, DatabaseTest.Status.TABLE_NAME));

		callback().onUpgrade(db, 3, 4);

		assertEquals(0, scratchCount(db, DatabaseTest.Status.TABLE_NAME));
	}

	/**
	 * Characterises the current downgrade behaviour: onDowngradeDatabase delegates to
	 * SupportSQLiteOpenHelper.Callback's own onDowngrade, which throws. Before 4.x this
	 * path rebuilt the database instead - see the note in the summary if that was the
	 * intended contract.
	 */
	@Test
	public void onDowngradeThrows() {
		final SupportSQLiteDatabase db = openScratch();

		final SQLiteException ex = assertThrows(SQLiteException.class,
				() -> callback().onDowngrade(db, 4, 3));
		assertEquals("Can't downgrade database from version 4 to 3", ex.getMessage());
	}

	/**
	 * Regression guard: onCorruptionDatabase must reach the framework's own handler, which
	 * deletes the corrupt file. Delegating back into the provider's callback instead would
	 * recurse until the stack overflowed.
	 */
	@Test
	public void onCorruptionDeletesDatabaseFileWithoutRecursing() {
		final SupportSQLiteDatabase db = openScratch();
		final File file = appContext.getDatabasePath(SCRATCH_DB);
		assertTrue("scratch database was not created", file.exists());

		callback().onCorruption(db);

		assertTrue("corrupt database file should have been deleted", !file.exists());
	}

	// ---- helpers ------------------------------------------------------------------------

	private SupportSQLiteOpenHelper.Callback callback() {
		final SupportSQLiteOpenHelper.Callback callback =
				TestProvider.getRunning(appContext).getCapturedCallback();
		assertNotNull("provider callback was not captured", callback);
		return callback;
	}

	/** A database built by the provider's own callback, safe to destroy. */
	private SupportSQLiteDatabase openScratch() {
		scratchHelper = new FrameworkSQLiteOpenHelperFactory().create(
				SupportSQLiteOpenHelper.Configuration.builder(appContext)
						.name(SCRATCH_DB).callback(callback()).build());
		return scratchHelper.getWritableDatabase();
	}

	private static int scratchCount(SupportSQLiteDatabase db, String tableName) {
		try (Cursor cursor = db.query("SELECT count(*) FROM " + tableName)) {
			assertTrue(cursor.moveToFirst());
			return cursor.getInt(0);
		}
	}

	private static Uri dirUri(String tableName) {
		return TestProvider.CONTENT_HELPER.getDirUri(tableName);
	}

	private void insertStatus(int id, String name) {
		final ContentValues values = new ContentValues();
		values.put(DatabaseTest.Status.ID, id);
		values.put(DatabaseTest.Status.NAME, name);
		assertNotNull(resolver.insert(dirUri(DatabaseTest.Status.TABLE_NAME), values));
	}

	private int count(String tableName) {
		try (Cursor cursor = resolver.query(dirUri(tableName), null, null, null, null)) {
			assertNotNull(cursor);
			return cursor.getCount();
		}
	}
}
