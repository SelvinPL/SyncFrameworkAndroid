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
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import pl.selvin.android.autocontentprovider.db.IndexInfo;
import pl.selvin.android.autocontentprovider.db.TableInfo;

/**
 * {@link IndexInfo} statement generation, plus a check that the generated statements are
 * actually accepted by SQLite and leave the indexes behind.
 */
@RunWith(AndroidJUnit4.class)
public class IndexInfoTest {

	private static final String SIMPLE_INDEX =
			"IX_" + DatabaseTest.AllTypes.TABLE_NAME + "_" + DatabaseTest.AllTypes.NAME;
	private static final String UNIQUE_INDEX =
			"UX_" + DatabaseTest.AllTypes.TABLE_NAME + "_" + DatabaseTest.AllTypes.GUID;

	private static IndexInfo index(String name) {
		final TableInfo allTypes =
				TestProvider.CONTENT_HELPER.getTableFromName(DatabaseTest.AllTypes.TABLE_NAME);
		assertNotNull(allTypes);
		for (final IndexInfo indexInfo : allTypes.indexes) {
			if (indexInfo.name.equals(name))
				return indexInfo;
		}
		throw new AssertionError("no index " + name);
	}

	@Test
	public void bothIndexesAreParsed() {
		final TableInfo allTypes =
				TestProvider.CONTENT_HELPER.getTableFromName(DatabaseTest.AllTypes.TABLE_NAME);
		assertNotNull(allTypes);
		assertEquals(2, allTypes.indexes.size());
		assertEquals(0,
				TestProvider.CONTENT_HELPER.getTableFromName(DatabaseTest.Status.TABLE_NAME)
						.indexes.size());
	}

	@Test
	public void indexFlagsAreParsed() {
		final IndexInfo simple = index(SIMPLE_INDEX);
		assertEquals(DatabaseTest.AllTypes.TABLE_NAME, simple.tableName);
		assertTrue(!simple.isUnique);
		assertTrue(!simple.ifNotExists);
		assertEquals("", simple.where);

		final IndexInfo unique = index(UNIQUE_INDEX);
		assertTrue(unique.isUnique);
		assertTrue(unique.ifNotExists);
		assertEquals(DatabaseTest.AllTypes.NUM + " IS NOT NULL", unique.where);
	}

	/** Single column, with both collate and order set. */
	@Test
	public void createStatementSimpleIndex() {
		assertEquals("CREATE INDEX [" + SIMPLE_INDEX + "] ON ["
						+ DatabaseTest.AllTypes.TABLE_NAME + "] (["
						+ DatabaseTest.AllTypes.NAME + "] COLLATE NOCASE ASC)",
				index(SIMPLE_INDEX).createStatement());
	}

	/**
	 * UNIQUE + IF NOT EXISTS + partial index, two columns, one of them with neither collate
	 * nor order - which is where the doubled spaces come from.
	 */
	@Test
	public void createStatementUniqueIfNotExistsPartialIndex() {
		assertEquals("CREATE UNIQUE INDEX IF NOT EXISTS [" + UNIQUE_INDEX + "] ON ["
						+ DatabaseTest.AllTypes.TABLE_NAME + "] (["
						+ DatabaseTest.AllTypes.GUID + "]  , ["
						+ DatabaseTest.AllTypes.NUM + "]  DESC) WHERE "
						+ DatabaseTest.AllTypes.NUM + " IS NOT NULL",
				index(UNIQUE_INDEX).createStatement());
	}

	/**
	 * The statements above are only useful if SQLite accepts them - this is the end to end
	 * check that onCreateDatabase actually left both indexes in place.
	 */
	@Test
	public void indexesExistInTheDatabase() {
		final Context appContext = ApplicationProvider.getApplicationContext();
		final SupportSQLiteDatabase db = TestProvider.getRunning(appContext).getReadableDatabase();

		final ArrayList<String> names = new ArrayList<>();
		try (Cursor cursor = db.query("SELECT name FROM sqlite_master WHERE type='index'"
				+ " AND tbl_name='" + DatabaseTest.AllTypes.TABLE_NAME + "'"
				+ " AND sql IS NOT NULL ORDER BY name")) {
			while (cursor.moveToNext())
				names.add(cursor.getString(0));
		}

		assertEquals(List.of(SIMPLE_INDEX, UNIQUE_INDEX), names);
	}
}
