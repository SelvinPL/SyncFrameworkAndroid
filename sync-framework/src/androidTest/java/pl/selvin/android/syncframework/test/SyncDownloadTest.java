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

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import pl.selvin.android.syncframework.content.RequestExecutor;

/**
 * Downloading changes: the first sync, the stored server blob, applying rows and tombstones
 * the server sends, and the moreChangesAvailable loop.
 */
@RunWith(AndroidJUnit4.class)
public class SyncDownloadTest extends SyncTestCase {

	private static final String URI_ONE = "http://server/Item('one')";
	private static final String URI_TWO = "http://server/Item('two')";

	/** With no stored blob the provider has nothing to send, so it GETs. */
	@Test
	public void firstSyncIsAGetWithNoBody() {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", false,
				SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE, SyncJson.itemFields("one", "first")),
				SyncJson.row(SyncDatabase.Item.TYPE, URI_TWO, SyncJson.itemFields("two", "second"))));

		syncDefaultScope();

		assertEquals(1, SyncTestProvider.EXECUTOR.requestCount());
		final FakeRequestExecutor.RecordedRequest request = SyncTestProvider.EXECUTOR.lastRequest();
		assertEquals(RequestExecutor.GET, request.method);
		assertEquals(RequestExecutor.DOWNLOAD, request.type);
		assertEquals(SyncDatabase.SCOPE, request.scope);
		assertNull("a GET must not carry a body", request.body);

		assertEquals(2, visibleCount(SyncDatabase.Item.TABLE_NAME));
	}

	/** Downloaded rows are not dirty - they came from the server, nothing to send back. */
	@Test
	public void downloadedRowsCarryServerUriAndAreNotDirty() {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", false,
				SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE, SyncJson.itemFields("one", "first"))));

		syncDefaultScope();

		final SyncState state = syncState(SyncDatabase.Item.TABLE_NAME, "one");
		assertNotNull(state);
		assertEquals(URI_ONE, state.uri);
		assertNull(state.tempId);
		assertTrue(!state.isDirty);
		assertTrue(!state.isDeleted);
		assertEquals("first", columnValue(SyncDatabase.Item.TABLE_NAME, "one",
				SyncDatabase.Item.NAME));
	}

	@Test
	public void serverBlobIsStoredAfterSync() {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", false));

		syncDefaultScope();

		assertEquals("blob-1", storedBlob(SyncDatabase.SCOPE));
		assertEquals(0, storedBlobState(SyncDatabase.SCOPE));
	}

	/** Once a blob exists the provider POSTs it back, still as a download when nothing is dirty. */
	@Test
	public void secondSyncPostsTheStoredBlob() {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", false));
		syncDefaultScope();

		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-2", false));
		syncDefaultScope();

		assertEquals(2, SyncTestProvider.EXECUTOR.requestCount());
		final FakeRequestExecutor.RecordedRequest second = SyncTestProvider.EXECUTOR.lastRequest();
		assertEquals(RequestExecutor.POST, second.method);
		assertEquals(RequestExecutor.DOWNLOAD, second.type);
		assertNotNull(second.body);
		assertTrue("blob should be sent back: " + second.body, second.body.contains("blob-1"));

		assertEquals("blob-2", storedBlob(SyncDatabase.SCOPE));
	}

	/** A tombstone from the server removes the row outright - it is already gone upstream. */
	@Test
	public void serverTombstoneDeletesTheRow() {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", false,
				SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE, SyncJson.itemFields("one", "first"))));
		syncDefaultScope();
		assertEquals(1, rawCount(SyncDatabase.Item.TABLE_NAME));

		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-2", false,
				SyncJson.deletedRow(SyncDatabase.Item.TYPE, URI_ONE)));
		syncDefaultScope();

		assertEquals("row deleted upstream should leave nothing behind", 0,
				rawCount(SyncDatabase.Item.TABLE_NAME));
	}

	/** A second row for the same uri replaces it rather than duplicating. */
	@Test
	public void downloadingTheSameRowTwiceUpdatesIt() {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", false,
				SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE, SyncJson.itemFields("one", "first"))));
		syncDefaultScope();

		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-2", false,
				SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE, SyncJson.itemFields("one", "renamed"))));
		syncDefaultScope();

		assertEquals(1, rawCount(SyncDatabase.Item.TABLE_NAME));
		assertEquals("renamed", columnValue(SyncDatabase.Item.TABLE_NAME, "one",
				SyncDatabase.Item.NAME));
	}

	/**
	 * moreChangesAvailable keeps the loop going, and the rounds it produces are not all
	 * downloads.
	 * <p>
	 * noChanges is only worked out when a blob already exists, so on a first sync it stays
	 * false and the follow up round is an UPLOAD even though nothing is dirty. That upload
	 * sets forceMoreChanges, which buys one further round. A first sync that reports more
	 * changes therefore costs three requests: GET download, POST upload, POST download.
	 * <p>
	 * Forcing a download after an upload is the protocol: the reference client runs its
	 * upload phase and then its download phase unconditionally, and forceMoreChanges is how
	 * that is expressed here inside one loop.
	 * <p>
	 * Every round after the first is a POST either way - the blob has to go up, so both
	 * request types carry the same envelope. The only difference from the reference is which
	 * endpoint this one round goes to: there EnqueueUploadRequest returns early when there
	 * is nothing to send ("No data to upload. Skip upload phase."). Costing one extra round
	 * trip is all it amounts to - SqlSyncProviderService.ApplyChanges no-ops an upload with
	 * no entities, returning the client knowledge unchanged, so nothing server side is
	 * disturbed by it.
	 */
	@Test
	public void moreChangesAvailableRunsFurtherRounds() {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", true,
				SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE, SyncJson.itemFields("one", "first"))));
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-2", false,
				SyncJson.row(SyncDatabase.Item.TYPE, URI_TWO, SyncJson.itemFields("two", "second"))));
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-3", false));

		syncDefaultScope();

		assertEquals(3, SyncTestProvider.EXECUTOR.requestCount());
		assertRequest(0, RequestExecutor.GET, RequestExecutor.DOWNLOAD);
		assertRequest(1, RequestExecutor.POST, RequestExecutor.UPLOAD);
		assertRequest(2, RequestExecutor.POST, RequestExecutor.DOWNLOAD);

		assertEquals(2, visibleCount(SyncDatabase.Item.TABLE_NAME));
		assertEquals("blob-3", storedBlob(SyncDatabase.SCOPE));
		assertTrue("every response should have been consumed",
				!SyncTestProvider.EXECUTOR.hasQueuedResponses());
	}

	/**
	 * Nothing was dirty, so the forced upload round sends the same thing a download round
	 * would: the blob and an empty results array. Both request types POST that envelope -
	 * the blob has to go up either way - so only the endpoint and the extra round differ.
	 */
	@Test
	public void forcedUploadRoundSendsTheSameEnvelopeAsADownload() {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", true));
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-2", false));
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-3", false));

		syncDefaultScope();

		final FakeRequestExecutor.RecordedRequest upload =
				SyncTestProvider.EXECUTOR.requests().get(1);
		final FakeRequestExecutor.RecordedRequest download =
				SyncTestProvider.EXECUTOR.requests().get(2);
		assertEquals(RequestExecutor.UPLOAD, upload.type);
		assertEquals(RequestExecutor.DOWNLOAD, download.type);

		assertNotNull(upload.body);
		assertTrue("nothing was dirty, so results must be empty: " + upload.body,
				upload.body.contains("\"results\":[]"));
		assertTrue("a download round sends an empty results array too: " + download.body,
				download.body.contains("\"results\":[]"));
	}

	/**
	 * noChanges is set to true at the end of the first POST round and never cleared inside
	 * the loop, so the forced upload happens at most once per sync however many batches the
	 * server sends - four batches still cost exactly one of them.
	 */
	@Test
	public void forcedUploadHappensOnlyOnceRegardlessOfBatchCount() {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", true));
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-2", true));
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-3", true));
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-4", false));

		syncDefaultScope();

		assertEquals(4, SyncTestProvider.EXECUTOR.requestCount());
		assertRequest(0, RequestExecutor.GET, RequestExecutor.DOWNLOAD);
		assertRequest(1, RequestExecutor.POST, RequestExecutor.UPLOAD);
		assertRequest(2, RequestExecutor.POST, RequestExecutor.DOWNLOAD);
		assertRequest(3, RequestExecutor.POST, RequestExecutor.DOWNLOAD);
	}

	private void assertRequest(int index, int method, String type) {
		final FakeRequestExecutor.RecordedRequest request =
				SyncTestProvider.EXECUTOR.requests().get(index);
		assertEquals("request " + index + " method", method, request.method);
		assertEquals("request " + index + " type", type, request.type);
	}

	/**
	 * A column this client does not have, on a table it does - the other half of a schema
	 * skew, and the benign half: the field is parsed into the value map, no column claims
	 * it, and the row applies as normal. Also exercises the NameCache miss path, since an
	 * unknown field name is not in it.
	 */
	@Test
	public void unknownColumnInResponseIsIgnored() {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", false,
				SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
						SyncJson.itemFields("one", "first")
								+ ",\"ColumnFromTheFuture\":\"ignored\"")));

		syncDefaultScope();

		assertEquals(1, visibleCount(SyncDatabase.Item.TABLE_NAME));
		assertEquals("first", columnValue(SyncDatabase.Item.TABLE_NAME, "one",
				SyncDatabase.Item.NAME));
		assertEquals("blob-1", storedBlob(SyncDatabase.SCOPE));
	}

	/** A field name too long for the NameCache still reads correctly, just uncached. */
	@Test
	public void veryLongUnknownColumnNameIsHandled() {
		final StringBuilder name = new StringBuilder();
		for (int i = 0; i < 80; i++)
			name.append('x');
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", false,
				SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
						SyncJson.itemFields("one", "first")
								+ ",\"" + name + "\":\"ignored\"")));

		syncDefaultScope();

		assertEquals(1, visibleCount(SyncDatabase.Item.TABLE_NAME));
	}

	/** Only the scope being synced is sent - tables live in exactly one scope. */
	@Test
	public void syncingOneScopeLeavesTheOtherAlone() {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", false,
				SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE, SyncJson.itemFields("one", "first"))));

		syncDefaultScope();

		assertEquals(SyncDatabase.SCOPE, SyncTestProvider.EXECUTOR.lastRequest().scope);
		assertEquals(0, rawCount(SyncDatabase.Other.TABLE_NAME));
	}

	/** Counters the SyncAdapter reports back to the system. */
	@Test
	public void syncResultCountsInsertsAndDeletes() {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", false,
				SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE, SyncJson.itemFields("one", "first")),
				SyncJson.row(SyncDatabase.Item.TYPE, URI_TWO, SyncJson.itemFields("two", "second"))));
		assertEquals(2, syncDefaultScope().stats.numInserts);

		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-2", false,
				SyncJson.deletedRow(SyncDatabase.Item.TYPE, URI_ONE)));
		assertEquals(1, sync(SyncDatabase.SCOPE).stats.numDeletes);
	}
}
