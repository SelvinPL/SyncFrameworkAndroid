/*
  Copyright (c) 2014-2018 Selvin
  Licensed under the Apache License, Version 2.0 (the "License"); you may not
  use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
  by applicable law or agreed to in writing, software distributed under the
  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
  OF ANY KIND, either express or implied. See the License for the specific
  language governing permissions and limitations under the License.
 */

package pl.selvin.android.syncframework.content;

import android.content.UriMatcher;
import android.net.Uri;

import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import pl.selvin.android.autocontentprovider.content.ContentHelper;
import pl.selvin.android.autocontentprovider.db.ColumnInfo;
import pl.selvin.android.autocontentprovider.db.DatabaseInfo;
import pl.selvin.android.autocontentprovider.db.DatabaseInfoFactory;
import pl.selvin.android.autocontentprovider.db.TableInfo;
import pl.selvin.android.autocontentprovider.log.Logger;
import pl.selvin.android.syncframework.annotation.SyncScope;

public class SyncContentHelper extends ContentHelper {
	static final int uriSyncCode = 0x10000;
	// why this name... to ensure that your never call your table like this
	private static final String DO_SYNC = "pl_selvin_android_sync_framework_do_sync";
	private static final String PARAMETER_UN_DELETING = "sf_un_deleting";
	private final static HashMap<Class<?>, SyncContentHelper> instances = new HashMap<>();
	private final SyncDatabaseInfo syncDatabaseInfo;

	public final Uri SYNC_URI;

	private SyncContentHelper(Class<?> dbClass, String authority, String databaseName, int databaseVersion) {
		super(dbClass, authority, new SyncDatabaseInfoFactory(), databaseName, databaseVersion);
		syncDatabaseInfo = (SyncDatabaseInfo) databaseInfo;
		SYNC_URI = Uri.withAppendedPath(CONTENT_URI, DO_SYNC);
		matcher.addURI(AUTHORITY, DO_SYNC, uriSyncCode);
	}

	static boolean checkUnDeleting(Uri uri) {
		final String un_deleting = uri.getQueryParameter(SyncContentHelper.PARAMETER_UN_DELETING);
		return un_deleting != null && Boolean.parseBoolean(un_deleting);
	}

	public static SyncContentHelper getInstance(Class<?> dbClass, String authority, String databaseName, int databaseVersion) {
		if (instances.containsKey(dbClass))
			return instances.get(dbClass);
		final SyncContentHelper ret = new SyncContentHelper(dbClass, authority, databaseName, databaseVersion);
		instances.put(dbClass, ret);
		return ret;
	}

	@SuppressWarnings("unused")
	public void createScopeTables(SupportSQLiteDatabase db, String scope) {
		for (SyncTableInfo tab : Objects.requireNonNull(getTableForScope(scope))) {
			db.execSQL(tab.createStatement());
			tab.executeAfterOnCreate(db);
			tab.createIndexes(db);
		}
	}

	void clearScope(SupportSQLiteDatabase db, String scope) {
		for (SyncTableInfo tab : Objects.requireNonNull(getTableForScope(scope))) {
			db.execSQL(tab.dropStatement());
			db.execSQL(tab.createStatement());
			tab.executeAfterOnCreate(db);
			tab.createIndexes(db);
		}
		db.delete(BlobsTable.NAME, BlobsTable.C_NAME + "=?", new String[]{scope});
	}

	@SuppressWarnings("WeakerAccess")
	public Uri.Builder getDirUriBuilder(String tableName, boolean syncToNetwork, boolean un_deleting) {
		final Uri.Builder builder = getDirUriBuilder(tableName, syncToNetwork);
		if (un_deleting)
			builder.appendQueryParameter(PARAMETER_UN_DELETING, Boolean.toString(true));
		return builder;
	}

	public Uri getDirUri(String tableName, boolean syncToNetwork, boolean un_deleting) {
		return getDirUriBuilder(tableName, syncToNetwork, un_deleting).build();
	}

	public List<SyncTableInfo> getTableForScope(String scope) {
		return syncDatabaseInfo.tablesInScope.get(scope);
	}

	@SuppressWarnings("unused")
	public List<String> getScopes() {
		return new ArrayList<>(syncDatabaseInfo.tablesInScope.keySet());
	}

	boolean hasDirtTable(SupportSQLiteDatabase db, String scope, Logger logger) {
		boolean ret = false;
		for (SyncTableInfo tab : Objects.requireNonNull(syncDatabaseInfo.tablesInScope.get(scope))) {
			ret = tab.hasDirtData(db);
			if (ret)
				break;
		}
		logger.LogD(getClass(), "Scope: '" + scope + "' has" + (ret ? "" : " NO") + " dirty tables");
		return ret;
	}

	static class SyncDatabaseInfo extends DatabaseInfo {

		final Map<String, List<SyncTableInfo>> tablesInScope;

		SyncDatabaseInfo(final Class<?> dbClass, String authority, UriMatcher matcher) throws Exception {
			super(dbClass, authority, matcher, (table, tableClass, authority1) -> {
				final SyncScope syncScope = tableClass.getAnnotation(SyncScope.class);
				if (syncScope == null)
					throw new IllegalStateException("There is no SyncScope annotation for class " + tableClass.getName());
				final String scope = syncScope.value();
				return new SyncTableInfo(table, tableClass, authority1, (column, field) -> {
					final String columnName = (String) field.get(null);
					if (columnName == null)
						throw new IllegalStateException("Column name can not be null!");
					return new ColumnInfo(columnName, column);
				}, scope);
			});
			final HashMap<String, List<SyncTableInfo>> map = new HashMap<>();
			for (TableInfo tabb : allTablesInfo.values()) {
				final SyncTableInfo tab = (SyncTableInfo) tabb;
				final List<SyncTableInfo> list;
				if (!map.containsKey(tab.scope)) {
					list = new ArrayList<>();
					map.put(tab.scope, list);
				} else
					list = map.get(tab.scope);
				Objects.requireNonNull(list).add(tab);
			}

			for (String key : map.keySet()) {
				map.put(key, Collections.unmodifiableList(Objects.requireNonNull(map.get(key))));
			}
			tablesInScope = Collections.unmodifiableMap(map);
		}
	}

	private static class SyncDatabaseInfoFactory implements DatabaseInfoFactory {
		@Override
		public DatabaseInfo createDatabaseInfo(Class<?> dbClass, String authority, UriMatcher matcher) throws Exception {
			return new SyncDatabaseInfo(dbClass, authority, matcher);
		}
	}
}