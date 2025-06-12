/*
  Copyright (c) 2014 Selvin
  Licensed under the Apache License, Version 2.0 (the "License"); you may not
  use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
  by applicable law or agreed to in writing, software distributed under the
  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
  OF ANY KIND, either express or implied. See the License for the specific
  language governing permissions and limitations under the License.
 */

package pl.selvin.android.syncframework.content;

import android.accounts.AuthenticatorException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.core.os.BundleCompat;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteQueryBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Objects;

import okio.BufferedSink;
import okio.BufferedSource;
import pl.selvin.android.autocontentprovider.content.AutoContentProvider;
import pl.selvin.android.autocontentprovider.db.TableInfo;
import pl.selvin.android.autocontentprovider.log.Logger;
import pl.selvin.android.autocontentprovider.utils.SupportSQLiteOpenHelperFactoryProvider;
import pl.selvin.android.syncframework.json.JsonDataException;
import pl.selvin.android.syncframework.json.JsonEncodingException;
import pl.selvin.android.syncframework.json.JsonFactory;
import pl.selvin.android.syncframework.json.JsonReader;
import pl.selvin.android.syncframework.json.JsonWriter;
import pl.selvin.android.syncframework.json.moshi.MoshiJsonFactory;

public abstract class BaseContentProvider extends AutoContentProvider<SyncTableInfo> {
	public final static String DATABASE_OPERATION_TYPE_UPGRADE = "DATABASE_OPERATION_TYPE_UPGRADE";
	public final static String DATABASE_OPERATION_TYPE_CREATE = "DATABASE_OPERATION_TYPE_CREATE";
	public final static String ACTION_SYNC_FRAMEWORK_DATABASE = "ACTION_SYNC_FRAMEWORK_DATABASE";
	private final static String DATABASE_OPERATION_TYPE = "DATABASE_OPERATION_TYPE";
	protected final RequestExecutor executor;
	private final SyncContentHelper syncContentHelper;


	public BaseContentProvider(SyncContentHelper contentHelper, Logger logger, SupportSQLiteOpenHelperFactoryProvider supportSQLiteOpenHelperFactoryProvider, RequestExecutor executor) {
		super(contentHelper, logger, supportSQLiteOpenHelperFactoryProvider);
		syncContentHelper = contentHelper;
		this.executor = executor;
	}

	@Override
	public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
		if (contentHelper.matchUri(uri) == SyncContentHelper.uriSyncCode) {
			throw new IllegalArgumentException("Can not delete with Sync Uri.");
		}
		return super.delete(uri, selection, selectionArgs);
	}

	@Override
	public String getType(@NonNull Uri uri) {
		if (contentHelper.matchUri(uri) == SyncContentHelper.uriSyncCode) {
			throw new IllegalArgumentException("There is no type for SYNC Uri: " + uri);
		}
		return super.getType(uri);
	}

	@Override
	public Uri insert(@NonNull Uri uri, ContentValues values) {
		if (contentHelper.matchUri(uri) == SyncContentHelper.uriSyncCode) {
			throw new IllegalArgumentException("Can not insert with Sync Uri.");
		}
		return super.insert(uri, values);
	}

	@Override
	public Bundle call(@NonNull String method, String arg, Bundle extras) {
		final Uri uri = Uri.parse(method);
		if (contentHelper.matchUri(uri) == SyncContentHelper.uriSyncCode) {
			return sync(extras);
		}
		return super.call(method, arg, extras);
	}

	private void parse(ParserState parserState, BufferedSource source, JsonFactory jsonFactory, DataCallback dataCallback) throws IOException {
		final JsonReader jsonParser = jsonFactory.createReader(source);
		final Metadata meta = new Metadata();
		int current;
		String name;
		final ArrayMap<String, Object> values = new ArrayMap<>();
		jsonParser.beginObject();
		if (!jsonParser.nextName().equals(SYNC.d))
			throw new JsonDataException("d expected!");
		jsonParser.beginObject();
		while (jsonParser.hasNext()) {
			name = jsonParser.nextName();
			if (SYNC.__sync.equals(name)) {
				jsonParser.beginObject();
				while (jsonParser.hasNext()) {
					name = jsonParser.nextName();
					switch (name) {
						case SYNC.serverBlob:
							parserState.serverBlob = jsonParser.nextString();
							break;
						case SYNC.moreChangesAvailable:
							parserState.moreChanges = jsonParser.nextBoolean() || parserState.forceMoreChanges;
							parserState.forceMoreChanges = false;
							break;
						case SYNC.resolveConflicts:
							parserState.resolveConflicts = jsonParser.nextBoolean();
							break;
					}
				}
				jsonParser.endObject();
			} else if (SYNC.results.equals(name)) {
				jsonParser.beginArray();
				while (jsonParser.hasNext()) {
					meta.isDeleted = false;
					meta.tempId = null;
					values.clear();
					jsonParser.beginObject();
					while (jsonParser.hasNext()) {
						name = jsonParser.nextName();
						current = jsonParser.peek();
						switch (current) {
							case JsonReader.STRING:
								values.put(name, jsonParser.nextString());
								break;
							case JsonReader.NUMBER_INTEGER:
								values.put(name, jsonParser.nextLong());
								break;
							case JsonReader.NUMBER_REAL:
								values.put(name, jsonParser.nextDouble());
								break;
							case JsonReader.BOOLEAN:
								values.put(name, jsonParser.nextBoolean() ? 1L : 0L);
								break;
							case JsonReader.NULL:
								values.put(name, jsonParser.nextNull());
								break;
							case JsonReader.BEGIN_OBJECT:
								switch (name) {
									case SYNC.__metadata:
										jsonParser.beginObject();
										while (jsonParser.hasNext()) {
											name = jsonParser.nextName();
											switch (name) {
												case SYNC.uri:
													meta.uri = jsonParser.nextString();
													break;
												case SYNC.type:
													meta.type = jsonParser.nextString();
													break;
												case SYNC.isDeleted:
													meta.isDeleted = jsonParser.nextBoolean();
													break;
												case SYNC.tempId:
													meta.tempId = jsonParser.nextString();
													break;
											}
										}
										jsonParser.endObject();
										break;
									case SYNC.__syncConflict:
										jsonParser.beginObject();
										while (jsonParser.hasNext()) {
											name = jsonParser.nextName();
											switch (name) {
												case SYNC.isResolved:
												case SYNC.conflictResolution:
												case SYNC.conflictingChange:
													//TODO: proper conflict resolution
													jsonParser.skipValue();
													break;
											}
										}
										jsonParser.endObject();
										break;
									case SYNC.__syncError:
										jsonParser.skipValue();
										break;
								}
								break;
							default:
								throw new JsonDataException("Wrong jsonToken");
						}

					}
					jsonParser.endObject();
					dataCallback.processValue(meta, values);
				}
				jsonParser.endArray();
			}
		}
		jsonParser.endObject();
		jsonParser.close();
	}

	public Bundle sync(Bundle parameters) {
		final String scope = parameters.getString(RequestExecutor.SCOPE_PARAMETER);
		final SyncResult syncResult = Objects.requireNonNull(BundleCompat.getParcelable(parameters, RequestExecutor.SYNC_RESULT_PARAMETER, SyncResult.class));
		final long start = System.currentTimeMillis();
		final ParserState parserState = new ParserState();
		final SupportSQLiteDatabase database = getWritableDatabase();
		final JsonFactory jsonFactory = new MoshiJsonFactory();
		final Cursor blobCursor = database.query(
				SupportSQLiteQueryBuilder.builder(BlobsTable.NAME).columns(new String[]{BlobsTable.C_VALUE})
						.selection(BlobsTable.C_NAME + "=?", new Object[]{scope}).create());
		if (blobCursor.moveToFirst()) {
			parserState.originalBlob = parserState.serverBlob = blobCursor.getString(0);
		} else {
			parserState.originalBlob = null;
		}
		blobCursor.close();
		final DataCallback dataCallback = new DataCallback(syncContentHelper, database, syncResult);
		try {
			if (parserState.serverBlob != null) {
				parserState.noChanges = !syncContentHelper.hasDirtTable(database, scope, logger);
			}
			do {
				RequestExecutor.Result result = null;
				try {
					database.beginTransaction();
					@RequestExecutor.RequestMethod final int requestMethod;
					@RequestExecutor.RequestType final String requestType;
					final SyncContentProducer contentProducer;

					if (parserState.serverBlob != null) {
						requestMethod = RequestExecutor.POST;
						if (parserState.noChanges) {
							requestType = RequestExecutor.DOWNLOAD;
						} else {
							requestType = RequestExecutor.UPLOAD;
							parserState.forceMoreChanges = true;
						}
						contentProducer = new SyncContentProducer(jsonFactory, database, scope, parserState.originalBlob, !parserState.noChanges, syncContentHelper, logger);
						parserState.noChanges = true;
					} else {
						requestMethod = RequestExecutor.GET;
						requestType = RequestExecutor.DOWNLOAD;
						contentProducer = null;
					}
					parameters.putInt(RequestExecutor.REQUEST_METHOD_PARAMETER, requestMethod);
					parameters.putString(RequestExecutor.REQUEST_TYPE_PARAMETER, requestType);
					result = executor.execute(requireContextEx(), contentProducer, parameters);
					if (result.status == 200) {
						if (contentProducer != null)
							syncResult.stats.numEntries += contentProducer.getChanges();
						parse(parserState, result.source, new MoshiJsonFactory(), dataCallback);
						if (parserState.resolveConflicts) {
							logger.LogE(clazz, "*Sync* has resolve conflicts !!!");
						}
					} else {
						httpBadStatusHandling(result, parserState, database, scope);
					}
				} catch (Exception e) {
					parserState.hasError = true;
					throw e;
				} finally {
					if (result != null)
						result.closeQuietly();
					dataCallback.commitOrRollback(requireContextEx(), scope, parserState, logger, clazz);
				}
			} while (parserState.moreChanges);
		} catch (JsonDataException | JsonEncodingException e) {
			syncResult.stats.numParseExceptions++;
			logger.LogE(clazz, e);
		} catch (IOException e) {
			syncResult.stats.numIoExceptions++;
			logger.LogE(clazz, e);
		} catch (AuthenticatorException e) {
			syncResult.stats.numAuthExceptions++;
			logger.LogE(clazz, e);
		}
		if (parserState.hasError) {
			final ContentValues contentValues = new ContentValues();
			contentValues.put(BlobsTable.C_NAME, scope);
			contentValues.put(BlobsTable.C_VALUE, parserState.originalBlob);
			contentValues.put(BlobsTable.C_DATE, Calendar.getInstance().getTimeInMillis());
			contentValues.put(BlobsTable.C_STATE, -1);
			database.insert(BlobsTable.NAME, SQLiteDatabase.CONFLICT_REPLACE, contentValues);
		}
		logger.LogTimeD(clazz, "*Sync* time", start);
		return parameters;
	}

	private void httpBadStatusHandling(final RequestExecutor.Result result, final ParserState parserState, final SupportSQLiteDatabase database, final String scope) throws IOException {
		boolean fixed = false;
		final String error = result.error;
		if (error != null) {
			if (error.contains("00-00-00-05-00-00-00-00-00-00-00-01") && !parserState.serializationException) {
				//nasty 500: System.Runtime.Serialization.SerializationException ...  using upload instead download with the same serverBlob should help
				logger.LogE(clazz, "*Sync* SerializationException first time - retrying", RuntimeSerializationException.Instance);
				parserState.noChanges = false;
				parserState.moreChanges = true;
				fixed = true;
				parserState.serializationException = true;
			}
			if ((error.contains("Cannot find a valid scope with the name") ||
					error.contains("Scope does not exist"))
					&& !parserState.serializationException) {
				//500 Cannot find a valid scope with the name 'table_xxxx-xxxx-guid-xxxxx' in table '[scope_info]'... delete tables in scope and blob then re-sync
				//400 Scope does not exist
				syncContentHelper.clearScope(database, scope);
				parserState.serverBlob = null;
				parserState.moreChanges = true;
				fixed = true;
				parserState.serializationException = true;
			}
		}
		if (!fixed) {
			throw new IOException(String.format("Server error: %d, error: %s, blob: %s", result.status, error, parserState.originalBlob == null ? "null" : parserState.originalBlob));
		}
	}

	protected void onCreateDatabase(SupportSQLiteDatabase db) {
		final Intent intent = new Intent(ACTION_SYNC_FRAMEWORK_DATABASE);
		intent.putExtra(DATABASE_OPERATION_TYPE, DATABASE_OPERATION_TYPE_CREATE);
		requireContextEx().sendBroadcast(intent);
		try {
			super.onCreateDatabase(db);
			db.execSQL(String.format(
					"CREATE TABLE [%s] ([%s] VARCHAR NOT NULL, [%s] VARCHAR, " + "[%s] LONG NOT " +
							"NULL, [%s] INT NOT NULL, PRIMARY KEY([%s]))",
					BlobsTable.NAME, BlobsTable.C_NAME, BlobsTable.C_VALUE, BlobsTable.C_DATE,
					BlobsTable.C_STATE, BlobsTable.C_NAME));
		} catch (Exception e) {
			logger.LogE(clazz, "*onCreateDataBase*: " + e, e);
		}
	}

	protected void onUpgradeDatabase(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
		final Intent intent = new Intent(ACTION_SYNC_FRAMEWORK_DATABASE);
		intent.putExtra(DATABASE_OPERATION_TYPE, DATABASE_OPERATION_TYPE_UPGRADE);
		requireContextEx().sendBroadcast(intent);
		super.onUpgradeDatabase(db, oldVersion, newVersion);
	}

	private static class ParserState {
		public boolean hasError;
		public String serverBlob;
		public boolean serializationException;
		public boolean moreChanges;
		public boolean forceMoreChanges;
		public boolean noChanges;
		public boolean resolveConflicts;
		public String originalBlob;
	}

	private static class RuntimeSerializationException extends Exception {
		static final RuntimeSerializationException Instance = new RuntimeSerializationException();
	}

	private static class DataCallback {
		private final SyncContentHelper syncContentHelper;
		private final SupportSQLiteDatabase database;
		private final ArrayList<SyncTableInfo> notifyTableInfo = new ArrayList<>();
		private final SyncResult syncResult;
		private final ContentValues contentValues = new ContentValues();

		public DataCallback(SyncContentHelper syncContentHelper, SupportSQLiteDatabase database, SyncResult syncResult) {
			this.syncContentHelper = syncContentHelper;
			this.database = database;
			this.syncResult = syncResult;
		}

		public void processValue(final Metadata meta, final ArrayMap<String, Object> values) {
			final SyncTableInfo tableInfo = syncContentHelper.getTableFromType(meta.type);
			if (meta.isDeleted) {
				tableInfo.deleteWithUri(meta.uri, database);
				syncResult.stats.numDeletes++;
			} else {
				if (tableInfo.SyncJSON(values, meta, database))
					syncResult.stats.numUpdates++;
				else syncResult.stats.numInserts++;
			}
			if (!notifyTableInfo.contains(tableInfo)) {
				notifyTableInfo.add(tableInfo);
			}
		}

		public void commitOrRollback(@NonNull Context context, final String scope, ParserState parserState, Logger logger, Class<?> clazz) {
			if (!parserState.hasError) {
				contentValues.clear();
				contentValues.put(BlobsTable.C_NAME, scope);
				contentValues.put(BlobsTable.C_VALUE, parserState.serverBlob);
				contentValues.put(BlobsTable.C_DATE, Calendar.getInstance().getTimeInMillis());
				contentValues.put(BlobsTable.C_STATE, 0);
				database.insert(BlobsTable.NAME, SQLiteDatabase.CONFLICT_REPLACE, contentValues);
				database.setTransactionSuccessful();
				database.endTransaction();
				parserState.originalBlob = parserState.serverBlob;
				logger.LogD(clazz, "*Sync* commit changes");
				final ContentResolver contentResolver = context.getContentResolver();
				for (TableInfo tableInfo : notifyTableInfo) {
					final Uri dirUri = syncContentHelper.getDirUri(tableInfo.name);
					notifyChange(contentResolver, dirUri, null, false);
					logger.LogD(clazz, "*Sync* notifyChange table: " + tableInfo.name + ", uri: " + dirUri);
					for (String notifyUri : tableInfo.notifyUris) {
						notifyChange(contentResolver, Uri.parse(notifyUri), null, false);
						logger.LogD(clazz, "\t+ uri: " + notifyUri);
					}
				}
			} else {
				database.endTransaction();
			}
			//ok
			notifyTableInfo.clear();
		}
	}

	public static class SyncContentProducer {
		final SupportSQLiteDatabase database;
		final String scope;
		final String serverBlob;
		final boolean upload;
		final SyncContentHelper syncContentHelper;
		final JsonFactory factory;
		final Logger logger;
		int counter = 0;

		private SyncContentProducer(JsonFactory factory, SupportSQLiteDatabase database, String scope,
									String serverBlob, boolean upload,
									SyncContentHelper syncContentHelper, Logger logger) {
			this.factory = factory;
			this.database = database;
			this.scope = scope;
			this.serverBlob = serverBlob;
			this.upload = upload;
			this.syncContentHelper = syncContentHelper;
			this.logger = logger;
		}

		public int getChanges() {
			return counter;
		}

		public void writeTo(final @NonNull BufferedSink sink) throws IOException {
			final JsonWriter jsonWriter = factory.createWriter(sink);
			jsonWriter.beginObject();
			jsonWriter.name(SYNC.d).beginObject();
			jsonWriter.name(SYNC.__sync).beginObject()
					.name(SYNC.moreChangesAvailable).value(false)
					.name(SYNC.serverBlob).value(serverBlob)
					.endObject();
			jsonWriter.name(SYNC.results).beginArray();
			if (upload) {
				for (SyncTableInfo tab : syncContentHelper.getTableForScope(scope)) {
					counter += tab.getChanges(database, jsonWriter, logger);
				}
			}
			jsonWriter
					.endArray()// result
					.endObject() // d
					.endObject();
			jsonWriter.close();
		}
	}
}