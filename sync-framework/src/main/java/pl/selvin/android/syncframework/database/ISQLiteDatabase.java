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


package pl.selvin.android.syncframework.database;

import android.content.ContentValues;
import android.database.Cursor;

public interface ISQLiteDatabase extends IObjectWrapper {
    int update(String table, ContentValues values, String whereClause, String[] whereArgs);

    int delete(String table, String whereClause, String[] whereArgs);

    Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy);

    void execSQL(String sql);
    void execSQL(String sql, Object[] bindArgs);

    long replace (String table, String nullColumnHack, ContentValues values);
    long insert (String table, String nullColumnHack, ContentValues values);

    void beginTransaction();
    void endTransaction();
    void setTransactionSuccessful();
}
