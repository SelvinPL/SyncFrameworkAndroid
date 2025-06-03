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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Objects;

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

	@SuppressLint("DefaultLocale")
	public Bundle sync(Bundle parameters) {
		final String scope = parameters.getString(RequestExecutor.SCOPE_PARAMETER);
		final SyncResult syncResult = Objects.requireNonNull(parameters.getParcelable(RequestExecutor.SYNC_RESULT_PARAMETER));
		final long start = System.currentTimeMillis();
		boolean hasError = false;
		final SupportSQLiteDatabase db = getWritableDatabase();
		final ArrayList<SyncTableInfo> notifyTableInfo = new ArrayList<>();
		final JsonFactory jsonFactory = new JsonFactory();
		JsonToken current;
		String serverBlob = null;
		final Cursor cur = db.query(
				SupportSQLiteQueryBuilder.builder(BlobsTable.NAME).columns(new String[]{BlobsTable.C_VALUE})
						.selection(BlobsTable.C_NAME + "=?", new Object[]{scope}).create());
		String originalBlob;
		if (cur.moveToFirst()) {
			originalBlob = serverBlob = cur.getString(0);
		} else {
			originalBlob = null;
		}
		cur.close();
		boolean serializationException = false;
		try {
			boolean noChanges = false;
			if (serverBlob != null) {
				noChanges = !syncContentHelper.hasDirtTable(db, scope, logger);
			}
			boolean resolveConflicts = false;
			final Metadata meta = new Metadata();
			final HashMap<String, Object> values = new HashMap<>();
			final ContentValues cv = new ContentValues(2);
			String name;
			boolean moreChanges = false;
			boolean forceMoreChanges = false;
			do {
				RequestExecutor.Result result = null;
				try {
					db.beginTransaction();
					@RequestExecutor.RequestMethod final int requestMethod;
					@RequestExecutor.RequestType final String requestType;
					final ISyncContentProducer contentProducer;

					if (serverBlob != null) {
						requestMethod = RequestExecutor.POST;
						if (noChanges) {
							requestType = RequestExecutor.DOWNLOAD;
						} else {
							requestType = RequestExecutor.UPLOAD;
							forceMoreChanges = true;
						}
						contentProducer = new SyncContentProducer(jsonFactory, db, scope, originalBlob, !noChanges, syncContentHelper, logger);
						noChanges = true;
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
						final JsonParser jp = jsonFactory.createParser(result.inputBuffer);

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
											serverBlob = jp.getText();
											break;
										case SYNC.moreChangesAvailable:
											moreChanges = jp.getBooleanValue() || forceMoreChanges;
											forceMoreChanges = false;
											break;
										case SYNC.resolveConflicts:
											resolveConflicts = jp.getBooleanValue();
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
									final SyncTableInfo tab = (SyncTableInfo) contentHelper.getTableFromType(meta.type);
									if (meta.isDeleted) {
										tab.deleteWithUri(meta.uri, db);
										syncResult.stats.numDeletes++;
									} else {
										if (tab.SyncJSON(values, meta, db))
											syncResult.stats.numUpdates++;
										else syncResult.stats.numInserts++;
									}
									if (!notifyTableInfo.contains(tab)) notifyTableInfo.add(tab);
								}
							}
						}
						jp.close();
						if (resolveConflicts)
							logger.LogE(clazz, "*Sync* has resolve conflicts !!!");
					} else {
						boolean fixed = false;
						final String error = result.error;
						if (error != null) {
							if (error.contains("00-00-00-05-00-00-00-00-00-00-00-01") && !serializationException) {
								//nasty 500: System.Runtime.Serialization.SerializationException ...  using upload instead download with the same serverBlob should help
								logger.LogE(clazz, "*Sync* SerializationException first time - retrying", RuntimeSerializationException.Instance);
								noChanges = false;
								moreChanges = true;
								fixed = true;
								serializationException = true;
							}
							if ((error.contains("Cannot find a valid scope with the name") ||
									error.contains("Scope does not exist"))
									&& !serializationException) {
								//500 Cannot find a valid scope with the name 'table_xxxx-xxxx-guid-xxxxx' in table '[scope_info]'... delete tables in scope and blob then re-sync
								//400 Scope does not exist
								syncContentHelper.clearScope(db, scope);
								serverBlob = null;
								moreChanges = true;
								fixed = true;
								serializationException = true;
							}
						}
						if (!fixed)
							throw new IOException(String.format("Server error: %d, error: %s, blob: %s", result.status, error, originalBlob == null ? "null" : originalBlob));
					}
				} catch (Exception e) {
					hasError = true;
					throw e;
				} finally {
					if (result != null)
						result.close();
					if (!hasError) {
						cv.clear();
						cv.put(BlobsTable.C_NAME, scope);
						cv.put(BlobsTable.C_VALUE, serverBlob);
						cv.put(BlobsTable.C_DATE, Calendar.getInstance().getTimeInMillis());
						cv.put(BlobsTable.C_STATE, 0);
						db.insert(BlobsTable.NAME, SQLiteDatabase.CONFLICT_REPLACE, cv);
						db.setTransactionSuccessful();
						db.endTransaction();
						originalBlob = serverBlob;
						logger.LogD(clazz, "*Sync* commit changes");
						final ContentResolver cr = requireContextEx().getContentResolver();
						for (TableInfo t : notifyTableInfo) {
							final Uri nu = contentHelper.getDirUri(t.name);
							cr.notifyChange(nu, null, false);
							logger.LogD(clazz, "*Sync* notifyChange table: " + t.name + ", uri: " + nu);
							for (String n : t.notifyUris) {
								cr.notifyChange(Uri.parse(n), null, false);
								logger.LogD(clazz, "\t+ uri: " + n);
							}
						}
					} else {
						db.endTransaction();
					}
					notifyTableInfo.clear();
				}
			} while (moreChanges);
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
		if (hasError) {
			ContentValues cv = new ContentValues();
			cv.put(BlobsTable.C_NAME, scope);
			cv.put(BlobsTable.C_VALUE, originalBlob);
			cv.put(BlobsTable.C_DATE, Calendar.getInstance().getTimeInMillis());
			cv.put(BlobsTable.C_STATE, -1);
			db.insert(BlobsTable.NAME, SQLiteDatabase.CONFLICT_REPLACE, cv);
		}
		logger.LogTimeD(clazz, "*Sync* time", start);
		return parameters;
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

		void writeTo(final OutputStream outputStream) throws IOException;
	}

	private static class RuntimeSerializationException extends Exception {
		static final RuntimeSerializationException Instance = new RuntimeSerializationException();
	}

	private static class SyncContentProducer implements ISyncContentProducer {
		final SupportSQLiteDatabase db;
		final String scope;
		final String serverBlob;
		final boolean upload;
		final SyncContentHelper ch;
		final JsonFactory factory;
		final Logger logger;
		int counter = 0;

		SyncContentProducer(JsonFactory factory, SupportSQLiteDatabase db, String scope,
							String serverBlob, boolean upload,
							SyncContentHelper ch, Logger logger) {
			this.factory = factory;
			this.db = db;
			this.scope = scope;
			this.serverBlob = serverBlob;
			this.upload = upload;
			this.ch = ch;
			this.logger = logger;
		}

		public int getChanges() {
			return counter;
		}

		public void writeTo(final OutputStream outputStream) throws IOException {
			final JsonGenerator generator = factory.createGenerator(outputStream);
			generator.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
			generator.writeStartObject();
			generator.writeObjectFieldStart(SYNC.d);
			generator.writeObjectFieldStart(SYNC.__sync);
			generator.writeBooleanField(SYNC.moreChangesAvailable, false);
			generator.writeStringField(SYNC.serverBlob, serverBlob);
			generator.writeEndObject(); // sync
			generator.writeArrayFieldStart(SYNC.results);
			if (upload) {
				for (SyncTableInfo tab : ch.getTableForScope(scope)) {
					counter += tab.getChanges(db, generator, logger);
				}
			}
			generator.writeEndArray();// result
			generator.writeEndObject(); // d
			generator.writeEndObject();
			generator.close();
		}
	}
}