/**
 * Copyright (c) 2014 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package pl.selvin.android.syncframework.content;

import android.annotation.TargetApi;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import pl.selvin.android.syncframework.support.v4.database.DatabaseUtilsCompat;

public abstract class BaseContentProvider extends ContentProvider {
    public static final String SYNC_SYNCSTATS = "SYNC_PARAM_IN_SYNCSTATS";
    private static final String DATABASE_OPERATION_TYPE = "DATABASE_OPERATION_TYPE";
    protected final RequestExecutor executor;
    protected final ContentHelper contentHelper;
    private final Class<?> clazz;
    private final Logger logger;
    private OpenHelper mDB;
    public final static String DATABASE_OPERATION_TYPE_UPGRADE= "DATABASE_OPERATION_TYPE_UPGRADE";
    public final static String DATABASE_OPERATION_TYPE_CREATE = "DATABASE_OPERATION_TYPE_CREATE";
    public final static String ACTION_SYNC_FRAMEWORK_DATABASE = "ACTION_SYNC_FRAMEWORK_DATABASE";

    public BaseContentProvider(ContentHelper contentHelper, RequestExecutor executor) {
        clazz = getClass();
        this.logger = contentHelper.getLogger();
        this.executor = executor;
        this.contentHelper = contentHelper;
    }

    public static boolean isItemCode(int code) {
        return (code & ContentHelper.uriCodeItemFlag) == ContentHelper.uriCodeItemFlag;
    }

    public static boolean isItemRowIDCode(int code) {
        return (code & ContentHelper.uriCodeItemRowIDFlag) == ContentHelper.uriCodeItemRowIDFlag;
    }

    public boolean onCreate() {
        mDB = new OpenHelper();
        return true;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        logger.LogD(clazz, "*delete* " + uri);
        final int code = contentHelper.matchUri(uri);
        if (code != UriMatcher.NO_MATCH) {
            if (code == ContentHelper.uriClearCode) {
                logger.LogD(clazz, "delete uriClearCode");
                mDB.onUpgrade(getWritableDatabase(), 1, contentHelper.DATABASE_VERSION);
                return 0;
            }
            if (code == ContentHelper.uriSyncCode) {
                throw new IllegalArgumentException("Can not delete with Sync Uri.");
            }
            boolean syncToNetwork = checkSyncToNetwork(uri);
            final TableInfo tab = contentHelper.getTableFromCode(code & ContentHelper.uriCode);
            if (tab.readonly) {
                throw new IllegalArgumentException("Table " + tab.name + " is readonly.");
            }
            if (isItemCode(code)) {
                final String newSelection;
                final String[] newSelectionArgs;
                if (isItemRowIDCode(code)) {
                    newSelection = "isDeleted=0 AND ROWID=?";
                    newSelectionArgs = new String[]{uri.getPathSegments().get(2)};
                } else {
                    newSelectionArgs = new String[tab.primaryKey.length];
                    for (int i = 0; i < tab.primaryKey.length; i++) {
                        newSelectionArgs[i] = uri.getPathSegments().get(i + 1);
                    }
                    newSelection = "isDeleted=0 " + tab.getSelection();
                }
                selection = DatabaseUtilsCompat.concatenateWhere(selection, newSelection);
                selectionArgs = DatabaseUtilsCompat
                        .appendSelectionArgs(selectionArgs, newSelectionArgs);
            } else {
                selection = DatabaseUtilsCompat.concatenateWhere(selection, "isDeleted=0");
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
            ContentValues values = new ContentValues(2);
            values.put("isDirty", 1);
            values.put("isDeleted", 1);
            final String updateSelection = DatabaseUtilsCompat.concatenateWhere("tempId IS NULL", selection);
            ret = getWritableDatabase().update(tab.name, values, updateSelection, selectionArgs);
            logger.LogD(clazz, "ret:" + ret + " -upd: selectionArgs: " + Arrays.toString(selectionArgs) + "selection: " + updateSelection + " values: " + String.valueOf(values));
            final String deleteSelection = DatabaseUtilsCompat.concatenateWhere("tempId IS NOT NULL", selection);
            ret += getWritableDatabase().delete(tab.name, deleteSelection, selectionArgs);
            logger.LogD(clazz, "ret:" + ret + " -del: selectionArgs: " + Arrays.toString(selectionArgs) + "selection: " + deleteSelection);
            if (ret > 0) {
                final ContentResolver cr = getContext().getContentResolver();
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
    public String getType(Uri uri) {
        final int code = contentHelper.matchUri(uri);
        if (code != UriMatcher.NO_MATCH) {
            if (code == ContentHelper.uriSyncCode) {
                throw new IllegalArgumentException("There is no type for SYNC Uri: " + uri);
            }
            final TableInfo tab = contentHelper.getTableFromCode(code & ContentHelper.uriCode);
            return (isItemCode(code)) ? tab.ItemMime : tab.DirMime;
        }
        throw new IllegalArgumentException("Unknown Uri " + uri);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        logger.LogD(clazz, "*insert* " + uri);
        final int code = contentHelper.matchUri(uri);
        if (code != UriMatcher.NO_MATCH) {
            if (code == ContentHelper.uriSyncCode) {
                throw new IllegalArgumentException("Can not insert with Sync Uri.");
            }
            final TableInfo tab = contentHelper.getTableFromCode(code & ContentHelper.uriCode);
            if (tab.readonly) {
                throw new IllegalArgumentException("Table " + tab.name + " is readonly.");
            }
            if (isItemCode(code)) {
                throw new IllegalArgumentException("Can not insert with Item type Uri.");
            }
            /*-String tempId = UUID.randomUUID().toString();
            if (tab.primaryKey.length == 1
					&& tab.primaryKey[0].type == ColumnType.guid) {
				tempId = values.getAsString(tab.primaryKey[0].name);
			}*/
            boolean syncToNetwork = checkSyncToNetwork(uri);
            values.put("tempId", UUID.randomUUID().toString());
            values.put("isDirty", 1);
            values.put("isDeleted", 0);
            long rowId = getWritableDatabase().insert(tab.name, null, values);
            logger.LogD(clazz, "rowId:" + rowId + ", values: " + String.valueOf(values));
            if (rowId > 0) {
                Uri ret_uri = contentHelper.getItemUri(tab.name, syncToNetwork, rowId);
                final ContentResolver cr = getContext().getContentResolver();
                cr.notifyChange(uri, null, syncToNetwork);
                for (String n : tab.notifyUris) {
                    cr.notifyChange(Uri.parse(n), null, syncToNetwork);
                }
                return ret_uri;
            }
        }
        throw new SQLException("Failed to insert row into " + uri);

    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        final int code = contentHelper.matchUri(uri);
        if (code != UriMatcher.NO_MATCH) {
            if (code == ContentHelper.uriSyncCode) {
                return null;
            }
            final String limit;
            final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
            final TableInfo tab = contentHelper.getTableFromCode(code & ContentHelper.uriCode);
            builder.setTables(tab.name);
            if (isItemCode(code)) {
                limit = null;
                final List<String> pathSegments = uri.getPathSegments();
                if (isItemRowIDCode(code)) {
                    builder.appendWhere(_.isDeleted + "=0 AND ROWID=?");
                    selectionArgs = DatabaseUtilsCompat
                            .appendSelectionArgs(new String[]{pathSegments.get(2)}, selectionArgs);
                } else {
                    builder.appendWhere(_.isDeleted + "=0" + tab.getSelection());
                    final String[] querySelection = new String[tab.primaryKey.length];
                    for (int i = 0; i < tab.primaryKey.length; i++)
                        querySelection[i] = pathSegments.get(i + 1);
                    selectionArgs = DatabaseUtilsCompat
                            .appendSelectionArgs(querySelection, selectionArgs);
                }
            } else {
                limit = uri.getQueryParameter(ContentHelper.PARAMETER_LIMIT);
                builder.appendWhere(_.isDeleted + "=0");
            }
            builder.setProjectionMap(tab.map);
            LogQuery(uri, builder, projection, selection, selectionArgs, null, null, sortOrder,
                    limit);
            final Cursor cursor = builder
                    .query(getReadableDatabase(), projection, selection, selectionArgs, null, null,
                            sortOrder, limit);
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
            return cursor;
        }
        throw new IllegalArgumentException("Unknown Uri " + uri);
    }

    protected void LogQuery(Uri uri, SQLiteQueryBuilder builder, String[] projection,
                            String selection, String[] selectionArgs, String groupBy, String having,
                            String sortOrder, String limit) {
        logger.LogD(clazz, uri + "");
        //noinspection deprecation
        logger.LogD(clazz, builder.buildQuery(projection, selection, null, groupBy, having, sortOrder, limit));
        logger.LogD(clazz, Arrays.toString(selectionArgs));
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public Bundle call(String method, String arg, Bundle syncParams) {
        try {
            Uri uri = Uri.parse(method);
            if (contentHelper.matchUri(uri) == ContentHelper.uriSyncCode) {
                final SyncStats inout = sync(uri.getPathSegments().get(1), uri.getPathSegments().get(2), arg, (SyncStats) syncParams.getParcelable(SYNC_SYNCSTATS));
                syncParams.putParcelable(SYNC_SYNCSTATS, inout);
            }
        } catch (Exception ex) {
            final SyncStats inout = syncParams.getParcelable(SYNC_SYNCSTATS);
            if(inout != null) {
                inout.numIoExceptions++;
                syncParams.putParcelable(SYNC_SYNCSTATS, inout);
            }
            ex.printStackTrace();
        }
        return syncParams;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        final int code = contentHelper.matchUri(uri);
        if (code != UriMatcher.NO_MATCH) {
            if (code == ContentHelper.uriSyncCode) {
                logger.LogD(clazz, "*update* sync uri: " + uri.toString());
                final SyncStats result = sync(uri.getPathSegments().get(1), uri.getPathSegments().get(2), selection, new SyncStats());
                return (result.hasErrors() ? 1 : 0);
            }
            final TableInfo tab = contentHelper.getTableFromCode(code & ContentHelper.uriCode);
            if (tab.readonly) {
                throw new IllegalArgumentException("Table " + tab.name + " is readonly.");
            }
            logger.LogD(clazz, "*update* uri: " + uri.toString());
            if (isItemCode(code)) {
                final String newSelection;
                final String[] newSelectionArgs;
                if (isItemRowIDCode(code)) {
                    newSelection = "isDeleted=0 AND ROWID=?";
                    newSelectionArgs = new String[]{uri.getPathSegments().get(2)};
                } else {
                    newSelectionArgs = new String[tab.primaryKey.length];
                    for (int i = 0; i < tab.primaryKey.length; i++) {
                        newSelectionArgs[i] = uri.getPathSegments().get(i + 1);
                    }
                    newSelection = "isDeleted=0 " + tab.getSelection();
                }
                selection = DatabaseUtilsCompat.concatenateWhere(selection, newSelection);
                selectionArgs = DatabaseUtilsCompat
                        .appendSelectionArgs(selectionArgs, newSelectionArgs);
            } else {
                selection = DatabaseUtilsCompat.concatenateWhere(selection, "isDeleted=0");
            }
            boolean syncToNetwork = checkSyncToNetwork(uri);
            values.put("isDirty", 1);
            int ret = getWritableDatabase().update(tab.name, values, selection, selectionArgs);
            logger.LogD(clazz, "ret:" + ret + " selectionArgs: " + Arrays.toString(selectionArgs) + "selection: " + selection + "values: " + String.valueOf(values));
            if (ret > 0) {
                final ContentResolver cr = getContext().getContentResolver();
                cr.notifyChange(uri, null, syncToNetwork);
                for (String n : tab.notifyUris) {
                    cr.notifyChange(Uri.parse(n), null, syncToNetwork);
                }
            }
            return ret;
        }
        throw new IllegalArgumentException("Unknown Uri " + uri);
    }

    boolean checkSyncToNetwork(Uri uri) {
        final String syncToNetworkUri = uri.getQueryParameter(ContentHelper.PARAMETER_SYNC_TO_NETWORK);
        return syncToNetworkUri == null || Boolean.parseBoolean(syncToNetworkUri);
    }

    final public SQLiteDatabase getReadableDatabase() {
        return mDB.getReadableDatabase();
    }

    final public SQLiteDatabase getWritableDatabase() {
        return mDB.getWritableDatabase();
    }

    public SyncStats sync(String service, String scope, String params, SyncStats stats) {
        final long start = System.currentTimeMillis();
        boolean hasError = false;
        if (params == null) params = "";
        final SQLiteDatabase db = mDB.getWritableDatabase();
        final ArrayList<TableInfo> notifyTableInfo = new ArrayList<>();
        final JsonFactory jsonFactory = new JsonFactory();
        JsonToken current;
        final String download = String.format(contentHelper.DOWNLOAD_SERVICE_URI, service, scope, params);
        final String upload = String.format(contentHelper.UPLOAD_SERVICE_URI, service, scope, params);
        final String scopeServerBlob = String.format("%s.%s.%s", service, scope, _.serverBlob);
        String serverBlob = null;
        Cursor cur = db.query(BlobsTable.NAME, new String[]{BlobsTable.C_VALUE}, BlobsTable.C_NAME + "=?",
                new String[]{scopeServerBlob}, null, null, null);
        final String originalBlob;
        if (cur.moveToFirst()) {
            originalBlob = serverBlob = cur.getString(0);
        } else {
            originalBlob = null;
        }
        cur.close();
        db.beginTransaction();
        try {
            boolean noChanges = false;
            if (serverBlob != null) {
                noChanges = !contentHelper.hasDirtTable(db, scope);
            }
            boolean resolveConflicts = false;
            final Metadata meta = new Metadata();
            final HashMap<String, Object> vals = new HashMap<>();
            final ContentValues cv = new ContentValues(2);
            String name;
            boolean moreChanges = false;
            boolean forceMoreChanges = false;
            do {
                final int requestMethod;
                final String serviceRequestUrl;
                final ISyncContentProducer contentProducer;

                if (serverBlob != null) {
                    requestMethod = RequestExecutor.HTTP_POST;
                    if (noChanges) {
                        serviceRequestUrl = download;
                    } else {
                        serviceRequestUrl = upload;
                        forceMoreChanges = true;
                    }

                    contentProducer = new SyncContentProducer(jsonFactory, db, scope, serverBlob, !noChanges, notifyTableInfo, contentHelper);
                    noChanges = true;
                } else {
                    requestMethod = RequestExecutor.HTTP_GET;
                    serviceRequestUrl = download;
                    contentProducer = null;

                }
                if (moreChanges) {
                    db.beginTransaction();
                }
                logger.LogD(getClass(), serviceRequestUrl);
                RequestExecutor.Result result = executeRequest(requestMethod, serviceRequestUrl, contentProducer);
                if (result.status == 200) {
                    if (contentProducer != null) stats.numEntries += contentProducer.getChanges();
                    final JsonParser jp = jsonFactory.createParser(result.inputBuffer);

                    jp.nextToken(); // skip ("START_OBJECT(d) expected");
                    jp.nextToken(); // skip ("FIELD_NAME(d) expected");
                    if (jp.nextToken() != JsonToken.START_OBJECT)
                        throw new JsonParseException("START_OBJECT(d - object) expected",
                                jp.getCurrentLocation());
                    while (jp.nextToken() != JsonToken.END_OBJECT) {
                        name = jp.getCurrentName();
                        if (_.__sync.equals(name)) {
                            jp.nextToken();
                            while (jp.nextToken() != JsonToken.END_OBJECT) {
                                name = jp.getCurrentName();
                                jp.nextToken();
                                switch (name) {
                                    case _.serverBlob:
                                        serverBlob = jp.getText();
                                        break;
                                    case _.moreChangesAvailable:
                                        moreChanges = jp.getBooleanValue() || forceMoreChanges;
                                        forceMoreChanges = false;
                                        break;
                                    case _.resolveConflicts:
                                        resolveConflicts = jp.getBooleanValue();
                                        break;
                                }
                            }
                        } else if (_.results.equals(name)) {
                            if (jp.nextToken() != JsonToken.START_ARRAY)
                                throw new JsonParseException("START_ARRAY(results) expected",
                                        jp.getCurrentLocation());
                            while (jp.nextToken() != JsonToken.END_ARRAY) {
                                meta.isDeleted = false;
                                meta.tempId = null;
                                vals.clear();
                                while (jp.nextToken() != JsonToken.END_OBJECT) {
                                    name = jp.getCurrentName();
                                    current = jp.nextToken();
                                    switch (current) {
                                        case VALUE_STRING:
                                            vals.put(name, jp.getText());
                                            break;
                                        case VALUE_NUMBER_INT:
                                            vals.put(name, jp.getLongValue());
                                            break;
                                        case VALUE_NUMBER_FLOAT:
                                            vals.put(name, jp.getDoubleValue());
                                            break;
                                        case VALUE_FALSE:
                                            vals.put(name, 0L);
                                            break;
                                        case VALUE_TRUE:
                                            vals.put(name, 1L);
                                            break;
                                        case VALUE_NULL:
                                            vals.put(name, null);
                                            break;
                                        case START_OBJECT:
                                            switch (name) {
                                                case _.__metadata:
                                                    while (jp.nextToken() != JsonToken.END_OBJECT) {
                                                        name = jp.getCurrentName();
                                                        jp.nextToken();
                                                        switch (name) {
                                                            case _.uri:
                                                                meta.uri = jp.getText();
                                                                break;
                                                            case _.type:
                                                                meta.type = jp.getText();
                                                                break;
                                                            case _.isDeleted:
                                                                meta.isDeleted = jp
                                                                        .getBooleanValue();
                                                                break;
                                                            case _.tempId:
                                                                meta.tempId = jp.getText();
                                                                break;
                                                        }
                                                    }
                                                    break;
                                                case _.__syncConflict:
                                                    while (jp.nextToken() != JsonToken.END_OBJECT) {
                                                        name = jp.getCurrentName();
                                                        jp.nextToken();
                                                        switch (name) {
                                                            case _.isResolved:
                                                                break;
                                                            case _.conflictResolution:
                                                                break;
                                                            case _.conflictingChange:
                                                                while (jp
                                                                        .nextToken() != JsonToken
                                                                        .END_OBJECT) {
                                                                    name = jp.getCurrentName();
                                                                    current = jp.nextToken();
                                                                    if (current == JsonToken
                                                                            .START_OBJECT) {
                                                                        if (_.__metadata
                                                                                .equals(name)) {
                                                                            //noinspection StatementWithEmptyBody
                                                                            while (jp
                                                                                    .nextToken()
                                                                                    != JsonToken
                                                                                    .END_OBJECT) {
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                break;
                                                        }
                                                    }
                                                    // resolve conf
                                                    break;
                                                case _.__syncError:
                                                    //noinspection StatementWithEmptyBody
                                                    while (jp.nextToken() != JsonToken.END_OBJECT) {
                                                    }
                                                    break;
                                            }
                                            break;
                                        default:
                                            throw new JsonParseException(
                                                    "Wrong jsonToken: " + current,
                                                    jp.getCurrentLocation());
                                    }

                                }
                                final TableInfo tab = contentHelper.getTableFromType(meta.type);
                                if (meta.isDeleted) {
                                    tab.DeleteWithUri(meta.uri, db);
                                    stats.numDeletes++;
                                } else {
                                    if (tab.SyncJSON(vals, meta, db)) stats.numUpdates++;
                                    else stats.numInserts++;
                                }
                                if (!notifyTableInfo.contains(tab)) notifyTableInfo.add(tab);
                            }
                        }
                    }
                    jp.close();
                    if (resolveConflicts)
                        logger.LogE(clazz, "*Sync* has resolve conflicts: " + resolveConflicts);
                    else
                        logger.LogD(clazz, "*Sync* has resolve conflicts: " + resolveConflicts);
                    if (!hasError) {
                        cv.clear();
                        cv.put(BlobsTable.C_NAME, scopeServerBlob);
                        cv.put(BlobsTable.C_VALUE, serverBlob);
                        cv.put(BlobsTable.C_DATE, Calendar.getInstance().getTimeInMillis());
                        cv.put(BlobsTable.C_STATE, 0);
                        db.replace(BlobsTable.NAME, null, cv);
                        db.setTransactionSuccessful();
                        db.endTransaction();
                        logger.LogD(clazz, "*Sync* commit changes");
                        final ContentResolver cr = getContext().getContentResolver();
                        for (TableInfo t : notifyTableInfo) {
                            final Uri nu = contentHelper.getDirUri(t.name);
                            cr.notifyChange(nu, null, false);
                            logger.LogD(clazz, "*Sync* notifyChange table: " + t.name + ", uri: " + nu);
                            for (String n : t.notifyUris) {
                                cr.notifyChange(Uri.parse(n), null, false);
                                logger.LogD(clazz, "\t+ uri: " + n);
                            }
                        }
                        notifyTableInfo.clear();
                    }
                } else {
                    logger.LogE(clazz, "*Sync* Server error: " + result.status);
                    logger.LogE(clazz, "\t" + result.getError());
                    hasError = true;
                    break;
                }
                result.close();
            } while (moreChanges);
        } catch (final IOException e) {
            stats.numIoExceptions++;
            hasError = true;
            logger.LogE(clazz, e);
            Log.d("Blob:", originalBlob == null ? "null" : originalBlob);
        }
        if (hasError) {
            db.endTransaction();
            ContentValues cv = new ContentValues();
            cv.put(BlobsTable.C_NAME, scopeServerBlob);
            cv.put(BlobsTable.C_VALUE, originalBlob);
            cv.put(BlobsTable.C_DATE, Calendar.getInstance().getTimeInMillis());
            cv.put(BlobsTable.C_STATE, -1);
            db.replace(BlobsTable.NAME, null, cv);
        }
        /*-if (!hasError) {
            final ContentValues cv = new ContentValues(2);
			cv.put(BlobsTable.C_NAME, scopeServerBlob);
			cv.put(BlobsTable.C_VALUE, serverBlob);
			db.replace(BlobsTable.NAME, null, cv);
			db.setTransactionSuccessful();
		}
		db.endTransaction();
		if (!hasError) {
			for (String t : notifyTableInfo) {
				getContext().getContentResolver().notifyChange(getDirUri(t),
						null);
			}
		}*/
        logger.LogTimeD(clazz, "*Sync* time", start);
        return stats;
    }

    protected RequestExecutor.Result executeRequest(int requestMethod, String serviceRequestUrl, final ISyncContentProducer syncContentProducer) throws IOException {
        return executor.execute(requestMethod, serviceRequestUrl, syncContentProducer);
    }

    public void onDowngradeDatabase(SQLiteDatabase db, int oldVersion, int newVersion) {
        mDB.superOnDowngrade(db, oldVersion, newVersion);
    }

    protected void onCreateDataBase(SQLiteDatabase db) {
        final Intent intent = new Intent(ACTION_SYNC_FRAMEWORK_DATABASE);
        intent.putExtra(DATABASE_OPERATION_TYPE, DATABASE_OPERATION_TYPE_CREATE);
        getContext().sendBroadcast(intent);
        try {
            for (TableInfo table : contentHelper.getAllTables()) {
                String create = table.CreateStatement();
                db.execSQL(create);
            }
            db.execSQL(String.format(
                    "CREATE TABLE [%s] ([%s] VARCHAR NOT NULL, [%s] VARCHAR, " + "[%s] LONG NOT " +
                            "NULL, [%s] INT NOT NULL, PRIMARY KEY([%s]))",
                    BlobsTable.NAME, BlobsTable.C_NAME, BlobsTable.C_VALUE, BlobsTable.C_DATE,
                    BlobsTable.C_STATE, BlobsTable.C_NAME));
        } catch (Exception e) {
            logger.LogE(clazz, "*onCreateDataBase*: " + e.toString(), e);
        }
    }

    protected void onUpgradeDatabase(SQLiteDatabase db, int oldVersion, int newVersion) {
        final Intent intent = new Intent(ACTION_SYNC_FRAMEWORK_DATABASE);
        intent.putExtra(DATABASE_OPERATION_TYPE, DATABASE_OPERATION_TYPE_UPGRADE);
        getContext().sendBroadcast(intent);
        Cursor c = db.query("sqlite_master", new String[]{"'DROP TABLE ' || name || ';' AS cmd"},
                "type=? AND name<>?", new String[]{"table", "android_metadata"}, null, null, null);
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
            }
        }
        onCreateDataBase(db);
    }

    public interface ISyncContentProducer {
        int getChanges();

        void writeTo(final OutputStream outputStream) throws IOException;
    }

    private static class SyncContentProducer implements ISyncContentProducer {
        final SQLiteDatabase db;
        final String scope;
        final String serverBlob;
        final boolean upload;
        final ArrayList<TableInfo> notifyTableInfo;
        final ContentHelper ch;
        JsonFactory factory;
        int counter = 0;

        public SyncContentProducer(JsonFactory factory, SQLiteDatabase db, String scope,
                                   String serverBlob, boolean upload,
                                   ArrayList<TableInfo> notifyTableInfo,
                                   ContentHelper ch) {
            this.factory = factory;
            this.db = db;
            this.scope = scope;
            this.serverBlob = serverBlob;
            this.upload = upload;
            this.notifyTableInfo = notifyTableInfo;
            this.ch = ch;
        }

        public int getChanges() {
            return counter;
        }

        public void writeTo(final OutputStream outputStream) throws IOException {
            final JsonGenerator generator = factory.createGenerator(outputStream);
            generator.writeStartObject();
            generator.writeObjectFieldStart(_.d);
            generator.writeObjectFieldStart(_.__sync);
            generator.writeBooleanField(_.moreChangesAvailable, false);
            generator.writeStringField(_.serverBlob, serverBlob);
            generator.writeEndObject(); // sync
            generator.writeArrayFieldStart(_.results);
            if (upload) {
                for (TableInfo tab : ch.getAllTables()) {
                    if (tab.scope.toLowerCase().equals(scope.toLowerCase()))
                        counter += tab.getChanges(db, generator);
                }
            }
            generator.writeEndArray();// result
            generator.writeEndObject(); // d
            generator.writeEndObject();
            generator.close();
        }
    }

    final class OpenHelper extends SQLiteOpenHelper {

        public OpenHelper() {
            super(getContext(), contentHelper.DATABASE_NAME, null, contentHelper.DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            onCreateDataBase(db);
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onDowngradeDatabase(db, oldVersion, newVersion);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onUpgradeDatabase(db, oldVersion, newVersion);
        }

        void superOnDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                super.onDowngrade(db, oldVersion, newVersion);
        }

    }
}
