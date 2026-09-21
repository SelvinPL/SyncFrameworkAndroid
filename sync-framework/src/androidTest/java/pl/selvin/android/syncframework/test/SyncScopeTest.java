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

import java.util.ArrayList;
import java.util.List;

import pl.selvin.android.syncframework.content.SyncTableInfo;

/**
 * Scopes are a sync grouping, not table identity: a table belongs to exactly one scope, and
 * work done for one scope must not touch another's tables or its stored blob.
 */
@RunWith(AndroidJUnit4.class)
public class SyncScopeTest extends SyncTestCase {

	private static final String ITEM_URI = "http://server/Item('one')";
	private static final String OTHER_URI = "http://server/Other('x')";

	private static List<String> tableNames(List<SyncTableInfo> tables) {
		final ArrayList<String> names = new ArrayList<>(tables.size());
		for (final SyncTableInfo table : tables)
			names.add(table.name);
		return names;
	}

	/** Seeds one row and a blob into each scope. */
	private void seedBothScopes() {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("item-blob", false,
				SyncJson.row(SyncDatabase.Item.TYPE, ITEM_URI, SyncJson.itemFields("one", "first"))));
		syncDefaultScope();

		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("other-blob", false,
				SyncJson.row(SyncDatabase.Other.TYPE, OTHER_URI,
						"\"" + SyncDatabase.Other.ID + "\":\"x\",\""
								+ SyncDatabase.Other.NAME + "\":\"other row\"")));
		sync(SyncDatabase.OTHER_SCOPE);

		assertEquals(1, rawCount(SyncDatabase.Item.TABLE_NAME));
		assertEquals(1, rawCount(SyncDatabase.Other.TABLE_NAME));
	}

	// ---- lookup -------------------------------------------------------------------------

	@Test
	public void tablesAreGroupedByScope() {
		assertEquals(List.of(SyncDatabase.Item.TABLE_NAME), tableNames(
				SyncTestProvider.CONTENT_HELPER.getTableForScope(SyncDatabase.SCOPE)));
		assertEquals(List.of(SyncDatabase.Other.TABLE_NAME), tableNames(
				SyncTestProvider.CONTENT_HELPER.getTableForScope(SyncDatabase.OTHER_SCOPE)));
	}

	@Test
	public void everyScopeIsListed() {
		assertTrue(SyncTestProvider.CONTENT_HELPER.getScopes()
				.containsAll(List.of(SyncDatabase.SCOPE, SyncDatabase.OTHER_SCOPE)));
	}

	/** __metadata.type is "<scope>.<table>", which is how a response row finds its table. */
	@Test
	public void tableIsFoundByScopedType() {
		final SyncTableInfo item =
				SyncTestProvider.CONTENT_HELPER.getTableFromType(SyncDatabase.Item.TYPE);
		assertNotNull(item);
		assertEquals(SyncDatabase.Item.TABLE_NAME, item.name);

		final SyncTableInfo other =
				SyncTestProvider.CONTENT_HELPER.getTableFromType(SyncDatabase.Other.TYPE);
		assertNotNull(other);
		assertEquals(SyncDatabase.Other.TABLE_NAME, other.name);
	}

	/** The bare table name is not a type - the scope prefix is required. */
	@Test
	public void unscopedNameIsNotAType() {
		assertNull(SyncTestProvider.CONTENT_HELPER.getTableFromType(
				SyncDatabase.Item.TABLE_NAME));
		assertNull(SyncTestProvider.CONTENT_HELPER.getTableFromType(
				"NoSuchScope." + SyncDatabase.Item.TABLE_NAME));
	}

	// ---- isolation ----------------------------------------------------------------------

	@Test
	public void syncingAScopeOnlySendsThatScope() {
		seedBothScopes();

		assertEquals(SyncDatabase.SCOPE, SyncTestProvider.EXECUTOR.requests().get(0).scope);
		assertEquals(SyncDatabase.OTHER_SCOPE, SyncTestProvider.EXECUTOR.requests().get(1).scope);
	}

	@Test
	public void eachScopeKeepsItsOwnBlob() {
		seedBothScopes();

		assertEquals("item-blob", storedBlob(SyncDatabase.SCOPE));
		assertEquals("other-blob", storedBlob(SyncDatabase.OTHER_SCOPE));
	}

	/**
	 * Recovering from "Scope does not exist" drops and rebuilds the scope's tables and
	 * forgets its blob. Everything belonging to another scope has to be left alone.
	 */
	@Test
	public void clearingAScopeLeavesOtherScopesIntact() {
		seedBothScopes();

		SyncTestProvider.EXECUTOR.enqueueError(400, "Scope does not exist");
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("item-blob-fresh", false));
		syncDefaultScope();

		assertEquals("cleared scope's rows are gone", 0, rawCount(SyncDatabase.Item.TABLE_NAME));
		assertEquals("item-blob-fresh", storedBlob(SyncDatabase.SCOPE));

		assertEquals("the other scope's rows must survive", 1,
				rawCount(SyncDatabase.Other.TABLE_NAME));
		assertEquals("the other scope's blob must survive", "other-blob",
				storedBlob(SyncDatabase.OTHER_SCOPE));
	}

	/**
	 * The rebuild goes through createStatement plus executeAfterOnCreate, so the sync
	 * bookkeeping columns have to come back too - without them the table looks fine until
	 * the first write.
	 */
	@Test
	public void clearedScopeTableIsFullyUsableAgain() {
		seedBothScopes();

		SyncTestProvider.EXECUTOR.enqueueError(400, "Scope does not exist");
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("item-blob-fresh", false));
		syncDefaultScope();

		insertItem("after-clear", "still works");

		final SyncState state = syncState(SyncDatabase.Item.TABLE_NAME, "after-clear");
		assertNotNull("row should be insertable after the table was rebuilt", state);
		assertNotNull("tempId column must exist after the rebuild", state.tempId);
		assertTrue(state.isDirty);
	}

	/**
	 * A row whose type names a table this client does not have - a server that has moved on
	 * from the installed schema, which is the normal state of a staged rollout.
	 * <p>
	 * processValue rejects it as a JsonDataException, so it travels the same route as any
	 * other bad payload: the round is rolled back, the failure is counted in SyncResult, and
	 * sync returns rather than throwing at whoever called it.
	 */
	@Test
	public void unknownTypeInResponseIsReportedAsAParseFailure() {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", false,
				SyncJson.row(SyncDatabase.Item.TYPE, ITEM_URI, SyncJson.itemFields("one", "first")),
				SyncJson.row(SyncDatabase.SCOPE + ".TableFromTheFuture",
						"http://server/Future('z')", "\"ID\":\"z\"")));

		assertEquals(1, syncDefaultScope().stats.numParseExceptions);

		assertEquals("the round is rolled back, so nothing is half applied", 0,
				rawCount(SyncDatabase.Item.TABLE_NAME));
		assertNull("no blob is committed for a failed first sync",
				storedBlob(SyncDatabase.SCOPE));
	}

	/** A later sync, once the payload no longer mentions the unknown table, succeeds. */
	@Test
	public void syncRecoversOnceTheUnknownTypeIsGone() {
		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-1", false,
				SyncJson.row(SyncDatabase.SCOPE + ".TableFromTheFuture",
						"http://server/Future('z')", "\"ID\":\"z\"")));
		syncDefaultScope();

		SyncTestProvider.EXECUTOR.enqueue(SyncJson.response("blob-2", false,
				SyncJson.row(SyncDatabase.Item.TYPE, ITEM_URI, SyncJson.itemFields("one", "first"))));
		assertEquals(0, syncDefaultScope().stats.numParseExceptions);

		assertEquals(1, rawCount(SyncDatabase.Item.TABLE_NAME));
		assertEquals("blob-2", storedBlob(SyncDatabase.SCOPE));
	}
}
