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

package pl.selvin.android.autocontentprovider.content;

import android.content.UriMatcher;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Collection;

import pl.selvin.android.autocontentprovider.db.DatabaseInfo;
import pl.selvin.android.autocontentprovider.db.DatabaseInfoFactory;
import pl.selvin.android.autocontentprovider.db.TableInfo;

public class ContentHelper<TTableInfo extends  TableInfo> {
	public static final String PARAMETER_LIMIT = "acp_limit";
	public static final String PARAMETER_SYNC_TO_NETWORK = "apc_sync_to_network";
	public static final int uriClearCode = 0x20000;
	public static final int uriCode = 0xfff;
	public static final int uriCodeItemFlag = 0x1000;
	public static final int uriCodeItemRowIDFlag = 0x2000 | uriCodeItemFlag;
	private static final String DO_CLEAR = "pl_selvin_android_auto_content_provider_do_clear";
	public final String AUTHORITY;
	public final Uri CONTENT_URI;
	public final Uri CLEAR_URI;
	protected final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
	protected final DatabaseInfo<TTableInfo> databaseInfo;
	final int DATABASE_VERSION;
	final String DATABASE_NAME;


	public ContentHelper(Class<?> dbClass, String authority, DatabaseInfoFactory<TTableInfo> databaseInfoFactory, String databaseName, int databaseVersion) {
		DATABASE_VERSION = databaseVersion;
		DATABASE_NAME = databaseName;
		AUTHORITY = authority;
		CONTENT_URI = Uri.parse("content://" + AUTHORITY);
		CLEAR_URI = Uri.withAppendedPath(CONTENT_URI, DO_CLEAR);
		matcher.addURI(AUTHORITY, DO_CLEAR, uriClearCode);
		try {
			databaseInfo = databaseInfoFactory.createDatabaseInfo(dbClass, authority, matcher);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean isItemCode(int code) {
		return (code & ContentHelper.uriCodeItemFlag) == ContentHelper.uriCodeItemFlag;
	}

	public static boolean isItemRowIDCode(int code) {
		return (code & ContentHelper.uriCodeItemRowIDFlag) == ContentHelper.uriCodeItemRowIDFlag;
	}

	public static boolean checkSyncToNetwork(Uri uri) {
		final String syncToNetworkUri = uri.getQueryParameter(ContentHelper.PARAMETER_SYNC_TO_NETWORK);
		return syncToNetworkUri == null || Boolean.parseBoolean(syncToNetworkUri);
	}

	public TableInfo getTableFromCode(int code) {
		return databaseInfo.allTablesInfoCode.get(code);
	}

	@NonNull
	public Uri.Builder getDirUriBuilder(String tableName) {
		return CONTENT_URI.buildUpon().appendPath(tableName);
	}

	@NonNull
	public Uri.Builder getDirUriBuilder(String tableName, boolean syncToNetwork) {
		final Uri.Builder builder = getDirUriBuilder(tableName);
		if (!syncToNetwork)
			builder.appendQueryParameter(PARAMETER_SYNC_TO_NETWORK, Boolean.toString(false));
		return builder;
	}

	@NonNull
	public Uri getDirUri(String tableName) {
		return getDirUriBuilder(tableName).build();
	}

	@NonNull
	public Uri getDirUri(String tableName, boolean syncToNetwork) {
		return getDirUriBuilder(tableName, syncToNetwork).build();
	}

	@NonNull
	public Uri.Builder getItemUriBuilder(String tableName, String... primaryKeys) {
		final Uri.Builder builder = CONTENT_URI.buildUpon();
		builder.appendPath(tableName);
		if (primaryKeys == null || primaryKeys.length == 0) {
			throw new IllegalArgumentException(
					"primary_keys should not be empty nor null");
		} else {
			for (final Object primaryKey : primaryKeys) {
				builder.appendPath(primaryKey.toString());
			}
		}
		return builder;
	}

	@NonNull
	public Uri.Builder getItemUriBuilder(String tableName, boolean syncToNetwork, String... primaryKeys) {
		return getItemUriBuilder(tableName, primaryKeys).appendQueryParameter(PARAMETER_SYNC_TO_NETWORK, Boolean.toString(syncToNetwork));
	}

	@NonNull
	public Uri getItemUri(String tableName, String... primaryKeys) {
		return getItemUriBuilder(tableName, primaryKeys).build();
	}

	@NonNull
	public Uri getItemUri(String tableName, boolean syncToNetwork, String... primaryKeys) {
		return getItemUriBuilder(tableName, syncToNetwork, primaryKeys).build();
	}

	@NonNull
	public Uri.Builder getItemUriBuilder(String tableName, long _id) {
		return CONTENT_URI.buildUpon().appendPath(tableName).appendPath("ROWID").appendPath(Long.toString(_id));
	}

	@NonNull
	public Uri.Builder getItemUriBuilder(String tableName, boolean syncToNetwork, long _id) {
		return getItemUriBuilder(tableName, _id).appendQueryParameter(PARAMETER_SYNC_TO_NETWORK, Boolean.toString(syncToNetwork));
	}

	@NonNull
	public Uri getItemUri(String tableName, long _id) {
		return getItemUriBuilder(tableName, _id).build();
	}

	@NonNull
	public Uri getItemUri(String tableName, boolean syncToNetwork, long _id) {
		return getItemUriBuilder(tableName, syncToNetwork, _id).build();
	}

	public int matchUri(Uri uri) {
		return matcher.match(uri);
	}

	public TTableInfo getTableFromType(String type) {
		return databaseInfo.allTablesInfo.get(type);
	}

	public Collection<TTableInfo> getAllTables() {
		return databaseInfo.allTablesInfo.values();
	}
}