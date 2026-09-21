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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.UriMatcher;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import pl.selvin.android.autocontentprovider.content.ContentHelper;
import pl.selvin.android.autocontentprovider.db.TableInfo;
import pl.selvin.android.autocontentprovider.impl.DefaultDatabaseInfoFactory;

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

	/**
	 * A composite key builds its matcher signature from the column types: {@code #} for
	 * INTEGER and {@code *} for everything else. Composite keys on {GroupID, Code, Seq},
	 * so the signature is Composite/#&#47;*&#47;#.
	 */
	@Test
	public void compositeKeyItemUriMatches() {
		final ContentHelper<TableInfo> contentHelper = TestProvider.CONTENT_HELPER;

		final Uri dirUri = contentHelper.getDirUri(DatabaseTest.Composite.TABLE_NAME);
		final int base = contentHelper.matchUri(dirUri) & ContentHelper.uriCode;

		final Uri itemUri = contentHelper.getItemUri(DatabaseTest.Composite.TABLE_NAME,
				"5", "ABC", "7");
		assertEquals(baseBuilder().appendPath(DatabaseTest.Composite.TABLE_NAME).appendPath("5")
				.appendPath("ABC").appendPath("7").build(), itemUri);
		assertEquals(ContentHelper.uriCodeItemFlag,
				contentHelper.matchUri(itemUri) & ContentHelper.uriCodeItemFlag);
		assertEquals(base, contentHelper.matchUri(itemUri) & ContentHelper.uriCode);
	}

	/** GroupID is INTEGER, so a non numeric first segment cannot be that table's item Uri. */
	@Test
	public void compositeKeyItemUriRejectsWrongTypedSegment() {
		final Uri notAnItemUri = Uri.parse("content://" + TestProvider.AUTHORITY + "/"
				+ DatabaseTest.Composite.TABLE_NAME + "/notANumber/ABC/7");

		assertEquals(UriMatcher.NO_MATCH, TestProvider.CONTENT_HELPER.matchUri(notAnItemUri));
	}

	@Test
	public void clearUriMatchesClearCode() {
		assertEquals(ContentHelper.uriClearCode,
				TestProvider.CONTENT_HELPER.matchUri(TestProvider.CONTENT_HELPER.CLEAR_URI));
	}

	@Test
	public void unknownUriDoesNotMatch() {
		assertEquals(UriMatcher.NO_MATCH, TestProvider.CONTENT_HELPER.matchUri(
				Uri.parse("content://" + TestProvider.AUTHORITY + "/NoSuchTable")));
	}

	@Test
	public void itemUriWithoutPrimaryKeysIsRejected() {
		final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> TestProvider.CONTENT_HELPER.getItemUri(DatabaseTest.Status.TABLE_NAME));
		assertEquals("primary_keys should not be empty nor null", ex.getMessage());
	}

	// ---- syncToNetwork ------------------------------------------------------------------

	/** Absent parameter means true - notifications sync to network unless opted out. */
	@Test
	public void syncToNetworkDefaultsToTrueWhenAbsent() {
		assertTrue(ContentHelper.checkSyncToNetwork(
				TestProvider.CONTENT_HELPER.getDirUri(DatabaseTest.Status.TABLE_NAME)));
	}

	/** Dir builder only writes the parameter when opting out, so true leaves no trace. */
	@Test
	public void dirUriOmitsSyncToNetworkParameterWhenTrue() {
		final ContentHelper<TableInfo> contentHelper = TestProvider.CONTENT_HELPER;

		final Uri optedIn = contentHelper.getDirUri(DatabaseTest.Status.TABLE_NAME, true);
		assertNull(optedIn.getQueryParameter(ContentHelper.PARAMETER_SYNC_TO_NETWORK));
		assertTrue(ContentHelper.checkSyncToNetwork(optedIn));

		final Uri optedOut = contentHelper.getDirUri(DatabaseTest.Status.TABLE_NAME, false);
		assertEquals("false", optedOut.getQueryParameter(ContentHelper.PARAMETER_SYNC_TO_NETWORK));
		assertTrue(!ContentHelper.checkSyncToNetwork(optedOut));
	}

	/** Item builder always writes it, unlike the dir builder above. */
	@Test
	public void itemUriAlwaysWritesSyncToNetworkParameter() {
		final ContentHelper<TableInfo> contentHelper = TestProvider.CONTENT_HELPER;

		assertEquals("true", contentHelper.getItemUri(DatabaseTest.Status.TABLE_NAME, true, "1")
				.getQueryParameter(ContentHelper.PARAMETER_SYNC_TO_NETWORK));
		assertEquals("false", contentHelper.getItemUri(DatabaseTest.Status.TABLE_NAME, false, 1L)
				.getQueryParameter(ContentHelper.PARAMETER_SYNC_TO_NETWORK));
	}

	// ---- table identity -----------------------------------------------------------------

	/**
	 * Table names are how tables are looked up, so two tables sharing one name must be
	 * rejected when the ContentHelper is built rather than last-one-wins.
	 */
	@Test
	public void duplicateTableNameIsRejected() {
		final RuntimeException ex = assertThrows(RuntimeException.class,
				() -> new ContentHelper<>(DuplicateTableNames.class, TestProvider.AUTHORITY,
						new DefaultDatabaseInfoFactory(), "duplicate_db", 1));

		assertEquals("Duplicate table name: " + DuplicateTableNames.SHARED_NAME,
				ex.getCause().getMessage());
	}

	@Test
	public void getTableFromNameAndCodeAgree() {
		final ContentHelper<TableInfo> contentHelper = TestProvider.CONTENT_HELPER;
		final Uri dirUri = contentHelper.getDirUri(DatabaseTest.Composite.TABLE_NAME);

		final TableInfo byName = contentHelper.getTableFromName(DatabaseTest.Composite.TABLE_NAME);
		final TableInfo byCode = contentHelper.getTableFromCode(
				contentHelper.matchUri(dirUri) & ContentHelper.uriCode);

		assertNotNull(byName);
		assertSame(byName, byCode);
	}
}