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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Base64;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQueryBuilder;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import pl.selvin.android.autocontentprovider.annotation.Table;
import pl.selvin.android.autocontentprovider.db.ColumnInfo;
import pl.selvin.android.autocontentprovider.db.ColumnInfoFactory;
import pl.selvin.android.autocontentprovider.db.ColumnType;
import pl.selvin.android.autocontentprovider.db.TableInfo;
import pl.selvin.android.autocontentprovider.log.Logger;
import pl.selvin.android.autocontentprovider.utils.DatabaseUtilsCompat;

public class SyncTableInfo extends TableInfo {

	private final static String ms_date = "\"/Date(%s)/\"";
	private final static SimpleDateFormat sdf = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss", Locale.US);

	static {
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		// comment this line if you're store date in SQL in UTC format
	}

	final String scope;
	private final ContentValues values = new ContentValues(2);

	SyncTableInfo(Table table, Class<?> clazz, String authority, ColumnInfoFactory columnInfoFactory, String scope) throws Exception {
		super(table, clazz, authority, scope + ".%s", columnInfoFactory);
		this.scope = scope;
	}

	boolean hasDirtData(SupportSQLiteDatabase db) {
		Cursor c = db.query("SELECT 1 FROM " + name + " WHERE " + SYNC.isDirty + "=1");
		if (c.moveToFirst()) {
			c.close();
			return true;
		}
		c.close();
		return false;
	}

	int getChanges(final SupportSQLiteDatabase db, final JsonGenerator generator, Logger logger) throws IOException {
		String[] cols = new String[columns.size() + 3];
		int i = 0;
		for (; i < columns.size(); i++)
			cols[i] = columns.get(i).name;
		cols[i] = SYNC.uri;
		cols[i + 1] = SYNC.tempId;
		cols[i + 2] = SYNC.isDeleted;
		SupportSQLiteQuery q = SupportSQLiteQueryBuilder.builder(name).columns(cols).selection(SYNC.isDirty + "=1", null).create();
		Cursor c = db.query(q);
		int counter = 0;
		//to fix startPos  > actual rows for large cursors db operations should be done after
		// cursor is closed ...
		final ArrayList<OperationHolder> operations = new ArrayList<>();
		if (c.moveToFirst()) {
			do {
				counter++;
				generator.writeStartObject();
				generator.writeObjectFieldStart(SYNC.__metadata);
				generator.writeBooleanField(SYNC.isDirty, true);
				generator.writeStringField(SYNC.type, nameForMime);
				final String uri = c.getString(i);
				if (uri == null) {
					generator.writeStringField(SYNC.tempId, c.getString(i + 1));
				} else {
					generator.writeStringField(SYNC.uri, uri);
					final ContentValues update = new ContentValues(1);
					update.put(SYNC.isDirty, 0);
					operations.add(new OperationHolder(name, OperationHolder.UPDATE, update, uri));
				}
				boolean isDeleted = c.getInt(i + 2) == 1;
				if (isDeleted) {
					generator.writeBooleanField(SYNC.isDeleted, true);
					generator.writeEndObject();// meta
					operations.add(new OperationHolder(name, OperationHolder.DELETE, null, uri));
				} else {
					generator.writeEndObject();// meta
					for (i = 0; i < columns.size(); i++) {
						final ColumnInfo column = columns.get(i);
						if (column.nullable && c.isNull(i)) {
							generator.writeNullField(column.name);
						} else {
							switch (column.type) {
								case ColumnType.BLOB:
									generator.writeBinaryField(column.name, c.getBlob(i));
									break;
								case ColumnType.BOOLEAN:
									generator.writeBooleanField(column.name, c.getLong(i) == 1);
									break;
								case ColumnType.INTEGER:
									generator.writeNumberField(column.name, c.getLong(i));
									break;
								case ColumnType.DATETIME:
									try {
										final Date date = sdf.parse(c.getString(i));
										if (date == null)
											throw new IllegalStateException("Parsed date is null!");
										generator.writeStringField(column.name, String.format(ms_date, date.getTime()));
									} catch (IllegalStateException ie) {
										throw ie;
									} catch (Exception e) {
										throw new RuntimeException(e);
										//logger.LogE(clazz, e);
									}
									break;
								case ColumnType.NUMERIC:
									generator.writeNumberField(column.name, c.getDouble(i));
									break;
								case ColumnType.DECIMAL:
									generator.writeNumberField(column.name, BigDecimal.valueOf(c.getDouble(i)).setScale(column.precision, RoundingMode.HALF_UP));
									break;
								default:
									generator.writeStringField(column.name, c.getString(i));
									break;
							}
						}
					}
				}
				generator.writeEndObject(); // end of row
			} while (c.moveToNext());
		}
		c.close();
		logger.LogD(clazz, "Table: '" + name + "', changes: " + counter);
		for (OperationHolder operation : operations)
			operation.execute(db);
		return counter;
	}

	public void executeAfterOnCreate(SupportSQLiteDatabase database) {
		database.execSQL("ALTER TABLE " + name + " ADD COLUMN [" + SYNC.uri + "] VARCHAR");
		database.execSQL("ALTER TABLE " + name + " ADD COLUMN [" + SYNC.tempId + "] GUID");
		database.execSQL("ALTER TABLE " + name + " ADD COLUMN [" + SYNC.isDeleted + "] INTEGER NOT NULL DEFAULT (0)");
		database.execSQL("ALTER TABLE " + name + " ADD COLUMN [" + SYNC.isDirty + "] INTEGER NOT NULL DEFAULT (0)");
		database.execSQL("CREATE INDEX IX_" + name + "_SYNC_" + SYNC.tempId + " ON " + name + "(" + SYNC.tempId + " ASC)");
		database.execSQL("CREATE INDEX IX_" + name + "_SYNC_" + SYNC.uri + " ON " + name + "(" + SYNC.uri + " ASC)");
	}

	final void deleteWithUri(String uri, SupportSQLiteDatabase db) {
		db.delete(name, SYNC.uriP, new String[]{uri});
	}

	final boolean SyncJSON(final HashMap<String, Object> map, final Metadata meta, final SupportSQLiteDatabase db) {
		int i = 0;
		values.clear();
		for (; i < columns.size(); i++) {
			final ColumnInfo columnInfo = columns.get(i);
			final String column = columnInfo.name;
			final Object obj = map.get(column);
			if (obj == null) {
				values.putNull(column);
			} else {
				switch (columnInfo.type) {
					case ColumnType.BLOB:
						values.put(column, Base64.decode((String) obj, Base64.DEFAULT));
						break;
					case ColumnType.BOOLEAN:
					case ColumnType.INTEGER:
					case ColumnType.NUMERIC:
					case ColumnType.DECIMAL:
						if (obj instanceof Double)
							values.put(column, (Double) obj);
						else
							values.put(column, (Long) obj);
						break;
					case ColumnType.DATETIME:
						final String date = (String) obj;
						values.put(column, sdf.format(new Date(Long.parseLong(date.substring(6, date.length() - 2)))));
						break;
					default:
						values.put(column, (String) obj);
						break;
				}
			}
		}
		values.put(SYNC.uri, meta.uri);
		values.putNull(SYNC.tempId);
		values.put(SYNC.isDirty, 0);
		if (meta.tempId != null) {
			db.update(name, SQLiteDatabase.CONFLICT_FAIL, values, SYNC.tempIdP, new String[]{meta.tempId});
			return true;
		} else {
			db.insert(name, SQLiteDatabase.CONFLICT_REPLACE, values);
		}
		return false;
	}

	@Override
	public Cursor query(SupportSQLiteDatabase database, Uri uri, SQLiteQueryBuilder builder, String[] projection, String selection, String[] selectionArgs, String groupBy, String having, String sortOrder, String limit, Logger logger) {
		selection = DatabaseUtilsCompat.concatenateWhere(selection, SYNC.isDeleted + "=0");
		return super.query(database, uri, builder, projection, selection, selectionArgs, groupBy, having, sortOrder, limit, logger);
	}

	@Override
	public long insert(SupportSQLiteDatabase database, Uri uri, ContentValues values, Logger logger) {
		logger.LogD(clazz, "*insert* " + uri);
		values.put("tempId", UUID.randomUUID().toString());
		values.put("isDirty", 1);
		values.put("isDeleted", 0);
		final long rowId;
		if (!SyncContentHelper.checkUnDeleting(uri)) {
			rowId = database.insert(name, SQLiteDatabase.CONFLICT_FAIL, values);
		} else {
			rowId = database.insert(name, SQLiteDatabase.CONFLICT_REPLACE, values);
		}
		logger.LogD(clazz, "rowId:" + rowId + ", values: " + values);
		return rowId;
	}

	@Override
	public int delete(SupportSQLiteDatabase database, Uri uri, String selection, String[] selectionArgs, Logger logger) {
		logger.LogD(clazz, "*delete* " + uri);
		selection = DatabaseUtilsCompat.concatenateWhere(selection, SYNC.isDeleted + "=0");
		final ContentValues values = new ContentValues(2);
		values.put("isDirty", 1);
		values.put("isDeleted", 1);
		final String updateSelection = DatabaseUtilsCompat.concatenateWhere("tempId IS NULL", selection);
		int ret = database.update(name, SQLiteDatabase.CONFLICT_FAIL, values, updateSelection, selectionArgs);
		logger.LogD(clazz, "ret:" + ret + " -upd: selectionArgs: " + Arrays.toString(selectionArgs) + "selection: " + updateSelection + " values: " + values);
		final String deleteSelection = DatabaseUtilsCompat.concatenateWhere("tempId IS NOT NULL", selection);
		ret += database.delete(name, deleteSelection, selectionArgs);
		logger.LogD(clazz, "ret:" + ret + " -del: selectionArgs: " + Arrays.toString(selectionArgs) + "selection: " + deleteSelection);
		return ret;
	}

	@Override
	public int update(SupportSQLiteDatabase database, Uri uri, ContentValues values, String selection, String[] selectionArgs, Logger logger) {
		selection = DatabaseUtilsCompat.concatenateWhere(selection, SYNC.isDeleted + "=0");
		values.put("isDirty", 1);
		return super.update(database, uri, values, selection, selectionArgs, logger);
	}

	static class OperationHolder {
		final static int DELETE = 0;
		final static int UPDATE = 1;
		final static int INSERT = 2;
		final String table;
		final int operation;
		final ContentValues values;
		final String uri;

		OperationHolder(String table, int operation, ContentValues values, String uri) {
			this.table = table;
			this.operation = operation;
			this.values = values;
			this.uri = uri;
		}

		void execute(SupportSQLiteDatabase db) {
			switch (operation) {
				case DELETE:
					db.delete(table, SYNC.uriP, new String[]{uri});
					break;
				case UPDATE:
					db.update(table, SQLiteDatabase.CONFLICT_FAIL, values, SYNC.uriP, new String[]{uri});
					break;
				case INSERT:
					db.insert(table, SQLiteDatabase.CONFLICT_FAIL, values);
					break;
			}
		}
	}
}