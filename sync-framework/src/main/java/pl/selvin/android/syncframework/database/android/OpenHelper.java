/***
 * Copyright (c) 2014-2017 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package pl.selvin.android.syncframework.database.android;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.os.Build;

import java.util.HashMap;

import pl.selvin.android.syncframework.content.BaseContentProvider;
import pl.selvin.android.syncframework.content.ContentHelper;
import pl.selvin.android.syncframework.database.ISQLiteDatabase;
import pl.selvin.android.syncframework.database.ISQLiteOpenHelper;
import pl.selvin.android.syncframework.database.ISQLiteQueryBuilder;

public class OpenHelper extends SQLiteOpenHelper implements ISQLiteOpenHelper {
    final private HashMap<SQLiteDatabase, ISQLiteDatabase> wrappedDatabase = new HashMap<>();
    final private BaseContentProvider provider;

    public OpenHelper(BaseContentProvider provider, ContentHelper contentHelper) {
        super(provider.getContext(), contentHelper.DATABASE_NAME, null, contentHelper.DATABASE_VERSION);
        this.provider = provider;
    }

    private synchronized ISQLiteDatabase wrapObject(SQLiteDatabase database) {
        final ISQLiteDatabase idb;
        if (!wrappedDatabase.containsKey(database)) {
            idb = new SQLiteDatabaseImpl(database);
            wrappedDatabase.put(database, idb);
        } else
            idb = wrappedDatabase.get(database);
        return idb;
    }

    @Override
    public Object getWrappedObject() {
        return this;
    }

    @Override
    public void onCreate(ISQLiteDatabase db) {
        provider.onCreateDataBase(db);
    }

    @Override
    public void onUpgrade(ISQLiteDatabase db, int oldVersion, int newVersion) {
        provider.onUpgradeDatabase(db, oldVersion, newVersion);
    }

    @Override
    public void onDowngrade(ISQLiteDatabase db, int oldVersion, int newVersion) {
        provider.onUpgradeDatabase(db, oldVersion, newVersion);
    }

    @Override
    public void superOnDowngrade(ISQLiteDatabase db, int oldVersion, int newVersion) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            super.onDowngrade((SQLiteDatabase) db.getWrappedObject(), oldVersion, newVersion);
    }

    @Override
    public ISQLiteDatabase getReadableISQLiteDatabase() {
        return wrapObject(super.getReadableDatabase());
    }

    @Override
    public ISQLiteDatabase getWritableISQLiteDatabase() {
        return wrapObject(super.getWritableDatabase());
    }

    @Override
    public ISQLiteQueryBuilder createQueryBuilder() {
        return new SQLiteQueryBuilderImpl();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        onCreate(wrapObject(db));
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(wrapObject(db), oldVersion, newVersion);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onDowngrade(wrapObject(db), oldVersion, newVersion);
    }

    private static class SQLiteDatabaseImpl implements ISQLiteDatabase {

        final SQLiteDatabase database;

        SQLiteDatabaseImpl(SQLiteDatabase database) {
            this.database = database;
        }

        @Override
        public Object getWrappedObject() {
            return database;
        }

        @Override
        public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
            return database.update(table, values, whereClause, whereArgs);
        }

        @Override
        public int delete(String table, String whereClause, String[] whereArgs) {
            return database.delete(table, whereClause, whereArgs);
        }

        @Override
        public Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy) {
            return database.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
        }

        @Override
        public void execSQL(String sql) {
            database.execSQL(sql);
        }

        @Override
        public void execSQL(String sql, Object[] bindArgs) {
            database.execSQL(sql, bindArgs);
        }

        @Override
        public long replace(String table, String nullColumnHack, ContentValues values) {
            return database.replace(table, nullColumnHack, values);
        }

        @Override
        public long insert(String table, String nullColumnHack, ContentValues values) {
            return database.insert(table, nullColumnHack, values);
        }

        @Override
        public void beginTransaction() {
            database.beginTransaction();
        }

        @Override
        public void endTransaction() {
            database.endTransaction();
        }

        @Override
        public void setTransactionSuccessful() {
            database.setTransactionSuccessful();
        }
    }

    private class SQLiteQueryBuilderImpl implements ISQLiteQueryBuilder {

        final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();

        @Override
        public Object getWrappedObject() {
            return builder;
        }

        @Override
        public String buildQuery(String[] projectionIn, String selection, String[] selectionArgs, String groupBy, String having, String sortOrder, String limit) {
            return builder.buildQuery(projectionIn, selection, selectionArgs, groupBy, having, sortOrder, limit);
        }

        @Override
        public Cursor query(ISQLiteDatabase readableDatabase, String[] projectionIn, String selection, String[] selectionArgs, String groupBy, String having, String sortOrder, String limit) {
            return builder.query((SQLiteDatabase) readableDatabase.getWrappedObject(), projectionIn, selection, selectionArgs, groupBy, having, sortOrder, limit);
        }

        @Override
        public void setProjectionMap(HashMap<String, String> columnMap) {
            builder.setProjectionMap(columnMap);
        }

        @Override
        public void appendWhere(CharSequence inWhere) {
            builder.appendWhere(inWhere);
        }

        @Override
        public void setTables(String inTables) {
            builder.setTables(inTables);
        }
    }
}
