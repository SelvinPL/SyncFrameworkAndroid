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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;

import pl.selvin.android.syncframework.content.BlobsTable;
import pl.selvin.android.syncframework.content.RequestExecutor;
import pl.selvin.android.syncframework.content.SYNC;

/**
 * Shared plumbing for the sync tests: a clean database, a reset fake executor, and direct
 * access to the sync bookkeeping columns, which are not part of the public projection.
 */
public abstract class SyncTestCase {

	protected Context appContext;
	protected ContentResolver resolver;
	protected SyncTestProvider provider;

	@Before
	public void resetDatabaseAndExecutor() {
		appContext = ApplicationProvider.getApplicationContext();
		resolver = appContext.getContentResolver();
		provider = SyncTestProvider.getRunning(appContext);

		SyncTestProvider.EXECUTOR.reset();

		// straight to the database: deleting through the provider would leave tombstones
		final SupportSQLiteDatabase db = db();
		db.delete(SyncDatabase.Item.TABLE_NAME, null, null);
		db.delete(SyncDatabase.Other.TABLE_NAME, null, null);
		db.delete(BlobsTable.NAME, null, null);
	}

	protected SupportSQLiteDatabase db() {
		return provider.getWritableDatabase();
	}

	protected static Uri dirUri(String tableName) {
		return SyncTestProvider.CONTENT_HELPER.getDirUri(tableName);
	}

	/** Runs a sync for the scope and hands back the SyncResult the provider filled in. */
	protected SyncResult sync(String scope) {
		final SyncResult syncResult = new SyncResult();
		final Bundle parameters = new Bundle();
		parameters.putString(RequestExecutor.SCOPE_PARAMETER, scope);
		parameters.putParcelable(RequestExecutor.SYNC_RESULT_PARAMETER, syncResult);
		provider.sync(parameters);
		return syncResult;
	}

	protected SyncResult syncDefaultScope() {
		return sync(SyncDatabase.SCOPE);
	}

	/** Inserts an Item through the provider, so it gets a tempId and isDirty. */
	protected Uri insertItem(String id, String name) {
		final ContentValues values = new ContentValues();
		values.put(SyncDatabase.Item.ID, id);
		values.put(SyncDatabase.Item.NAME, name);
		final Uri uri = resolver.insert(dirUri(SyncDatabase.Item.TABLE_NAME), values);
		assertNotNull(uri);
		return uri;
	}

	/** Rows visible through the provider - tombstones are filtered out. */
	protected int visibleCount(String tableName) {
		try (Cursor cursor = resolver.query(dirUri(tableName), null, null, null, null)) {
			assertNotNull(cursor);
			return cursor.getCount();
		}
	}

	/** Rows actually in the table, tombstones included. */
	protected int rawCount(String tableName) {
		try (Cursor cursor = db().query("SELECT count(*) FROM " + tableName)) {
			assertTrue(cursor.moveToFirst());
			return cursor.getInt(0);
		}
	}

	/** The sync bookkeeping columns of one row, or null when the row is gone. */
	protected SyncState syncState(String tableName, String id) {
		try (Cursor cursor = db().query("SELECT " + SYNC.uri + ", " + SYNC.tempId + ", "
				+ SYNC.isDeleted + ", " + SYNC.isDirty + " FROM " + tableName
				+ " WHERE ID=?", new Object[]{id})) {
			if (!cursor.moveToFirst())
				return null;
			return new SyncState(cursor.getString(0), cursor.getString(1),
					cursor.getInt(2) == 1, cursor.getInt(3) == 1);
		}
	}

	/** Stored server blob for a scope, or null when none has been saved. */
	protected String storedBlob(String scope) {
		try (Cursor cursor = db().query("SELECT " + BlobsTable.C_VALUE + " FROM "
				+ BlobsTable.NAME + " WHERE " + BlobsTable.C_NAME + "=?", new Object[]{scope})) {
			return cursor.moveToFirst() ? cursor.getString(0) : null;
		}
	}

	/** Blob state column: 0 after a clean commit, -1 when the sync ended in error. */
	protected int storedBlobState(String scope) {
		try (Cursor cursor = db().query("SELECT " + BlobsTable.C_STATE + " FROM "
				+ BlobsTable.NAME + " WHERE " + BlobsTable.C_NAME + "=?", new Object[]{scope})) {
			assertTrue("no blob row for scope " + scope, cursor.moveToFirst());
			return cursor.getInt(0);
		}
	}

	protected String columnValue(String tableName, String id, String column) {
		try (Cursor cursor = db().query("SELECT " + column + " FROM " + tableName
				+ " WHERE ID=?", new Object[]{id})) {
			assertTrue("no row " + id + " in " + tableName, cursor.moveToFirst());
			return cursor.getString(0);
		}
	}

	protected static class SyncState {
		public final String uri;
		public final String tempId;
		public final boolean isDeleted;
		public final boolean isDirty;

		SyncState(String uri, String tempId, boolean isDeleted, boolean isDirty) {
			this.uri = uri;
			this.tempId = tempId;
			this.isDeleted = isDeleted;
			this.isDirty = isDirty;
		}
	}
}
