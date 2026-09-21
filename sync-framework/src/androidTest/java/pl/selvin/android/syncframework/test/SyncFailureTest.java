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
 * What happens when a sync does not go to plan: malformed payloads, server errors, and the
 * codec seam that decides how payloads are read and written in the first place.
 */
@RunWith(AndroidJUnit4.class)
public class SyncFailureTest extends SyncTestCase {

	private static final String URI_ONE = "http://server/Item('one')";

	private void establishBlobWithOneRow() {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", false,
				SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE, SyncJson.itemFields("one", "first"))));
		syncDefaultScope();
		assertEquals(1, rawCount(SyncDatabase.Item.TABLE_NAME));
	}

	/**
	 * A response that fails to parse must leave the database exactly as it was - the round
	 * runs inside a transaction that is rolled back rather than committed.
	 */
	@Test
	public void malformedResponseRollsBackAndKeepsExistingData() {
		establishBlobWithOneRow();

		SyncTestProvider.EXECUTOR.enqueue("{\"d\":{\"__sync\":{\"serverBlob\":\"blob-2\"},"
				+ "\"results\":[{\"__metadata\":{\"uri\":\"http://server/Item('two')\","
				+ "\"type\":\"" + SyncDatabase.Item.TYPE + "\"},\"ID\":\"two\"");
		syncDefaultScope();

		assertEquals("half applied rows must not survive a failed sync", 1,
				rawCount(SyncDatabase.Item.TABLE_NAME));
		assertEquals("first", columnValue(SyncDatabase.Item.TABLE_NAME, "one",
				SyncDatabase.Item.NAME));
	}

	/** A failed sync records the blob it started from, flagged with state -1. */
	@Test
	public void failedSyncKeepsOriginalBlobAndMarksItFailed() {
		establishBlobWithOneRow();

		SyncTestProvider.EXECUTOR.enqueue("not json at all");
		syncDefaultScope();

		assertEquals("blob-1", storedBlob(SyncDatabase.SCOPE));
		assertEquals(-1, storedBlobState(SyncDatabase.SCOPE));
	}

	@Test
	public void malformedResponseIsCountedAsAParseException() {
		establishBlobWithOneRow();

		SyncTestProvider.EXECUTOR.enqueue("not json at all");

		assertEquals(1, syncDefaultScope().stats.numParseExceptions);
	}

	/** A payload missing the "d" envelope is rejected rather than silently ignored. */
	@Test
	public void responseWithoutEnvelopeIsRejected() {
		establishBlobWithOneRow();

		SyncTestProvider.EXECUTOR.enqueue("{\"notD\":{}}");
		final long parseExceptions = syncDefaultScope().stats.numParseExceptions;

		assertEquals(1, parseExceptions);
		assertEquals(1, rawCount(SyncDatabase.Item.TABLE_NAME));
	}

	/** An unrecognised server error ends the sync as an IO failure. */
	@Test
	public void serverErrorIsCountedAsAnIoException() {
		establishBlobWithOneRow();

		SyncTestProvider.EXECUTOR.enqueueError(500, "something went wrong");

		assertEquals(1, syncDefaultScope().stats.numIoExceptions);
		assertEquals(-1, storedBlobState(SyncDatabase.SCOPE));
	}

	/**
	 * "Scope does not exist" is recoverable: the scope's tables and blob are dropped and the
	 * sync restarts from nothing, as a first sync would.
	 */
	@Test
	public void missingScopeErrorClearsScopeAndRetries() {
		establishBlobWithOneRow();

		SyncTestProvider.EXECUTOR.enqueueError(400, "Scope does not exist");
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-fresh", false,
				SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE, SyncJson.itemFields("one", "rebuilt"))));

		syncDefaultScope();

		// one to establish the blob, then the rejected round and the retry
		assertEquals(3, SyncTestProvider.EXECUTOR.requestCount());
		// dropping the blob puts the retry back to a first sync: a GET with no body
		assertEquals(RequestExecutor.GET, SyncTestProvider.EXECUTOR.lastRequest().method);
		assertNull(SyncTestProvider.EXECUTOR.lastRequest().body);
		assertEquals("blob-fresh", storedBlob(SyncDatabase.SCOPE));
		assertEquals(1, rawCount(SyncDatabase.Item.TABLE_NAME));
		assertEquals("rebuilt", columnValue(SyncDatabase.Item.TABLE_NAME, "one",
				SyncDatabase.Item.NAME));
	}

	// ---- upload durability --------------------------------------------------------------

	/**
	 * Serializing an upload is not read only: getChanges clears isDirty on everything it
	 * writes. That happens inside the round's transaction, so a failed upload has to put the
	 * flag back - otherwise the change is gone locally and was never received by the server.
	 */
	@Test
	public void failedUploadLeavesLocalChangesDirtyForRetry() {
		establishBlobWithOneRow();
		final ContentValues values = new ContentValues();
		values.put(SyncDatabase.Item.NAME, "renamed");
		resolver.update(dirUri(SyncDatabase.Item.TABLE_NAME), values,
				SyncDatabase.Item.ID + "=?", new String[]{"one"});
		assertTrue(syncState(SyncDatabase.Item.TABLE_NAME, "one").isDirty);

		SyncTestProvider.EXECUTOR.enqueueError(500, "boom");
		syncDefaultScope();

		assertTrue("a failed upload must leave the row pending",
				syncState(SyncDatabase.Item.TABLE_NAME, "one").isDirty);
		assertEquals("the local edit itself must survive", "renamed",
				columnValue(SyncDatabase.Item.TABLE_NAME, "one", SyncDatabase.Item.NAME));
		assertEquals("blob-1", storedBlob(SyncDatabase.SCOPE));
		assertEquals(-1, storedBlobState(SyncDatabase.SCOPE));
	}

	/**
	 * Same hazard, worse consequence: uploading a tombstone physically deletes it, so a
	 * failed upload that did not roll back would drop the deletion entirely - the row would
	 * be gone locally and still present on the server, with nothing left to report.
	 */
	@Test
	public void failedUploadKeepsTombstoneForRetry() {
		establishBlobWithOneRow();
		resolver.delete(dirUri(SyncDatabase.Item.TABLE_NAME),
				SyncDatabase.Item.ID + "=?", new String[]{"one"});
		assertEquals(1, rawCount(SyncDatabase.Item.TABLE_NAME));

		SyncTestProvider.EXECUTOR.enqueueError(500, "boom");
		syncDefaultScope();

		assertEquals("tombstone must survive a failed upload", 1,
				rawCount(SyncDatabase.Item.TABLE_NAME));
		final SyncState state = syncState(SyncDatabase.Item.TABLE_NAME, "one");
		assertNotNull(state);
		assertTrue(state.isDeleted);
		assertTrue("deletion is still pending", state.isDirty);
	}

	/**
	 * The reference client refuses to download after a failed upload, because advancing the
	 * anchor would strand the un-uploaded rows. Here the rounds commit separately, so the
	 * reverse case is what matters: a successful upload must keep its result even though the
	 * download round that follows it fails.
	 */
	@Test
	public void successfulUploadSurvivesAFailedDownloadRound() {
		establishBlobWithOneRow();
		final ContentValues values = new ContentValues();
		values.put(SyncDatabase.Item.NAME, "renamed");
		resolver.update(dirUri(SyncDatabase.Item.TABLE_NAME), values,
				SyncDatabase.Item.ID + "=?", new String[]{"one"});

		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-2", false));
		SyncTestProvider.EXECUTOR.enqueueError(500, "boom");
		syncDefaultScope();

		assertTrue("the upload round committed, so the row is no longer pending",
				!syncState(SyncDatabase.Item.TABLE_NAME, "one").isDirty);
		assertEquals("blob from the committed upload round is kept", "blob-2",
				storedBlob(SyncDatabase.SCOPE));
		assertEquals("but the sync is still marked as failed", -1,
				storedBlobState(SyncDatabase.SCOPE));
	}

	// ---- codec seam ---------------------------------------------------------------------

	/**
	 * BaseContentProvider takes a JsonFactory, and this provider is built with one. If the
	 * parameter were ignored, a caller could not change how payloads are read or written -
	 * which is the supported way to get behaviour the bundled codec does not offer, such as
	 * serializing nulls.
	 */
	@Test
	public void suppliedJsonFactoryIsUsedForBothDirections() {
		SyncTestProvider.JSON_FACTORY.reset();

		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", false));
		syncDefaultScope();
		assertEquals("response should be read through the supplied factory", 1,
				SyncTestProvider.JSON_FACTORY.readersCreated());

		insertItem("local", "mine");
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-2", false));
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-3", false));
		syncDefaultScope();

		assertTrue("upload body should be written through the supplied factory",
				SyncTestProvider.JSON_FACTORY.writersCreated() > 0);
		assertEquals(3, SyncTestProvider.JSON_FACTORY.readersCreated());
	}

	@Test
	public void runningOutOfResponsesSurfacesAsIoFailureNotACrash() {
		establishBlobWithOneRow();

		// nothing enqueued - the fake executor throws IOException, as a dead network would
		final long ioExceptions = syncDefaultScope().stats.numIoExceptions;

		assertEquals(1, ioExceptions);
		assertNotNull(syncState(SyncDatabase.Item.TABLE_NAME, "one"));
	}
}
