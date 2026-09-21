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

import java.util.ArrayList;
import java.util.List;

/**
 * Cascade delete across Parent -&gt; Child -&gt; GrandChild and Parent -&gt; Note.
 * <p>
 * Fixture (Parent 1 is the one deleted by most tests):
 * <pre>
 * Parent 1 (Tenant 7, Code P-1)   Parent 2 (Tenant 7, Code P-2)
 *   Child 10 (Tenant 7)             Child 20 (Tenant 7)
 *     GrandChild 100, 101             GrandChild 200
 *   Child 11 (Tenant 7)
 *     GrandChild 110
 *   Child 12 (Tenant 99)  &lt;- right parent, wrong tenant: must survive
 *     GrandChild 120      &lt;- survives with its parent
 *   Note 1000, 1001                 Note 2000
 * </pre>
 */
@RunWith(AndroidJUnit4.class)
public class CascadeDeleteTest {

	private static final int PARENT_ONE = 1;
	private static final int PARENT_TWO = 2;
	private static final int TENANT = 7;
	private static final int OTHER_TENANT = 99;
	private static final String CODE_ONE = "P-1";
	private static final String CODE_TWO = "P-2";

	private ContentResolver resolver;
	private long parentOneRowId;

	@Before
	public void setUp() {
		final Context appContext = ApplicationProvider.getApplicationContext();
		resolver = appContext.getContentResolver();

		// leaf first, so nothing relies on cascade while clearing
		resolver.delete(dirUri(DatabaseTest.GrandChild.TABLE_NAME), null, null);
		resolver.delete(dirUri(DatabaseTest.Note.TABLE_NAME), null, null);
		resolver.delete(dirUri(DatabaseTest.Child.TABLE_NAME), null, null);
		resolver.delete(dirUri(DatabaseTest.Parent.TABLE_NAME), null, null);

		parentOneRowId = insertParent(PARENT_ONE, TENANT, CODE_ONE);
		insertParent(PARENT_TWO, TENANT, CODE_TWO);

		insertChild(10, PARENT_ONE, TENANT);
		insertChild(11, PARENT_ONE, TENANT);
		insertChild(12, PARENT_ONE, OTHER_TENANT);
		insertChild(20, PARENT_TWO, TENANT);

		insertGrandChild(100, 10);
		insertGrandChild(101, 10);
		insertGrandChild(110, 11);
		insertGrandChild(120, 12);
		insertGrandChild(200, 20);

		insertNote(1000, CODE_ONE);
		insertNote(1001, CODE_ONE);
		insertNote(2000, CODE_TWO);
	}

	/**
	 * Both cascades of Parent run, the Child cascade recurses into GrandChild, and the
	 * returned count is the table's own deletions plus everything cascaded:
	 * 1 Parent + 2 Child + 3 GrandChild + 2 Note.
	 */
	@Test
	public void deleteCascadesWholeChainAndCountsEverything() {
		final int deleted = resolver.delete(dirUri(DatabaseTest.Parent.TABLE_NAME),
				DatabaseTest.Parent.ID + "=?", new String[]{Integer.toString(PARENT_ONE)});

		assertEquals(8, deleted);
		assertSurvivorsOfDeletingParentOne();
	}

	/** Same cascade, reached through a primary key item Uri instead of a dir Uri. */
	@Test
	public void deleteByPrimaryKeyItemUriCascades() {
		final int deleted = resolver.delete(TestProvider.CONTENT_HELPER.getItemUri(
				DatabaseTest.Parent.TABLE_NAME, Integer.toString(PARENT_ONE)), null, null);

		assertEquals(8, deleted);
		assertSurvivorsOfDeletingParentOne();
	}

	/** Same cascade, reached through a ROWID item Uri. */
	@Test
	public void deleteByRowIdItemUriCascades() {
		final int deleted = resolver.delete(TestProvider.CONTENT_HELPER.getItemUri(
				DatabaseTest.Parent.TABLE_NAME, parentOneRowId), null, null);

		assertEquals(8, deleted);
		assertSurvivorsOfDeletingParentOne();
	}

	/**
	 * The Child cascade keys on {ID, Tenant}. Child 12 points at the deleted parent but
	 * carries a different tenant, so it must be left alone - as must the GrandChild hanging
	 * off it. A cascade that only matched the first key column would take both.
	 */
	@Test
	public void cascadeMatchesEveryKeyColumn() {
		resolver.delete(dirUri(DatabaseTest.Parent.TABLE_NAME),
				DatabaseTest.Parent.ID + "=?", new String[]{Integer.toString(PARENT_ONE)});

		assertEquals(List.of(12, 20), ids(DatabaseTest.Child.TABLE_NAME));
		assertEquals(List.of(120, 200), ids(DatabaseTest.GrandChild.TABLE_NAME));
	}

	/** Second cascade of Parent keys on a GUID column rather than an INTEGER one. */
	@Test
	public void cascadeOnStringKeyColumn() {
		resolver.delete(dirUri(DatabaseTest.Parent.TABLE_NAME),
				DatabaseTest.Parent.ID + "=?", new String[]{Integer.toString(PARENT_ONE)});

		assertEquals(List.of(2000), ids(DatabaseTest.Note.TABLE_NAME));
	}

	/** Deleting a row with nothing hanging off it counts only itself. */
	@Test
	public void deleteWithoutRelatedRowsCountsOnlyItself() {
		insertParent(3, TENANT, "P-3");

		assertEquals(1, resolver.delete(dirUri(DatabaseTest.Parent.TABLE_NAME),
				DatabaseTest.Parent.ID + "=?", new String[]{"3"}));
	}

	/** A table with no @Cascade returns its own count and touches nothing else. */
	@Test
	public void deleteOnNonCascadingTableCountsOnlyItself() {
		assertEquals(2, resolver.delete(dirUri(DatabaseTest.GrandChild.TABLE_NAME),
				DatabaseTest.GrandChild.CHILD_ID + "=?", new String[]{"10"}));

		assertEquals(List.of(110, 120, 200), ids(DatabaseTest.GrandChild.TABLE_NAME));
		assertEquals(4, count(DatabaseTest.Child.TABLE_NAME));
	}

	@Test
	public void deleteMatchingNothingCascadesNothing() {
		assertEquals(0, resolver.delete(dirUri(DatabaseTest.Parent.TABLE_NAME),
				DatabaseTest.Parent.ID + "=?", new String[]{"404"}));

		assertEquals(2, count(DatabaseTest.Parent.TABLE_NAME));
		assertEquals(4, count(DatabaseTest.Child.TABLE_NAME));
		assertEquals(5, count(DatabaseTest.GrandChild.TABLE_NAME));
		assertEquals(3, count(DatabaseTest.Note.TABLE_NAME));
	}

	// ---- helpers ------------------------------------------------------------------------

	private void assertSurvivorsOfDeletingParentOne() {
		assertEquals(List.of(PARENT_TWO), ids(DatabaseTest.Parent.TABLE_NAME));
		assertEquals(List.of(12, 20), ids(DatabaseTest.Child.TABLE_NAME));
		assertEquals(List.of(120, 200), ids(DatabaseTest.GrandChild.TABLE_NAME));
		assertEquals(List.of(2000), ids(DatabaseTest.Note.TABLE_NAME));
	}

	private static Uri dirUri(String tableName) {
		return TestProvider.CONTENT_HELPER.getDirUri(tableName);
	}

	private long insertParent(int id, int tenant, String code) {
		final ContentValues values = new ContentValues();
		values.put(DatabaseTest.Parent.ID, id);
		values.put(DatabaseTest.Parent.TENANT, tenant);
		values.put(DatabaseTest.Parent.CODE, code);
		values.put(DatabaseTest.Parent.NAME, "parent " + id);
		return insert(DatabaseTest.Parent.TABLE_NAME, values);
	}

	private void insertChild(int id, int parentId, int tenant) {
		final ContentValues values = new ContentValues();
		values.put(DatabaseTest.Child.ID, id);
		values.put(DatabaseTest.Child.PARENT_ID, parentId);
		values.put(DatabaseTest.Child.TENANT, tenant);
		insert(DatabaseTest.Child.TABLE_NAME, values);
	}

	private void insertGrandChild(int id, int childId) {
		final ContentValues values = new ContentValues();
		values.put(DatabaseTest.GrandChild.ID, id);
		values.put(DatabaseTest.GrandChild.CHILD_ID, childId);
		insert(DatabaseTest.GrandChild.TABLE_NAME, values);
	}

	private void insertNote(int id, String parentCode) {
		final ContentValues values = new ContentValues();
		values.put(DatabaseTest.Note.ID, id);
		values.put(DatabaseTest.Note.PARENT_CODE, parentCode);
		insert(DatabaseTest.Note.TABLE_NAME, values);
	}

	private long insert(String tableName, ContentValues values) {
		final Uri uri = resolver.insert(dirUri(tableName), values);
		assertNotNull("insert into " + tableName + " returned null", uri);
		return ContentUris.parseId(uri);
	}

	private int count(String tableName) {
		try (Cursor cursor = resolver.query(dirUri(tableName), null, null, null, null)) {
			assertNotNull(cursor);
			return cursor.getCount();
		}
	}

	/** Ids present in a table, ascending, so assertions read as the surviving rows. */
	private List<Integer> ids(String tableName) {
		final ArrayList<Integer> ids = new ArrayList<>();
		try (Cursor cursor = resolver.query(dirUri(tableName), new String[]{"ID"}, null, null,
				"ID ASC")) {
			assertNotNull(cursor);
			while (cursor.moveToNext())
				ids.add(cursor.getInt(0));
		}
		return ids;
	}
}
