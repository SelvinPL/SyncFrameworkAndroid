/*
 Copyright (c) 2017-2018 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */

package pl.selvin.android.autocontentprovider.db;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

import androidx.sqlite.db.SupportSQLiteDatabase;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pl.selvin.android.autocontentprovider.annotation.Cascade;
import pl.selvin.android.autocontentprovider.annotation.Column;
import pl.selvin.android.autocontentprovider.annotation.Index;
import pl.selvin.android.autocontentprovider.annotation.Table;
import pl.selvin.android.autocontentprovider.annotation.TableName;
import pl.selvin.android.autocontentprovider.log.Logger;

public class TableInfo {

	public final Map<String, String> map = new HashMap<>();
	public final String name;
	public final List<ColumnInfo> primaryKeys;
	public final String dirMime;
	public final String itemMime;
	public final boolean readonly;
	public final String[] notifyUris;
	public final String rowIdAlias;
	public final List<ColumnInfo> columns;
	public final List<ColumnInfo> computedColumns;
	public final CascadeInfo[] cascadeDelete;
	public final List<IndexInfo> indexes;
	protected final String nameForMime;
	protected final Class<?> clazz;
	private final String[] primaryKeyStrings;
	private String selection = null;

	public TableInfo(Table table, Class<?> clazz, final String authority, final String nameForMimeFormat, ColumnInfoFactory columnInfoFactory) throws Exception {
		String tableName = null;
		this.clazz = clazz;
		final ArrayList<ColumnInfo> columns = new ArrayList<>();
		final ArrayList<ColumnInfo> computedColumns = new ArrayList<>();
		final Field[] fields = clazz.getDeclaredFields();
		final ArrayList<ColumnInfo> primaryKeys = new ArrayList<>();
		final HashMap<String, ColumnInfo> namesToColumns = new HashMap<>();
		for (final Field field : fields) {
			final Column column = field.getAnnotation(Column.class);
			if (column != null) {
				final ColumnInfo columnToAdd;
				columnToAdd = columnInfoFactory.createColumnInfo(column, field);
				if (column.computed().equals(Column.EMPTY)) {
					columns.add(columnToAdd);
				} else
					computedColumns.add(columnToAdd);
				namesToColumns.put(columnToAdd.name, columnToAdd);
			} else if (field.getAnnotation(TableName.class) != null) {
				try {
					if (tableName != null)
						throw new RuntimeException("Only one field should be annotated with @TableName");
					tableName = (String) field.get(null);
				} catch (Exception ex) {
					throw new RuntimeException(ex);
				}
			}
		}
		if (columns.size() == 0)
			throw new RuntimeException("Table has no columns");
		if (tableName == null)
			throw new RuntimeException("There is no field with @TableName annotation");
		this.name = tableName;
		final Cascade[] delete = table.delete();
		cascadeDelete = new CascadeInfo[delete.length];
		for (int i = 0; i < delete.length; i++) {
			cascadeDelete[i] = new CascadeInfo(delete[i]);
		}
		this.rowIdAlias = table.rowIdAlias();
		readonly = table.readonly();
		map.put("_id", "[" + name + "]." + rowIdAlias + " AS _id");
		for (ColumnInfo ci : columns) {
			map.put(ci.name, ci.name);
		}
		for (ColumnInfo ci : computedColumns) {
			map.put(ci.name, String.format("%s AS %s", ci.computed, ci.name));
		}
		this.primaryKeyStrings = table.primaryKeys();
		for (String primaryKeyString : primaryKeyStrings) {
			primaryKeys.add(namesToColumns.get(primaryKeyString));
		}
		this.columns = Collections.unmodifiableList(columns);
		this.computedColumns = Collections.unmodifiableList(computedColumns);
		this.primaryKeys = Collections.unmodifiableList(primaryKeys);

		this.indexes = new ArrayList<>();
		for (Index index : table.indexes()) {
			indexes.add(new IndexInfo(name, index));
		}
		this.nameForMime = String.format(nameForMimeFormat, name);
		dirMime = String.format("%s/%s.%s", ContentResolver.CURSOR_DIR_BASE_TYPE, authority, nameForMime);
		itemMime = String.format("%s/%s.%s", ContentResolver.CURSOR_ITEM_BASE_TYPE, authority, nameForMime);
		notifyUris = table.notifyUris();
	}

	public final String getSelection() {
		if (selection == null) {
			StringBuilder builder = null;
			for (String primaryKey : primaryKeyStrings) {
				if (builder == null) {
					builder = new StringBuilder(primaryKey);
				} else {
					builder.append(" AND ");
					builder.append(primaryKey);
				}
				builder.append("=?");
			}
			selection = builder != null ? builder.toString() : "";
		}
		return selection;
	}

	public String dropStatement() {
		return "DROP TABLE IF EXISTS " + name;
	}

	public String createStatement() {
		StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
		sb.append(name);
		sb.append(" (");
		boolean first = true;
		for (final ColumnInfo column : columns) {
			if (!first)
				sb.append(", ");
			else
				first = false;
			sb.append("[");
			sb.append(column.name);
			sb.append("] ");
			sb.append(ColumnType.getName(column.type));
			sb.append(' ');
			sb.append(column.extras);
			if (!column.nullable)
				sb.append(" NOT NULL ");
		}
		sb.append(", PRIMARY KEY (");
		sb.append(TextUtils.join(", ", primaryKeyStrings));
		sb.append("));");
		return sb.toString();
	}

	public void executeAfterOnCreate(SupportSQLiteDatabase database) {
	}

	public Cursor query(SupportSQLiteDatabase database, Uri uri, SQLiteQueryBuilder builder, String[] projection, String selection, String[] selectionArgs, String groupBy, String having, String sortOrder, String limit, Logger logger) {
		final String query = builder.buildQuery(projection, selection, groupBy, having, sortOrder, limit);
		logger.LogQuery(clazz, uri, builder, projection, selection, selectionArgs, groupBy, having, sortOrder, limit);
		if(selectionArgs == null)
			return database.query(query);
		return database.query(query, selectionArgs);
	}

	public long insert(SupportSQLiteDatabase database, Uri uri, ContentValues values, Logger logger) {
		logger.LogD(clazz, "*insert* " + uri);
		final long rowId = database.insert(name, SQLiteDatabase.CONFLICT_FAIL, values);
		logger.LogD(clazz, "rowId:" + rowId + ", values: " + values);
		return rowId;
	}

	public int delete(SupportSQLiteDatabase database, Uri uri, String selection, String[] selectionArgs, Logger logger) {
		logger.LogD(clazz, "*delete* " + uri);
		int ret = database.delete(name, selection, selectionArgs);
		logger.LogD(clazz, "ret:" + ret + " -del: selectionArgs: " + Arrays.toString(selectionArgs) + "selection: " + selection);
		return ret;
	}

	public int update(SupportSQLiteDatabase database, Uri uri, ContentValues values, String selection, String[] selectionArgs, Logger logger) {
		logger.LogD(clazz, "*update* uri: " + uri.toString());
		final int ret = database.update(name, SQLiteDatabase.CONFLICT_FAIL, values, selection, selectionArgs);
		logger.LogD(clazz, "ret:" + ret + " selectionArgs: " + Arrays.toString(selectionArgs) + "selection: " + selection + "values: " + values);
		return ret;
	}

	public void createIndexes(SupportSQLiteDatabase db) {
		for (IndexInfo index : indexes) {
			db.execSQL(index.createStatement());
		}
	}
}
