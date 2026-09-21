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
import android.content.SyncResult;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Conflicts the server resolved for us. The server never asks - it decides and then reports,
 * so a conflicted entry is just a row with an extra __syncConflict member on it.
 * <p>
 * The shape, from the reference implementation: the entry itself is the <em>winning</em>
 * entity, and __syncConflict.conflictingChange carries the whole <em>losing</em> one.
 * SyncConflictResolution says which is which - ServerWins means "client version was ignored",
 * so the entry is the server's copy and the loser is ours. ClientWins is the mirror image.
 * <p>
 * Only those two are covered, because only those two can arrive. Merge is the third value of
 * SyncConflictResolution but not of ConflictResolutionPolicy, which is what a service is
 * configured with - it is reachable only if the host registers a custom SyncConflictInterceptor
 * that returns Merge along with a merged entity. Even then the service resolves it as
 * ClientWins internally and only the reported string differs, so there is nothing here that a
 * Merge fixture would exercise that the ClientWins one does not.
 * <p>
 * That is why the parser getting away with skipping the whole __syncConflict block produces
 * correct data for every resolution - applying the entry is applying the winner. What is
 * genuinely missing is the report: the reference client hands a SyncConflict to the consumer
 * so it can tell the user their edit was dropped, and there is nowhere for that to go here
 * (the "TODO: proper conflict resolution" in BaseContentProvider). These tests therefore pin
 * the data outcome, which is decided, and not the reporting, which is not.
 * <p>
 * One asymmetry is worth knowing about, and is pinned below: the server puts the tempId on
 * the losing entity under ServerWins and on the winning entity otherwise
 * (UploadChangesRequestProcessor), so a ServerWins conflict over a local insert arrives with
 * no tempId at all.
 */
@RunWith(AndroidJUnit4.class)
public class SyncConflictTest extends SyncTestCase {

	private static final String URI_ONE = "http://server/Item('one')";
	private static final String ASSIGNED_URI = "http://server/Item('assigned')";
	private static final String SERVER_WINS = "ServerWins";
	private static final String CLIENT_WINS = "ClientWins";

	/** Establishes a server blob, optionally seeding rows, so later syncs POST. */
	private void establishBlob(String... rows) {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", false, rows));
		syncDefaultScope();
	}

	/** An upload round is always followed by a forced download round. */
	private SyncResult syncUpload(String uploadResponse) {
		SyncTestProvider.EXECUTOR.enqueue(uploadResponse);
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-3", false));
		return syncDefaultScope();
	}

	/** Seeds row "one", then edits it locally so the next upload has something to conflict. */
	private void seedAndEditRowOne() {
		establishBlob(SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
				SyncJson.itemFields("one", "first")));
		final ContentValues values = new ContentValues();
		values.put(SyncDatabase.Item.NAME, "mine");
		assertEquals(1, resolver.update(dirUri(SyncDatabase.Item.TABLE_NAME), values,
				SyncDatabase.Item.ID + "=?", new String[]{"one"}));
	}

	// ---- ServerWins ---------------------------------------------------------------------

	/** Our edit lost, so the row has to end up holding the server's value, not ours. */
	@Test
	public void serverWinsOverwritesTheLocalEdit() {
		seedAndEditRowOne();

		syncUpload(SyncJson.response("blob-2", false,
				SyncJson.withConflict(
						SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
								SyncJson.itemFields("one", "theirs")),
						SERVER_WINS,
						SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
								SyncJson.itemFields("one", "mine")))));

		assertEquals("the conflict describes one row, not two", 1,
				rawCount(SyncDatabase.Item.TABLE_NAME));
		assertEquals("theirs", columnValue(SyncDatabase.Item.TABLE_NAME, "one",
				SyncDatabase.Item.NAME));
		final SyncState state = syncState(SyncDatabase.Item.TABLE_NAME, "one");
		assertNotNull(state);
		assertEquals(URI_ONE, state.uri);
		assertTrue("the losing edit must not be queued for another attempt", !state.isDirty);
	}

	/**
	 * conflictingChange is a whole entity, __metadata and all, sitting inside the entry. It
	 * gets skipped wholesale, so none of its fields may leak into the row being applied and it
	 * may not turn into a row of its own.
	 */
	@Test
	public void theLosingEntityIsNotApplied() {
		seedAndEditRowOne();

		syncUpload(SyncJson.response("blob-2", false,
				SyncJson.withConflict(
						SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
								SyncJson.itemFields("one", "theirs") + ",\""
										+ SyncDatabase.Item.QUANTITY + "\":1"),
						SERVER_WINS,
						SyncJson.row(SyncDatabase.Item.TYPE, ASSIGNED_URI,
								SyncJson.itemFields("one", "mine") + ",\""
										+ SyncDatabase.Item.QUANTITY + "\":99"))));

		assertEquals(1, rawCount(SyncDatabase.Item.TABLE_NAME));
		assertEquals("1", columnValue(SyncDatabase.Item.TABLE_NAME, "one",
				SyncDatabase.Item.QUANTITY));
		assertEquals(URI_ONE, syncState(SyncDatabase.Item.TABLE_NAME, "one").uri);
	}

	/**
	 * A local insert that lost. The server puts the tempId on the losing entity here, so the
	 * entry has none and cannot be matched the usual way - it is applied as a fresh insert
	 * instead. It still lands on one row rather than two, because the primary key is the same
	 * on both sides (it is what made it a conflict) and the insert replaces on conflict. The
	 * unreconciled row goes with it, taking its tempId and dirty flag.
	 */
	@Test
	public void serverWinsOverALocalInsertReplacesTheUnreconciledRow() {
		establishBlob();
		insertItem("local", "mine");
		final String tempId = syncState(SyncDatabase.Item.TABLE_NAME, "local").tempId;

		final SyncResult result = syncUpload(SyncJson.response("blob-2", false,
				SyncJson.withConflict(
						SyncJson.row(SyncDatabase.Item.TYPE, ASSIGNED_URI,
								SyncJson.itemFields("local", "theirs")),
						SERVER_WINS,
						SyncJson.row(SyncDatabase.Item.TYPE, ASSIGNED_URI, tempId,
								SyncJson.itemFields("local", "mine")))));

		assertEquals("replaced on the primary key, not duplicated", 1,
				rawCount(SyncDatabase.Item.TABLE_NAME));
		assertEquals("theirs", columnValue(SyncDatabase.Item.TABLE_NAME, "local",
				SyncDatabase.Item.NAME));
		final SyncState state = syncState(SyncDatabase.Item.TABLE_NAME, "local");
		assertEquals(ASSIGNED_URI, state.uri);
		assertNull("the tempId went with the row it identified", state.tempId);
		assertTrue("nothing left to send", !state.isDirty);
		assertEquals("no tempId to match on, so it is counted as an insert", 1,
				result.stats.numInserts);
		assertEquals(0, result.stats.numUpdates);
	}

	/**
	 * Server deleted, we updated. Under ServerWins the entry is a tombstone
	 * (LocalDeleteRemoteUpdate in SqlSyncProviderService sets IsTombstone on the live entity),
	 * and a tombstone entry carries no fields at all - only the conflict block hangs off it.
	 */
	@Test
	public void serverWinsWithATombstoneDeletesTheRow() {
		seedAndEditRowOne();

		final SyncResult result = syncUpload(SyncJson.response("blob-2", false,
				SyncJson.withConflict(
						SyncJson.deletedRow(SyncDatabase.Item.TYPE, URI_ONE),
						SERVER_WINS,
						SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
								SyncJson.itemFields("one", "mine")))));

		assertEquals("deleted upstream, so nothing is left to keep", 0,
				rawCount(SyncDatabase.Item.TABLE_NAME));
		assertEquals(1, result.stats.numDeletes);
	}

	/** A resolved conflict is a normal outcome, not an error: the round still commits. */
	@Test
	public void aConflictStillCommitsTheRound() {
		seedAndEditRowOne();

		syncUpload(SyncJson.response("blob-2", false,
				SyncJson.withConflict(
						SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
								SyncJson.itemFields("one", "theirs")),
						SERVER_WINS,
						SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
								SyncJson.itemFields("one", "mine")))));

		assertEquals("blob-3", storedBlob(SyncDatabase.SCOPE));
		assertEquals(0, storedBlobState(SyncDatabase.SCOPE));
		assertTrue("every response should have been consumed",
				!SyncTestProvider.EXECUTOR.hasQueuedResponses());
	}

	// ---- the other resolutions ------------------------------------------------------------

	/**
	 * ClientWins is the service default, and it is the mirror image: our version is the entry
	 * and carries the tempId, so a lost race over an insert reconciles exactly like a plain
	 * echo would.
	 */
	@Test
	public void clientWinsCarriesTheTempIdOnTheWinningEntry() {
		establishBlob();
		insertItem("local", "mine");
		final String tempId = syncState(SyncDatabase.Item.TABLE_NAME, "local").tempId;

		final SyncResult result = syncUpload(SyncJson.response("blob-2", false,
				SyncJson.withConflict(
						SyncJson.row(SyncDatabase.Item.TYPE, ASSIGNED_URI, tempId,
								SyncJson.itemFields("local", "mine")),
						CLIENT_WINS,
						SyncJson.row(SyncDatabase.Item.TYPE, ASSIGNED_URI,
								SyncJson.itemFields("local", "theirs")))));

		assertEquals(1, rawCount(SyncDatabase.Item.TABLE_NAME));
		assertEquals("mine", columnValue(SyncDatabase.Item.TABLE_NAME, "local",
				SyncDatabase.Item.NAME));
		final SyncState state = syncState(SyncDatabase.Item.TABLE_NAME, "local");
		assertEquals(ASSIGNED_URI, state.uri);
		assertNull(state.tempId);
		assertTrue(!state.isDirty);
		assertEquals("matched on tempId, so it is an update", 1, result.stats.numUpdates);
	}

	// ---- shape of the conflict block ------------------------------------------------------

	/**
	 * The reference writer appends __metadata after the fields and __syncConflict after that,
	 * so the entries a real server sends are not in the order the other tests build them in.
	 * Nothing is applied until the entry is fully read, so the order cannot matter - pinned
	 * here because every other fixture in these tests happens to put __metadata first.
	 */
	@Test
	public void memberOrderWithinTheEntryDoesNotMatter() {
		seedAndEditRowOne();

		final String asTheServerWritesIt = "{" + SyncJson.itemFields("one", "theirs")
				+ ",\"__metadata\":{\"uri\":\"" + URI_ONE + "\",\"type\":\""
				+ SyncDatabase.Item.TYPE + "\"}"
				+ ",\"__syncConflict\":{\"conflictResolution\":\"" + SERVER_WINS + "\","
				+ "\"conflictingChange\":" + SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
				SyncJson.itemFields("one", "mine")) + "}}";

		syncUpload(SyncJson.response("blob-2", false, asTheServerWritesIt));

		assertEquals(1, rawCount(SyncDatabase.Item.TABLE_NAME));
		assertEquals("theirs", columnValue(SyncDatabase.Item.TABLE_NAME, "one",
				SyncDatabase.Item.NAME));
		assertEquals(URI_ONE, syncState(SyncDatabase.Item.TABLE_NAME, "one").uri);
	}

	/**
	 * isResolved is part of the documented shape at the top of SYNC and the parser has a case
	 * for it, even though the reference writer never emits it. Reading the full documented
	 * block has to work as well as reading the one that actually arrives.
	 */
	@Test
	public void theDocumentedIsResolvedFieldIsRead() {
		seedAndEditRowOne();

		syncUpload(SyncJson.response("blob-2", false,
				SyncJson.withConflict(
						SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
								SyncJson.itemFields("one", "theirs")),
						true, SERVER_WINS,
						SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
								SyncJson.itemFields("one", "mine")))));

		assertEquals("theirs", columnValue(SyncDatabase.Item.TABLE_NAME, "one",
				SyncDatabase.Item.NAME));
		assertEquals("blob-3", storedBlob(SyncDatabase.SCOPE));
	}

	/**
	 * __sync.resolveConflicts, the other half of the documented shape. All the provider does
	 * with it is log, and that is all this can check: the flag must not derail the round.
	 */
	@Test
	public void theResolveConflictsFlagDoesNotDerailTheSync() {
		seedAndEditRowOne();

		SyncTestProvider.EXECUTOR.enqueue(SyncJson.responseResolvingConflicts("blob-2", false,
				SyncJson.withConflict(
						SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
								SyncJson.itemFields("one", "theirs")),
						SERVER_WINS,
						SyncJson.row(SyncDatabase.Item.TYPE, URI_ONE,
								SyncJson.itemFields("one", "mine")))));
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-3", false));
		syncDefaultScope();

		assertEquals("theirs", columnValue(SyncDatabase.Item.TABLE_NAME, "one",
				SyncDatabase.Item.NAME));
		assertEquals("blob-3", storedBlob(SyncDatabase.SCOPE));
		assertEquals(0, storedBlobState(SyncDatabase.SCOPE));
	}
}
