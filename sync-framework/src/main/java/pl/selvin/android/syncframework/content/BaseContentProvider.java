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
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteQueryBuilder;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Objects;

import okio.BufferedSink;
import okio.BufferedSource;
import pl.selvin.android.autocontentprovider.content.AutoContentProvider;
import pl.selvin.android.autocontentprovider.db.TableInfo;
import pl.selvin.android.autocontentprovider.log.Logger;
import pl.selvin.android.autocontentprovider.utils.SupportSQLiteOpenHelperFactoryProvider;

public abstract class BaseContentProvider extends AutoContentProvider {
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
		final JsonParser jp = jsonFactory.createParser(source.inputStream());
		final Metadata meta = new Metadata();
		JsonToken current;
		String name;
		final HashMap<String, Object> values = new HashMap<>();
		jp.nextToken(); // skip ("START_OBJECT(d) expected");
		jp.nextToken(); // skip ("FIELD_NAME(d) expected");
		if (jp.nextToken() != JsonToken.START_OBJECT)
			throw new JsonParseException(jp, "START_OBJECT(d - object) expected", jp.getCurrentLocation());
		while (jp.nextToken() != JsonToken.END_OBJECT) {
			name = jp.getCurrentName();
			if (SYNC.__sync.equals(name)) {
				jp.nextToken();
				while (jp.nextToken() != JsonToken.END_OBJECT) {
					name = jp.getCurrentName();
					jp.nextToken();
					switch (name) {
						case SYNC.serverBlob:
							parserState.serverBlob = jp.getText();
							break;
						case SYNC.moreChangesAvailable:
							parserState.moreChanges = jp.getBooleanValue() || parserState.forceMoreChanges;
							parserState.forceMoreChanges = false;
							break;
						case SYNC.resolveConflicts:
							parserState.resolveConflicts = jp.getBooleanValue();
							break;
					}
				}
			} else if (SYNC.results.equals(name)) {
				if (jp.nextToken() != JsonToken.START_ARRAY)
					throw new JsonParseException(jp, "START_ARRAY(results) expected", jp.getCurrentLocation());
				while (jp.nextToken() != JsonToken.END_ARRAY) {
					meta.isDeleted = false;
					meta.tempId = null;
					values.clear();
					while (jp.nextToken() != JsonToken.END_OBJECT) {
						name = jp.getCurrentName();
						current = jp.nextToken();
						switch (current) {
							case VALUE_STRING:
								values.put(name, jp.getText());
								break;
							case VALUE_NUMBER_INT:
								values.put(name, jp.getLongValue());
								break;
							case VALUE_NUMBER_FLOAT:
								values.put(name, jp.getDoubleValue());
								break;
							case VALUE_FALSE:
								values.put(name, 0L);
								break;
							case VALUE_TRUE:
								values.put(name, 1L);
								break;
							case VALUE_NULL:
								values.put(name, null);
								break;
							case START_OBJECT:
								switch (name) {
									case SYNC.__metadata:
										while (jp.nextToken() != JsonToken.END_OBJECT) {
											name = jp.getCurrentName();
											jp.nextToken();
											switch (name) {
												case SYNC.uri:
													meta.uri = jp.getText();
													break;
												case SYNC.type:
													meta.type = jp.getText();
													break;
												case SYNC.isDeleted:
													meta.isDeleted = jp
															.getBooleanValue();
													break;
												case SYNC.tempId:
													meta.tempId = jp.getText();
													break;
											}
										}
										break;
									case SYNC.__syncConflict:
										while (jp.nextToken() != JsonToken.END_OBJECT) {
											name = jp.getCurrentName();
											jp.nextToken();
											switch (name) {
												case SYNC.isResolved:
												case SYNC.conflictResolution:
													break;
												case SYNC.conflictingChange:
													while (jp.nextToken() != JsonToken
															.END_OBJECT) {
														name = jp.getCurrentName();
														current = jp.nextToken();
														if (current == JsonToken
																.START_OBJECT) {
															if (SYNC.__metadata.equals(name)) {
																//noinspection StatementWithEmptyBody
																while (jp.nextToken() != JsonToken.END_OBJECT) {
																}
															}
														}
													}
													break;
											}
										}
										// resolve conf
										break;
									case SYNC.__syncError:
										//noinspection StatementWithEmptyBody
										while (jp.nextToken() != JsonToken.END_OBJECT) {
										}
										break;
								}
								break;
							default:
								throw new JsonParseException(jp, "Wrong jsonToken: " + current, jp.getCurrentLocation());
						}

					}
					dataCallback.processValue(meta, values);
				}
			}
		}
		jp.close();
	}

	@SuppressLint("DefaultLocale")
	public Bundle sync(Bundle parameters) {
		final String scope = parameters.getString(RequestExecutor.SCOPE_PARAMETER);
		final SyncResult syncResult = Objects.requireNonNull(parameters.getParcelable(RequestExecutor.SYNC_RESULT_PARAMETER));
		final long start = System.currentTimeMillis();
		final ParserState parserState = new ParserState();
		final SupportSQLiteDatabase database = getWritableDatabase();
		final JsonFactory jsonFactory = new JsonFactory();
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
					final ISyncContentProducer contentProducer;

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
						parse(parserState, result.source, jsonFactory, dataCallback);
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
		} catch (JsonParseException e) {
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

	public interface ISyncContentProducer {
		int getChanges();

		void writeTo(final @NonNull BufferedSink sink) throws IOException;
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

		public void processValue(final Metadata meta, final HashMap<String, Object> values) {
			final SyncTableInfo tableInfo = (SyncTableInfo) syncContentHelper.getTableFromType(meta.type);
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
					contentResolver.notifyChange(dirUri, null, false);
					logger.LogD(clazz, "*Sync* notifyChange table: " + tableInfo.name + ", uri: " + dirUri);
					for (String notifyUri : tableInfo.notifyUris) {
						contentResolver.notifyChange(Uri.parse(notifyUri), null, false);
						logger.LogD(clazz, "\t+ uri: " + notifyUri);
					}
				}
			} else {
				database.endTransaction();
			}
			notifyTableInfo.clear();
		}
	}

	private static class SyncContentProducer implements ISyncContentProducer {
		final SupportSQLiteDatabase database;
		final String scope;
		final String serverBlob;
		final boolean upload;
		final SyncContentHelper syncContentHelper;
		final JsonFactory factory;
		final Logger logger;
		int counter = 0;

		SyncContentProducer(JsonFactory factory, SupportSQLiteDatabase database, String scope,
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
			final JsonGenerator generator = factory.createGenerator(sink.outputStream());
			generator.writeStartObject();
			generator.writeObjectFieldStart(SYNC.d);
			generator.writeObjectFieldStart(SYNC.__sync);
			generator.writeBooleanField(SYNC.moreChangesAvailable, false);
			generator.writeStringField(SYNC.serverBlob, serverBlob);
			generator.writeEndObject(); // sync
			generator.writeArrayFieldStart(SYNC.results);
			if (upload) {
				for (SyncTableInfo tab : syncContentHelper.getTableForScope(scope)) {
					counter += tab.getChanges(database, generator, logger);
				}
			}
			generator.writeEndArray();// result
			generator.writeEndObject(); // d
			generator.writeEndObject();
			generator.close();
		}
	}
}