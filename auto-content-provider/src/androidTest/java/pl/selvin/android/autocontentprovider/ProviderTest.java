/*
 Copyright (c) 2017 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */
package pl.selvin.android.autocontentprovider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

@RunWith(AndroidJUnit4.class)
public class ProviderTest {

	Context appContext;

	@Before
	public void setUp() {
		appContext = ApplicationProvider.getApplicationContext();
	}

	@Test
	public void testInsert() {
		final ContentResolver resolver = appContext.getContentResolver();

		final Uri url = TestProvider.CONTENT_HELPER.getDirUri(DatabaseTest.Status.TABLE_NAME);
		resolver.delete(url, null, null);

		Uri uri = resolver.insert(TestProvider.CONTENT_HELPER.getDirUri(DatabaseTest.Status.TABLE_NAME), getFullContentValues());
		assertNotNull(uri);
		assertEquals(1L, ContentUris.parseId(uri));
	}

	@Test
	public void testReadonly() {
		final String errMsg = "Table " + DatabaseTest.StatusReadonly.TABLE_NAME + " is readonly.";
		final ContentResolver resolver = appContext.getContentResolver();
		try {
			resolver.insert(TestProvider.CONTENT_HELPER.getDirUri(DatabaseTest.StatusReadonly.TABLE_NAME), new ContentValues());
			fail();
		} catch (IllegalArgumentException ex) {
			assertEquals(errMsg, ex.getMessage());
		}
		final Uri itemUri = TestProvider.CONTENT_HELPER.getItemUri(DatabaseTest.StatusReadonly.TABLE_NAME, 1);
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

	@Test
	public void testInsertAndSelect() {
		final ContentResolver resolver = appContext.getContentResolver();
		final Uri url = TestProvider.CONTENT_HELPER.getDirUri(DatabaseTest.Status.TABLE_NAME);
		resolver.delete(url, null, null);
		Uri uri = resolver.insert(url, getFullContentValues());
		assertNotNull(uri);
		assertEquals(1L, ContentUris.parseId(uri));
		final Cursor cursor = resolver.query(url, new String[]{DatabaseTest.Status.ID, DatabaseTest.Status.NAME}, null, null, null);
		assertNotNull(cursor);
		assertEquals(1, cursor.getCount());
		assertEquals(DatabaseTest.Status.ID, cursor.getColumnName(0));
		assertEquals(DatabaseTest.Status.NAME, cursor.getColumnName(1));
		assertTrue(cursor.moveToFirst());
		assertEquals(1, cursor.getInt(0));
		assertEquals("Test", cursor.getString(1));
	}

	private ContentValues getFullContentValues() {
		final ContentValues values = new ContentValues();
		values.put(DatabaseTest.Status.ID, 1);
		values.put(DatabaseTest.Status.NAME, "Test");
		return values;
	}
}