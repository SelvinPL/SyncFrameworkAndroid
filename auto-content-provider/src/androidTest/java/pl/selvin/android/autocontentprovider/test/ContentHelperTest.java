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

package pl.selvin.android.autocontentprovider.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import pl.selvin.android.autocontentprovider.content.ContentHelper;
import pl.selvin.android.autocontentprovider.db.TableInfo;

@RunWith(AndroidJUnit4.class)
public class ContentHelperTest {

	private Uri.Builder baseBuilder() {
		return Uri.parse("content://" + TestProvider.AUTHORITY).buildUpon();
	}

	@Test
	public void getUriTest() {
		final ContentHelper<TableInfo> contentHelper = TestProvider.CONTENT_HELPER;

		final Uri statusDirUri = contentHelper.getDirUri(DatabaseTest.Status.TABLE_NAME);
		assertEquals(baseBuilder().appendPath(DatabaseTest.Status.TABLE_NAME).build(),
				statusDirUri);
		assertEquals(0, contentHelper.matchUri(statusDirUri) & ContentHelper.uriCodeItemFlag);
		assertEquals(0, contentHelper.matchUri(statusDirUri) & ContentHelper.uriCodeItemRowIDFlag);
		int statusUriCodeBase = contentHelper.matchUri(statusDirUri) & ContentHelper.uriCode;

		final Uri statusItemROWIDUri = contentHelper.getItemUri(DatabaseTest.Status.TABLE_NAME, 1);
		assertEquals(baseBuilder().appendPath(DatabaseTest.Status.TABLE_NAME).appendPath("ROWID").appendPath("1").build(),
				statusItemROWIDUri);
		assertEquals(ContentHelper.uriCodeItemFlag, contentHelper.matchUri(statusItemROWIDUri) & ContentHelper.uriCodeItemFlag);
		assertEquals(ContentHelper.uriCodeItemRowIDFlag, contentHelper.matchUri(statusItemROWIDUri) & ContentHelper.uriCodeItemRowIDFlag);
		assertEquals(statusUriCodeBase, contentHelper.matchUri(statusItemROWIDUri) & ContentHelper.uriCode);


		final Uri statusItemPKUri = contentHelper.getItemUri(DatabaseTest.Status.TABLE_NAME, "1");
		assertEquals(baseBuilder().appendPath(DatabaseTest.Status.TABLE_NAME).appendPath("1").build(),
				statusItemPKUri);
		assertEquals(ContentHelper.uriCodeItemFlag, contentHelper.matchUri(statusItemPKUri) & ContentHelper.uriCodeItemFlag);
		assertEquals(ContentHelper.uriCodeItemFlag, contentHelper.matchUri(statusItemPKUri) & ContentHelper.uriCodeItemRowIDFlag);
		assertEquals(statusUriCodeBase, contentHelper.matchUri(statusItemPKUri) & ContentHelper.uriCode);


		final Uri statusReadonlyDirUri = contentHelper.getDirUri(DatabaseTest.StatusReadonly.TABLE_NAME);
		assertEquals(baseBuilder().appendPath(DatabaseTest.StatusReadonly.TABLE_NAME).build(),
				statusReadonlyDirUri);
		assertEquals(0, contentHelper.matchUri(statusReadonlyDirUri) & ContentHelper.uriCodeItemFlag);
		assertEquals(0, contentHelper.matchUri(statusReadonlyDirUri) & ContentHelper.uriCodeItemRowIDFlag);
		int statusReadonlyUriCodeBase = contentHelper.matchUri(statusReadonlyDirUri) & ContentHelper.uriCode;

		assertNotEquals(statusUriCodeBase, statusReadonlyUriCodeBase);

		final Uri statusReadonlyItemROWIDUri = contentHelper.getItemUri(DatabaseTest.StatusReadonly.TABLE_NAME, 1);
		assertEquals(baseBuilder().appendPath(DatabaseTest.StatusReadonly.TABLE_NAME).appendPath("ROWID").appendPath("1").build(),
				statusReadonlyItemROWIDUri);
		assertEquals(ContentHelper.uriCodeItemFlag, contentHelper.matchUri(statusReadonlyItemROWIDUri) & ContentHelper.uriCodeItemFlag);
		assertEquals(ContentHelper.uriCodeItemRowIDFlag, contentHelper.matchUri(statusReadonlyItemROWIDUri) & ContentHelper.uriCodeItemRowIDFlag);
		assertEquals(statusReadonlyUriCodeBase, contentHelper.matchUri(statusReadonlyItemROWIDUri) & ContentHelper.uriCode);


		final Uri statusReadonlyItemPKUri = contentHelper.getItemUri(DatabaseTest.StatusReadonly.TABLE_NAME, "1");
		assertEquals(baseBuilder().appendPath(DatabaseTest.StatusReadonly.TABLE_NAME).appendPath("1").build(),
				statusReadonlyItemPKUri);
		assertEquals(ContentHelper.uriCodeItemFlag, contentHelper.matchUri(statusReadonlyItemPKUri) & ContentHelper.uriCodeItemFlag);
		assertEquals(ContentHelper.uriCodeItemFlag, contentHelper.matchUri(statusReadonlyItemPKUri) & ContentHelper.uriCodeItemRowIDFlag);
		assertEquals(statusReadonlyUriCodeBase, contentHelper.matchUri(statusReadonlyItemPKUri) & ContentHelper.uriCode);
	}
}