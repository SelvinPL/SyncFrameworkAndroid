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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import pl.selvin.android.syncframework.content.RequestExecutor;

/**
 * Uploading local changes: tempId to uri reconciliation, tombstones versus hard deletes, and
 * the dirty flag bookkeeping that decides what gets sent.
 */
@RunWith(AndroidJUnit4.class)
public class SyncUploadTest extends SyncTestCase {

	private static final String URI_ONE = "http://server/Item('one')";
	private static final String ASSIGNED_URI = "http://server/Item('assigned')";

	/** Establishes a server blob, optionally seeding rows, so later syncs POST. */
	private void establishBlob(String... rows) {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", false, rows));
		syncDefaultScope();
	}

	/** An upload round is always followed by a forced download round. */
	private void syncUpload(String uploadResponse) {
		SyncTestProvider.EXECUTOR.enqueue(uploadResponse);
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-3", false));
		syncDefaultScope();
	}

	// ---- local changes ------------------------------------------------------------------

	@Test
	public void localInsertIsDirtyAndCarriesATempId() {
		insertItem("local", "mine");

		final SyncState state = syncState(SyncDatabase.Item.TABLE_NAME, "local");
		assertNotNull(state);
		assertNotNull("a new local row needs a tempId to be matched on later", state.tempId);
		assertNull("nothing has been assigned by the server yet", state.uri);
		assertTrue(state.isDirty);
		assertTrue(!state.isDeleted);
	}

	@Test
	public void localUpdateMarksRowDirty() {
		establishBlob(SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
				SyncJson.itemFields("one", "first")));
		assertTrue(!syncState(SyncDatabase.Item.TABLE_NAME, "one").isDirty);

		final ContentValues values = new ContentValues();
		values.put(SyncDatabase.Item.NAME, "renamed");
		assertEquals(1, resolver.update(dirUri(SyncDatabase.Item.TABLE_NAME), values,
				SyncDatabase.Item.ID + "=?", new String[]{"one"}));

		assertTrue(syncState(SyncDatabase.Item.TABLE_NAME, "one").isDirty);
	}

	// ---- tempId reconciliation ----------------------------------------------------------

	@Test
	public void uploadSendsTempIdForARowTheServerHasNotSeen() {
		establishBlob();
		insertItem("local", "mine");
		final String tempId = syncState(SyncDatabase.Item.TABLE_NAME, "local").tempId;

		syncUpload(SyncJson.response("blob-2", false));

		final FakeRequestExecutor.RecordedRequest upload =
				SyncTestProvider.EXECUTOR.requests().get(1);
		assertEquals(RequestExecutor.UPLOAD, upload.type);
		assertTrue("upload should carry the tempId: " + upload.body,
				upload.body.contains(tempId));
		assertTrue("upload should carry the row's data: " + upload.body,
				upload.body.contains("mine"));
	}

	/**
	 * The heart of it: the server answers with the uri it assigned plus the tempId we sent,
	 * and that has to update the existing row rather than insert a second one.
	 */
	@Test
	public void serverEchoReconcilesTempIdIntoServerUri() {
		establishBlob();
		insertItem("local", "mine");
		final String tempId = syncState(SyncDatabase.Item.TABLE_NAME, "local").tempId;

		syncUpload(SyncJson.response("blob-2", false,
				SyncJson.row(SyncDatabase.Item.TYPE, ASSIGNED_URI, tempId,
						SyncJson.itemFields("local", "mine"))));

		assertEquals("reconciliation must update in place, not duplicate", 1,
				rawCount(SyncDatabase.Item.TABLE_NAME));
		final SyncState state = syncState(SyncDatabase.Item.TABLE_NAME, "local");
		assertNotNull(state);
		assertEquals(ASSIGNED_URI, state.uri);
		assertNull("tempId is spent once the server assigned a uri", state.tempId);
		assertTrue("reconciled row is no longer pending", !state.isDirty);
	}

	@Test
	public void reconciliationCountsAsAnUpdateNotAnInsert() {
		establishBlob();
		insertItem("local", "mine");
		final String tempId = syncState(SyncDatabase.Item.TABLE_NAME, "local").tempId;

		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-2", false,
				SyncJson.row(SyncDatabase.Item.TYPE, ASSIGNED_URI, tempId,
						SyncJson.itemFields("local", "mine"))));
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-3", false));

		assertEquals(1, syncDefaultScope().stats.numUpdates);
	}

	/** A row that already has a uri is sent by uri, and stops being dirty once sent. */
	@Test
	public void uploadSendsUriAndClearsDirtyForAnExistingRow() {
		establishBlob(SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
				SyncJson.itemFields("one", "first")));
		final ContentValues values = new ContentValues();
		values.put(SyncDatabase.Item.NAME, "renamed");
		resolver.update(dirUri(SyncDatabase.Item.TABLE_NAME), values,
				SyncDatabase.Item.ID + "=?", new String[]{"one"});

		syncUpload(SyncJson.response("blob-2", false));

		final String body = SyncTestProvider.EXECUTOR.requests().get(1).body;
		assertTrue("upload should identify the row by uri: " + body, body.contains(URI_ONE));
		assertTrue("changed value should be sent: " + body, body.contains("renamed"));
		assertTrue("row was sent, so it is no longer pending",
				!syncState(SyncDatabase.Item.TABLE_NAME, "one").isDirty);
	}

	// ---- deletes ------------------------------------------------------------------------

	/**
	 * Deleting a row the server knows about cannot remove it locally - the deletion itself
	 * still has to be uploaded, so the row stays as a tombstone and is hidden from queries.
	 */
	@Test
	public void deletingASyncedRowLeavesAHiddenTombstone() {
		establishBlob(SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
				SyncJson.itemFields("one", "first")));

		assertEquals(1, resolver.delete(dirUri(SyncDatabase.Item.TABLE_NAME),
				SyncDatabase.Item.ID + "=?", new String[]{"one"}));

		assertEquals("tombstone must stay until it has been uploaded", 1,
				rawCount(SyncDatabase.Item.TABLE_NAME));
		assertEquals("tombstone must not be visible to callers", 0,
				visibleCount(SyncDatabase.Item.TABLE_NAME));
		final SyncState state = syncState(SyncDatabase.Item.TABLE_NAME, "one");
		assertTrue(state.isDeleted);
		assertTrue(state.isDirty);
	}

	/**
	 * A row the server never saw has nothing to report, so deleting it removes it outright
	 * rather than leaving a tombstone that would never resolve.
	 */
	@Test
	public void deletingAnUnsyncedRowRemovesItImmediately() {
		establishBlob();
		insertItem("local", "mine");
		assertEquals(1, rawCount(SyncDatabase.Item.TABLE_NAME));

		assertEquals(1, resolver.delete(dirUri(SyncDatabase.Item.TABLE_NAME),
				SyncDatabase.Item.ID + "=?", new String[]{"local"}));

		assertEquals("never uploaded, so nothing to tombstone", 0,
				rawCount(SyncDatabase.Item.TABLE_NAME));
	}

	/** Once the deletion has been sent, the tombstone is dropped. */
	@Test
	public void uploadingATombstoneRemovesIt() {
		establishBlob(SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
				SyncJson.itemFields("one", "first")));
		resolver.delete(dirUri(SyncDatabase.Item.TABLE_NAME),
				SyncDatabase.Item.ID + "=?", new String[]{"one"});

		syncUpload(SyncJson.response("blob-2", false));

		final String body = SyncTestProvider.EXECUTOR.requests().get(1).body;
		assertTrue("deletion should be reported: " + body,
				body.contains("\"" + "isDeleted" + "\":true"));
		assertTrue("deleted row should be identified by uri: " + body, body.contains(URI_ONE));
		assertEquals("tombstone is spent once uploaded", 0,
				rawCount(SyncDatabase.Item.TABLE_NAME));
	}

	// ---- value conversion ---------------------------------------------------------------

	/**
	 * DATETIME crosses the wire as /Date(millis)/ and is stored as UTC "yyyy-MM-dd HH:mm:ss".
	 * Down and back up again has to land on the same instant.
	 */
	@Test
	public void dateTimeSurvivesDownloadAndUpload() {
		final long millis = 1700000000000L;
		establishBlob(SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
				SyncJson.itemFields("one", "first") + ",\"" + SyncDatabase.Item.CREATED + "\":"
						+ SyncJson.msDate(millis)));

		assertEquals("2023-11-14 22:13:20",
				columnValue(SyncDatabase.Item.TABLE_NAME, "one", SyncDatabase.Item.CREATED));

		final ContentValues values = new ContentValues();
		values.put(SyncDatabase.Item.NAME, "renamed");
		resolver.update(dirUri(SyncDatabase.Item.TABLE_NAME), values,
				SyncDatabase.Item.ID + "=?", new String[]{"one"});
		syncUpload(SyncJson.response("blob-2", false));

		final String body = SyncTestProvider.EXECUTOR.requests().get(1).body;
		assertTrue("date should go back up unchanged: " + body,
				body.contains("/Date(" + millis + ")/"));
	}

	/** A null coming down from the server is stored as a real SQL NULL. */
	@Test
	public void nullColumnIsStoredAsNull() {
		establishBlob(SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
				"\"" + SyncDatabase.Item.ID + "\":\"one\",\""
						+ SyncDatabase.Item.NAME + "\":null"));

		assertNull(columnValue(SyncDatabase.Item.TABLE_NAME, "one", SyncDatabase.Item.NAME));
	}

	/**
	 * Null columns are left out of the upload rather than written as null: the bundled
	 * JsonUtf8Writer pins serializeNulls to false, so nullValue() drops the pending name.
	 * That is the default codec's choice, not the protocol's - a caller who needs explicit
	 * nulls supplies their own JsonFactory to the BaseContentProvider constructor.
	 */
	@Test
	public void nullColumnsAreOmittedFromUpload() {
		establishBlob(SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
				"\"" + SyncDatabase.Item.ID + "\":\"one\",\""
						+ SyncDatabase.Item.NAME + "\":null"));

		final ContentValues values = new ContentValues();
		values.put(SyncDatabase.Item.QUANTITY, 5);
		resolver.update(dirUri(SyncDatabase.Item.TABLE_NAME), values,
				SyncDatabase.Item.ID + "=?", new String[]{"one"});
		syncUpload(SyncJson.response("blob-2", false));

		final String body = SyncTestProvider.EXECUTOR.requests().get(1).body;
		assertTrue("non null columns are still sent: " + body, body.contains("\"Quantity\":5"));
		assertTrue("null column should be absent entirely: " + body,
				!body.contains("\"" + SyncDatabase.Item.NAME + "\""));
	}
}
