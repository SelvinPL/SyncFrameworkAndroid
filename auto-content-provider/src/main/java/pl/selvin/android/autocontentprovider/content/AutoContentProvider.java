/*
 * Copyright (c) 2017-2018 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package pl.selvin.android.autocontentprovider.content;


import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.db.SupportSQLiteOpenHelper;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import pl.selvin.android.autocontentprovider.db.CascadeInfo;
import pl.selvin.android.autocontentprovider.db.TableInfo;
import pl.selvin.android.autocontentprovider.log.Logger;
import pl.selvin.android.autocontentprovider.utils.DatabaseUtilsCompat;
import pl.selvin.android.autocontentprovider.utils.SupportSQLiteOpenHelperFactoryProvider;

public abstract class AutoContentProvider extends ContentProvider {

    protected final ContentHelper contentHelper;
    private final SupportSQLiteOpenHelper.Callback defaultCallback;
    protected final Class<?> clazz = getClass();
    private SupportSQLiteOpenHelper mDB;
    private final SupportSQLiteOpenHelperFactoryProvider supportSQLiteOpenHelperFactoryProvider;
    protected final Logger logger;

    public AutoContentProvider(ContentHelper contentHelper, Logger logger, SupportSQLiteOpenHelperFactoryProvider supportSQLiteOpenHelperFactoryProvider) {
        this.logger = logger != null ? logger : Logger.EmptyLogger.INSTANCE;
        this.contentHelper = contentHelper;
        this.supportSQLiteOpenHelperFactoryProvider = supportSQLiteOpenHelperFactoryProvider;
        defaultCallback = new SupportSQLiteOpenHelper.Callback(contentHelper.DATABASE_VERSION) {
            @Override
            public void onCreate(SupportSQLiteDatabase db) {
                onCreateDatabase(db);
            }

            @Override
            public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                onUpgradeDatabase(db, oldVersion, newVersion);
            }

            @Override
            public void onDowngrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                onDowngradeDatabase(db, oldVersion, newVersion);
            }
        };
    }

    @Override
    public boolean onCreate() {
        mDB = getHelperFactory().create(getHelperConfiguration());
        return true;
    }

    final public SupportSQLiteDatabase getReadableDatabase() {
        return mDB.getReadableDatabase();
    }

    final public SupportSQLiteDatabase getWritableDatabase() {
        return mDB.getWritableDatabase();
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        final int code = contentHelper.matchUri(uri);
        if (code != UriMatcher.NO_MATCH) {
            final TableInfo tab = contentHelper.getTableFromCode(code & ContentHelper.uriCode);
            return (ContentHelper.isItemCode(code)) ? tab.itemMime : tab.dirMime;
        }
        throw new IllegalArgumentException("Unknown Uri " + uri);
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        final int code = contentHelper.matchUri(uri);
        if (code != UriMatcher.NO_MATCH) {
            final String limit;
            final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
            final TableInfo tab = contentHelper.getTableFromCode(code & ContentHelper.uriCode);
            builder.setTables(tab.name);
            if (ContentHelper.isItemCode(code)) {
                limit = null;
                final List<String> pathSegments = uri.getPathSegments();
                if (ContentHelper.isItemRowIDCode(code)) {
                    builder.appendWhere(tab.rowIdAlias + "=?");
                    selectionArgs = DatabaseUtilsCompat.appendSelectionArgs(new String[]{pathSegments.get(2)}, selectionArgs);
                } else {
                    builder.appendWhere(tab.getSelection());
                    final String[] querySelection = new String[tab.primaryKeys.size()];
                    for (int i = 0; i < tab.primaryKeys.size(); i++)
                        querySelection[i] = pathSegments.get(i + 1);
                    selectionArgs = DatabaseUtilsCompat.appendSelectionArgs(querySelection, selectionArgs);
                }
            } else {
                limit = uri.getQueryParameter(ContentHelper.PARAMETER_LIMIT);
            }
            builder.setProjectionMap(tab.map);
            final Cursor cursor = tab.query(getReadableDatabase(), uri, builder, projection, selection, selectionArgs, null, null, sortOrder, limit, logger);
            cursor.setNotificationUri(requireContext().getContentResolver(), uri);
            return cursor;
        }
        throw new IllegalArgumentException("Unknown Uri " + uri);
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        final int code = contentHelper.matchUri(uri);
        if (code != UriMatcher.NO_MATCH) {
            final TableInfo tab = contentHelper.getTableFromCode(code & ContentHelper.uriCode);
            if (tab.readonly) {
                throw new IllegalArgumentException("Table " + tab.name + " is readonly.");
            }
            if (ContentHelper.isItemCode(code)) {
                throw new IllegalArgumentException("Can not insert with Item type Uri.");
            }
            final long rowId = tab.insert(getWritableDatabase(), uri, values, logger);
            if (rowId > 0) {
                boolean syncToNetwork = ContentHelper.checkSyncToNetwork(uri);
                Uri ret_uri = contentHelper.getItemUri(tab.name, syncToNetwork, rowId);
                final ContentResolver cr = requireContext().getContentResolver();
                cr.notifyChange(uri, null, syncToNetwork);
                for (String n : tab.notifyUris) {
                    cr.notifyChange(Uri.parse(n), null, syncToNetwork);
                }
                return ret_uri;
            }
        }
        throw new RuntimeException("Failed to insert row into " + uri);

    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        final int code = contentHelper.matchUri(uri);
        if (code != UriMatcher.NO_MATCH) {
            if (code == ContentHelper.uriClearCode) {
                logger.LogD(clazz, "delete uriClearCode");
                getHelperCallback().onUpgrade(getWritableDatabase(), 1, contentHelper.DATABASE_VERSION);
                return 0;
            }
            boolean syncToNetwork = ContentHelper.checkSyncToNetwork(uri);
            final TableInfo tab = contentHelper.getTableFromCode(code & ContentHelper.uriCode);
            if (tab.readonly) {
                throw new IllegalArgumentException("Table " + tab.name + " is readonly.");
            }
            if (ContentHelper.isItemCode(code)) {
                final String newSelection;
                final String[] newSelectionArgs;
                if (ContentHelper.isItemRowIDCode(code)) {
                    newSelection = tab.rowIdAlias + "=?";
                    newSelectionArgs = new String[]{uri.getPathSegments().get(2)};
                } else {
                    newSelectionArgs = new String[tab.primaryKeys.size()];
                    for (int i = 0; i < tab.primaryKeys.size(); i++) {
                        newSelectionArgs[i] = uri.getPathSegments().get(i + 1);
                    }
                    newSelection = tab.getSelection();
                }
                selection = DatabaseUtilsCompat.concatenateWhere(selection, newSelection);
                selectionArgs = DatabaseUtilsCompat.appendSelectionArgs(selectionArgs, newSelectionArgs);
            }
            int ret;
            int cascadeResults = 0;
            ArrayList<String[]> deleteWhereArgs = new ArrayList<>();
            for (CascadeInfo info : tab.cascadeDelete) {
                final Cursor c = query(contentHelper.getDirUri(tab.name), info.pk, selection, selectionArgs, null);
                if (c != null) {
                    try {
                        if (c.moveToFirst()) {
                            deleteWhereArgs.clear();
                            final Uri deleteUri = contentHelper.getDirUri(info.table, syncToNetwork);
                            final StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < info.pk.length; i++) {
                                if (i > 0) sb.append("AND ");
                                sb.append(info.fk[i]);
                                sb.append("=? ");
                            }
                            final String deleteWhere = sb.toString();
                            do {
                                final String[] whereArgs = new String[info.pk.length];
                                for (int i = 0; i < info.pk.length; i++) {
                                    whereArgs[i] = c.getString(i);
                                }
                                deleteWhereArgs.add(whereArgs);
                            } while (c.moveToNext());
                            try {
                                for (String[] whereArgs : deleteWhereArgs) {
                                    cascadeResults += delete(deleteUri, deleteWhere, whereArgs);
                                }
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    } finally {
                        c.close();
                    }

                }
            }
            ret = tab.delete(getWritableDatabase(), uri, selection, selectionArgs, logger);
            if (ret > 0) {
                final ContentResolver cr = requireContext().getContentResolver();
                cr.notifyChange(uri, null, syncToNetwork);
                for (String n : tab.notifyUris) {
                    cr.notifyChange(Uri.parse(n), null, syncToNetwork);
                }
            }
            return ret + cascadeResults;
        }
        throw new IllegalArgumentException("Unknown Uri " + uri);
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        final int code = contentHelper.matchUri(uri);
        if (code != UriMatcher.NO_MATCH) {
            final TableInfo tab = contentHelper.getTableFromCode(code & ContentHelper.uriCode);
            if (tab.readonly) {
                throw new IllegalArgumentException("Table " + tab.name + " is readonly.");
            }
            if (ContentHelper.isItemCode(code)) {
                final String newSelection;
                final String[] newSelectionArgs;
                if (ContentHelper.isItemRowIDCode(code)) {
                    newSelection = tab.rowIdAlias + "=?";
                    newSelectionArgs = new String[]{uri.getPathSegments().get(2)};
                } else {
                    newSelectionArgs = new String[tab.primaryKeys.size()];
                    for (int i = 0; i < tab.primaryKeys.size(); i++) {
                        newSelectionArgs[i] = uri.getPathSegments().get(i + 1);
                    }
                    newSelection = tab.getSelection();
                }
                selection = DatabaseUtilsCompat.concatenateWhere(selection, newSelection);
                selectionArgs = DatabaseUtilsCompat
                        .appendSelectionArgs(selectionArgs, newSelectionArgs);
            }
            final int ret = tab.update(getWritableDatabase(), uri, values, selection, selectionArgs, logger);
            if (ret > 0) {
                boolean syncToNetwork = ContentHelper.checkSyncToNetwork(uri);
                final ContentResolver cr = requireContext().getContentResolver();
                cr.notifyChange(uri, null, syncToNetwork);
                for (String n : tab.notifyUris) {
                    cr.notifyChange(Uri.parse(n), null, syncToNetwork);
                }
            }
            return ret;
        }
        throw new IllegalArgumentException("Unknown Uri " + uri);
    }

    protected SupportSQLiteOpenHelper.Factory getHelperFactory() {
        return supportSQLiteOpenHelperFactoryProvider.createFactory(requireContext());
    }


    protected SupportSQLiteOpenHelper.Callback getHelperCallback() {
        return defaultCallback;
    }

    protected SupportSQLiteOpenHelper.Configuration getHelperConfiguration() {
        return SupportSQLiteOpenHelper.Configuration.builder(getContext()).name(contentHelper.DATABASE_NAME)
                .callback(getHelperCallback()).build();
    }

    protected void onCreateDatabase(SupportSQLiteDatabase db) {
        try {
            for (TableInfo table : contentHelper.getAllTables()) {
                final String create = table.createStatement();
                db.execSQL(create);
                table.executeAfterOnCreate(db);
                table.createIndexes(db);
                logger.LogD(clazz, "*onCreateDataBase*: " + create);
            }
        } catch (Exception e) {
            logger.LogE(clazz, "*onCreateDataBase*: " + e.toString(), e);
            throw e;
        }
    }

    public Context requireContext() {
        final Context ctx = getContext();
        if (ctx == null)
            throw new RuntimeException("Context is null");
        return ctx;
    }


    @SuppressWarnings("unused")
    protected void onUpgradeDatabase(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
        final Cursor c = db.query("SELECT 'DROP TABLE ' || name || ';' AS cmd FROM sqlite_master WHERE type='table' AND name<>'android_metadata'");
        final ArrayList<String> commands = new ArrayList<>();
        if (c.moveToFirst()) {
            do {
                commands.add(c.getString(0));
            } while (c.moveToNext());
        }
        c.close();
        for (String command : commands) {
            try {
                logger.LogD(clazz, "*onUpgradeDatabase*: " + command);
                db.execSQL(command);
            } catch (Exception e) {
                logger.LogE(clazz, e);
                throw e;
            }
        }
        onCreateDatabase(db);
    }

    protected void onDowngradeDatabase(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgradeDatabase(db, oldVersion, newVersion);
    }
}
