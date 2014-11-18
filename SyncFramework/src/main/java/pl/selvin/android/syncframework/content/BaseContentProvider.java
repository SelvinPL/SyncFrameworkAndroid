/***
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

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import pl.selvin.android.syncframework.BuildConfig;

public abstract class BaseContentProvider extends ContentProvider {

    protected final static int HTTP_GET = 1;
    protected final static int HTTP_POST = 2;
    final static String TAG = "BaseContentProvider";
    final static DefaultHttpClient Instance;

    static {
        HttpParams httpParameters = new BasicHttpParams();
        int timeout = 30000;
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeout);
        HttpConnectionParams.setSoTimeout(httpParameters, timeout);
        Instance = new DefaultHttpClient(httpParameters);
        Instance.addRequestInterceptor(new HttpRequestInterceptor() {

            public void process(final HttpRequest request,
                                final HttpContext context) throws HttpException,
                    IOException {
                request.addHeader("Cache-Control", "no-store,no-cache");
                request.addHeader("Pragma", "no-cache");
                request.addHeader("Accept-Encoding", "gzip");
                request.setHeader("Accept", "application/json");
                request.setHeader("Content-type",
                        "application/json; charset=utf-8");
            }

        });
        Instance.addResponseInterceptor(new HttpResponseInterceptor() {
            public void process(final HttpResponse response,
                                final HttpContext context) throws HttpException,
                    IOException {
                HttpEntity entity = response.getEntity();
                Header ceheader = entity.getContentEncoding();
                if (ceheader != null) {
                    HeaderElement[] codecs = ceheader.getElements();
                    for (HeaderElement codec : codecs) {
                        if (codec.getName().equalsIgnoreCase("gzip")) {
                            response.setEntity(new GzipDecompressingEntity(
                                    response.getEntity()));
                            return;
                        }
                    }
                }
            }

        });
    }

    private static final boolean DEBUG = true;//BuildConfig.DEBUG;
    protected final ContentHelper contentHelper;
    protected OpenHelper mDB;


    public BaseContentProvider(ContentHelper contentHelper) {
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
        final int code = contentHelper.matchUri(uri);
        if (code != UriMatcher.NO_MATCH) {
            if (code == ContentHelper.uriClearCode) {
                if (DEBUG) {
                    Log.d("delete", "uriClearCode");
                }
                mDB.onUpgrade(getWritableDatabase(), 1, contentHelper.DATABASE_VERSION);
                return 0;
            }
            if (code == ContentHelper.uriSyncCode) {
                throw new IllegalArgumentException(
                        "Can not delete with Sync Uri.");
            }
            boolean syncToNetwork = checkSyncToNetwork(uri);
            final TableInfo tab = contentHelper.getTableFromCode(code & ContentHelper.uriCode);
            if (tab.readonly) {
                throw new IllegalArgumentException("Table " + tab.name
                        + " is readonly.");
            }
            if (isItemCode(code)) {
                if (isItemRowIDCode(code)) {
                    selection = "isDeleted=0 AND ROWID="
                            + uri.getPathSegments().get(2)
                            + (!TextUtils.isEmpty(selection) ? " AND ("
                            + selection + ')' : "");
                } else {
                    selection = "isDeleted=0"
                            + tab.getSelection()
                            + (!TextUtils.isEmpty(selection) ? "(" + selection
                            + ") AND " : "");
                    int i = 0;
                    final String[] old = selectionArgs;
                    final int len = (old == null) ? 0 : old.length;
                    selectionArgs = new String[len + tab.primaryKey.length];
                    for (; i < tab.primaryKey.length; i++) {
                        selectionArgs[i] = uri.getPathSegments().get(i);
                    }
                    if (len > 0) {
                        for (; i < old.length; i++) {
                            selectionArgs[i] = old[i - tab.primaryKey.length];
                        }
                    }
                }
            } else {
                selection = "isDeleted=0"
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection
                        + ')' : "");
            }
            int ret = 0;
            int cascaderet = 0;
            if (tab.cascadeDelete != null) {
                for (CascadeInfo info : tab.cascadeDelete) {

                    final Cursor c = query(contentHelper.getDirUri(tab.name), info.pk,
                            selection, selectionArgs, null);
                    if (c.moveToFirst()) {

                        do {
                            final String[] args = new String[info.pk.length];
                            final StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < info.pk.length; i++) {
                                if (i > 0)
                                    sb.append("AND ");
                                sb.append(info.fk[i]);
                                sb.append("=? ");
                                args[i] = c.getString(i);
                            }
                            cascaderet += delete(
                                    contentHelper.getDirUri(info.table, syncToNetwork),
                                    sb.toString(), args);
                        } while (c.moveToNext());
                    }
                    c.close();
                }
            }
            ContentValues values = new ContentValues(2);
            values.put("isDirty", 1);
            values.put("isDeleted", 1);
            ret = getWritableDatabase().update(tab.name, values,
                    "tempId IS NULL AND " + selection, selectionArgs);
            ret += getWritableDatabase().delete(tab.name,
                    "tempId IS NOT NULL AND " + selection, selectionArgs);
            if (ret > 0) {
                final ContentResolver cr = getContext().getContentResolver();
                cr.notifyChange(uri, null, syncToNetwork);
                for (String n : tab.notifyUris) {
                    cr.notifyChange(Uri.parse(n), null, syncToNetwork);
                }
            }
            return ret + cascaderet;
        }
        throw new IllegalArgumentException("Unknown Uri " + uri);
    }

    @Override
    public String getType(Uri uri) {
        final int code = contentHelper.matchUri(uri);
        if (code != UriMatcher.NO_MATCH) {
            if (code == contentHelper.uriSyncCode) {
                throw new IllegalArgumentException(
                        "There is no type for SYNC Uri: " + uri);
            }
            final TableInfo tab = contentHelper.getTableFromCode(code & ContentHelper.uriCode);
            return (isItemCode(code)) ? tab.ItemMime
                    : tab.DirMime;
        }
        throw new IllegalArgumentException("Unknown Uri " + uri);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        final int code = contentHelper.matchUri(uri);
        if (code != UriMatcher.NO_MATCH) {
            if (code == ContentHelper.uriSyncCode) {
                throw new IllegalArgumentException(
                        "Can not insert with Sync Uri.");
            }
            final TableInfo tab = contentHelper.getTableFromCode(code & ContentHelper.uriCode);
            if (tab.readonly) {
                throw new IllegalArgumentException("Table " + tab.name
                        + " is readonly.");
            }
            if (isItemCode(code)) {
                throw new IllegalArgumentException(
                        "Can not delete with Item type Uri.");
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

            if (rowId > 0) {
                Uri ret_uri = contentHelper.getItemUri(tab.name, syncToNetwork, rowId);
                final ContentResolver cr = getContext().getContentResolver();
                cr.notifyChange(ret_uri, null, syncToNetwork);
                for (String n : tab.notifyUris) {
                    cr.notifyChange(Uri.parse(n), null, syncToNetwork);
                }
                return ret_uri;
            }
        }
        throw new SQLException("Failed to insert row into " + uri);

    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        final int code = contentHelper.matchUri(uri);
        if (code != UriMatcher.NO_MATCH) {
            if (code == ContentHelper.uriSyncCode) {
                return null;
            }
            SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
            final TableInfo tab = contentHelper.getTableFromCode(code & ContentHelper.uriCode);
            builder.setTables(tab.name);
            if (isItemCode(code)) {
                if (isItemRowIDCode(code)) {
                    selection = "isDeleted=0 AND ROWID="
                            + uri.getPathSegments().get(2)
                            + (!TextUtils.isEmpty(selection) ? " AND ("
                            + selection + ')' : "");
                } else {
                    selection = "isDeleted=0"
                            + tab.getSelection()
                            + (!TextUtils.isEmpty(selection) ? " AND ("
                            + selection + ")" : "");
                    int i = 0;
                    final String[] old = selectionArgs;
                    final int len = (old == null) ? 0 : old.length;
                    selectionArgs = new String[len + tab.primaryKey.length];
                    for (; i < tab.primaryKey.length; i++) {
                        selectionArgs[i] = uri.getPathSegments().get(i + 1);
                    }
                    if (len > 0) {
                        for (; i < old.length; i++) {
                            selectionArgs[i] = old[i - tab.primaryKey.length];
                        }
                    }
                }
            } else {
                selection = "isDeleted=0"
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection
                        + ')' : "");
            }
            builder.setProjectionMap(tab.map);

            Cursor cursor = builder.query(getReadableDatabase(), projection,
                    selection, selectionArgs, null, null, sortOrder);
            if (DEBUG) {
                Log.d("Query", builder.buildQuery(projection, selection,
                        selectionArgs, null, null, sortOrder, null));
            }
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
            return cursor;
        }
        throw new IllegalArgumentException("Unknown Uri " + uri);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        final int code = contentHelper.matchUri(uri);
        if (code != UriMatcher.NO_MATCH) {
            if (code == ContentHelper.uriSyncCode) {
                if (DEBUG) {
                    Log.d(TAG, "CP-update-sync: " + uri.toString());
                }
                return (Sync(uri.getPathSegments().get(1), uri
                        .getPathSegments().get(2), selection) ? 1 : 0);
            }
            final TableInfo tab = contentHelper.getTableFromCode(code & ContentHelper.uriCode);
            if (tab.readonly) {
                throw new IllegalArgumentException("Table " + tab.name
                        + " is readonly.");
            }
            if (isItemCode(code)) {
                if (isItemRowIDCode(code)) {
                    selection = "isDeleted=0 AND ROWID="
                            + uri.getPathSegments().get(2)
                            + (!TextUtils.isEmpty(selection) ? " AND ("
                            + selection + ')' : "");
                } else {
                    selection = "isDeleted=0"
                            + tab.getSelection()
                            + (!TextUtils.isEmpty(selection) ? "(" + selection
                            + ") AND " : "");
                    int i = 0;
                    final String[] old = selectionArgs;
                    final int len = (old == null) ? 0 : old.length;
                    selectionArgs = new String[len + tab.primaryKey.length];
                    for (; i < tab.primaryKey.length; i++) {
                        selectionArgs[i] = uri.getPathSegments().get(i);
                    }
                    if (len > 0) {
                        for (; i < old.length; i++) {
                            selectionArgs[i] = old[i - tab.primaryKey.length];
                        }
                    }
                }
            } else {
                selection = "isDeleted=0"
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection
                        + ')' : "");
            }
            boolean syncToNetwork = checkSyncToNetwork(uri);
            values.put("isDirty", 1);
            int ret = getWritableDatabase().update(tab.name, values, selection,
                    selectionArgs);
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
        final String syncToNetworkUri = uri.getQueryParameter(ContentHelper.SYNCTONETWORK);
        return syncToNetworkUri == null || Boolean.parseBoolean(syncToNetworkUri);
    }

    final public SQLiteDatabase getReadableDatabase() {
        return mDB.getReadableDatabase();
    }

    final public SQLiteDatabase getWritableDatabase() {
        return mDB.getWritableDatabase();
    }

    protected boolean Sync(String service, String scope, String params) {
        final Date start = new Date();
        boolean hasError = false;
        if (params == null)
            params = "";
        final SQLiteDatabase db = mDB.getWritableDatabase();
        final ArrayList<TableInfo> notifyTableInfo = new ArrayList<TableInfo>();

        final String download = String.format(contentHelper.DOWNLOAD_SERVICE_URI, service,
                scope, params);
        final String upload = String.format(contentHelper.UPLOAD_SERVICE_URI, service, scope,
                params);
        final String scopeServerBlob = String.format("%s.%s.%s", service,
                scope, _.serverBlob);
        String serverBlob = null;
        Cursor cur = db.query(BlobsTable.NAME,
                new String[]{BlobsTable.C_VALUE}, BlobsTable.C_NAME + "=?",
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
            boolean nochanges = false;
            if (serverBlob != null) {
                nochanges = !contentHelper.hasDirtTable(db, scope);
            }
            boolean resolve = false;
            final Metadata meta = new Metadata();
            final HashMap<String, Object> vals = new HashMap<String, Object>();
            final ContentValues cv = new ContentValues(2);
            JsonFactory jsonFactory = new JsonFactory();
            JsonToken current = null;
            String name = null;
            boolean moreChanges = false;
            boolean forceMoreChanges = false;
            do {
                final int requestMethod;
                final String serviceRequestUrl;
                final ContentProducer contentProducer;

                if (serverBlob != null) {
                    requestMethod = HTTP_POST;
                    if (nochanges) {
                        serviceRequestUrl = download;
                    } else {
                        serviceRequestUrl = upload;
                        forceMoreChanges = true;
                    }
                    contentProducer = new SyncContentProducer(jsonFactory, db,
                            scope, serverBlob, !nochanges, notifyTableInfo, contentHelper);
                    nochanges = true;
                } else {
                    requestMethod = HTTP_GET;
                    serviceRequestUrl = download;
                    contentProducer = null;

                }
                if (moreChanges) {
                    db.beginTransaction();
                }

                Result result = executeRequest(requestMethod, serviceRequestUrl,
                        contentProducer);
                if (result.getStatus() == HttpStatus.SC_OK) {
                    final JsonParser jp = jsonFactory.createParser(result
                            .getInputStream());

                    jp.nextToken(); // skip ("START_OBJECT(d) expected");
                    jp.nextToken(); // skip ("FIELD_NAME(d) expected");
                    if (jp.nextToken() != JsonToken.START_OBJECT)
                        throw new Exception("START_OBJECT(d - object) expected");
                    while (jp.nextToken() != JsonToken.END_OBJECT) {
                        name = jp.getCurrentName();
                        if (_.__sync.equals(name)) {
                            current = jp.nextToken();
                            while (jp.nextToken() != JsonToken.END_OBJECT) {
                                name = jp.getCurrentName();
                                current = jp.nextToken();
                                if (_.serverBlob.equals(name)) {
                                    serverBlob = jp.getText();
                                } else if (_.moreChangesAvailable.equals(name)) {
                                    moreChanges = jp.getBooleanValue()
                                            || forceMoreChanges;
                                    forceMoreChanges = false;
                                } else if (_.resolveConflicts.equals(name)) {
                                    resolve = jp.getBooleanValue();
                                }
                            }
                        } else if (_.results.equals(name)) {
                            if (jp.nextToken() != JsonToken.START_ARRAY)
                                throw new Exception(
                                        "START_ARRAY(results) expected");
                            while (jp.nextToken() != JsonToken.END_ARRAY) {
                                meta.isDeleted = false;
                                meta.tempId = null;
                                vals.clear();
                                while (jp.nextToken() != JsonToken.END_OBJECT) {
                                    name = jp.getCurrentName();
                                    current = jp.nextToken();
                                    if (current == JsonToken.VALUE_STRING) {
                                        vals.put(name, jp.getText());
                                    } else if (current == JsonToken.VALUE_NUMBER_INT) {
                                        vals.put(name, jp.getLongValue());
                                    } else if (current == JsonToken.VALUE_NUMBER_FLOAT) {
                                        vals.put(name, jp.getDoubleValue());
                                    } else if (current == JsonToken.VALUE_FALSE) {
                                        vals.put(name, 0L);
                                    } else if (current == JsonToken.VALUE_TRUE) {
                                        vals.put(name, 1L);
                                    } else if (current == JsonToken.VALUE_NULL) {
                                        vals.put(name, null);
                                    } else {
                                        if (current == JsonToken.START_OBJECT) {
                                            if (_.__metadata.equals(name)) {
                                                while (jp.nextToken() != JsonToken.END_OBJECT) {
                                                    name = jp.getCurrentName();
                                                    jp.nextToken();
                                                    if (_.uri.equals(name)) {
                                                        meta.uri = jp.getText();
                                                    } else if (_.type
                                                            .equals(name)) {
                                                        meta.type = jp
                                                                .getText();
                                                    } else if (_.isDeleted
                                                            .equals(name)) {
                                                        meta.isDeleted = jp
                                                                .getBooleanValue();
                                                    } else if (_.tempId
                                                            .equals(name)) {
                                                        meta.tempId = jp
                                                                .getText();
                                                    }
                                                }
                                            } else if (_.__syncConflict
                                                    .equals(name)) {
                                                while (jp.nextToken() != JsonToken.END_OBJECT) {
                                                    name = jp.getCurrentName();
                                                    jp.nextToken();
                                                    if (_.isResolved
                                                            .equals(name)) {
                                                    } else if (_.conflictResolution
                                                            .equals(name)) {
                                                    } else if (_.conflictingChange
                                                            .equals(name)) {
                                                        while (jp.nextToken() != JsonToken.END_OBJECT) {
                                                            name = jp
                                                                    .getCurrentName();
                                                            current = jp
                                                                    .nextToken();
                                                            if (current == JsonToken.START_OBJECT) {
                                                                if (_.__metadata
                                                                        .equals(name)) {
                                                                    while (jp
                                                                            .nextToken() != JsonToken.END_OBJECT) {

                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                // resolve conf

                                            } else if (_.__syncError
                                                    .equals(name)) {
                                                while (jp.nextToken() != JsonToken.END_OBJECT) {
                                                    name = jp.getCurrentName();
                                                    jp.nextToken();
                                                }
                                            }
                                        }
                                    }
                                }
                                TableInfo tab = contentHelper.getTableFromType(meta.type);
                                if (meta.isDeleted) {
                                    tab.DeleteWithUri(meta.uri, db);
                                } else {
                                    tab.SyncJSON(vals, meta, db);
                                }
                                if (!notifyTableInfo.contains(tab))
                                    notifyTableInfo.add(tab);
                            }
                        }
                    }
                    jp.close();
                    if (!hasError) {
                        cv.clear();
                        cv.put(BlobsTable.C_NAME, scopeServerBlob);
                        cv.put(BlobsTable.C_VALUE, serverBlob);
                        cv.put(BlobsTable.C_DATE, Calendar.getInstance()
                                .getTimeInMillis());
                        cv.put(BlobsTable.C_STATE, 0);
                        db.replace(BlobsTable.NAME, null, cv);
                        db.setTransactionSuccessful();
                        db.endTransaction();
                        if (DEBUG) {
                            Log.d(TAG, "CP-Sync: commit changes");
                        }
                        final ContentResolver cr = getContext()
                                .getContentResolver();
                        for (TableInfo t : notifyTableInfo) {
                            final Uri nu = contentHelper.getDirUri(t.name, false);
                            cr.notifyChange(nu, null,
                                    false);
                            // false - do not force sync cause we are in sync
                            if (DEBUG) {
                                Log.d(TAG, "CP-Sync: notifyChange table: " + t.name + ", uri: " + nu);
                            }

                            for (String n : t.notifyUris) {
                                cr.notifyChange(Uri.parse(n), null, false);
                                if (DEBUG) {
                                    Log.d(TAG, "+uri: " + n);
                                }
                            }
                        }
                        notifyTableInfo.clear();
                    }
                } else {
                    if (DEBUG) {
                        Log.e(TAG, "Server error in fetching remote contacts: "
                                + result.getStatus());
                    }
                    hasError = true;
                    break;
                }
            } while (moreChanges);
        } catch (final ConnectTimeoutException e) {
            hasError = true;
            if (DEBUG) {
                Log.e(TAG, "ConnectTimeoutException", e);
            }
        } catch (final IOException e) {
            hasError = true;
            if (DEBUG) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        } catch (final ParseException e) {
            hasError = true;
            if (DEBUG) {
                Log.e(TAG, "ParseException", e);
            }
        } catch (final Exception e) {
            hasError = true;
            if (DEBUG) {
                Log.e(TAG, "ParseException", e);
            }
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
        if (DEBUG) {
            Helpers.LogInfo(start);
        }
        return !hasError;
    }

    public Result executeRequest(int requestMethod, String serviceRequestUrl,
                                 ContentProducer contentProducer) throws Exception {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.FROYO) {
            final HttpRequestBase request;
            switch (requestMethod) {
                case HTTP_POST:
                    request = new HttpPost(serviceRequestUrl);
                    ((HttpPost) request).setEntity(new EntityTemplate(
                            contentProducer));
                    break;
                case HTTP_GET:
                    request = new HttpGet(serviceRequestUrl);
                    break;
                default:
                    request = null;
            }
            HttpResponse response = Instance.execute(request);
            return new Result(response.getEntity().getContent(), response
                    .getStatusLine().getStatusCode());
        } else {
            final HttpURLConnection request = (HttpURLConnection) new URL(
                    serviceRequestUrl).openConnection();
            request.addRequestProperty("Cache-Control", "no-store,no-cache");
            request.addRequestProperty("Pragma", "no-cache");
            request.addRequestProperty("Accept-Encoding", "gzip");
            request.addRequestProperty("Accept", "application/json");
            request.addRequestProperty("Content-type",
                    "application/json; charset=utf-8");
            switch (requestMethod) {
                case HTTP_POST:
                    request.setDoOutput(true);
                    request.setRequestMethod("POST");
                    contentProducer.writeTo(request.getOutputStream());
                    break;
                case HTTP_GET:
                    break;
            }
            InputStream stream = request.getInputStream();
            return new Result(
                    "gzip".equals(request.getContentEncoding()) ? new GZIPInputStream(
                            stream) : stream, request.getResponseCode());
        }
    }

    public void onDowngradeDatabase(SQLiteDatabase db, int oldVersion, int newVersion) {
        mDB.superOnDowngrade(db, oldVersion, newVersion);
    }

    public void onCreateDataBase(SQLiteDatabase db) {
        try {
            for (TableInfo table : contentHelper.getAllTables()) {
                String create = table.CreateStatement();
                db.execSQL(create);
            }
            db.execSQL(String
                    .format("CREATE TABLE [%s] ([%s] VARCHAR NOT NULL, [%s] VARCHAR, [%s] LONG NOT NULL, [%s] INT NOT NULL, PRIMARY KEY([%s]))",
                            BlobsTable.NAME, BlobsTable.C_NAME,
                            BlobsTable.C_VALUE, BlobsTable.C_DATE,
                            BlobsTable.C_STATE, BlobsTable.C_NAME));
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(BaseContentProvider.TAG,
                        "BaseContentProvider-onCreateDataBase " + e.toString());
            }
        }
    }

    public void onUpgradeDatabase(SQLiteDatabase db, int oldVersion, int newVersion) {
        Cursor c = db.rawQuery("select 'drop table ' || name || ';' from sqlite_master where type = 'table'", null);
        if (c.moveToFirst()) {
            do {
                db.execSQL(c.getString(0));
            } while (c.moveToNext());
        }
        onCreateDataBase(db);
    }

    static class SyncContentProducer implements ContentProducer {
        final SQLiteDatabase db;
        final String scope;
        final String serverBlob;
        final JsonFactory jsonFactory;
        final boolean upload;
        final ArrayList<TableInfo> notifyTableInfo;
        final ContentHelper ch;

        public SyncContentProducer(JsonFactory jsonFactory, SQLiteDatabase db,
                                   String scope, String serverBlob, boolean upload,
                                   ArrayList<TableInfo> notifyTableInfo, ContentHelper ch) {
            this.db = db;
            this.scope = scope;
            this.serverBlob = serverBlob;
            this.jsonFactory = jsonFactory;
            this.upload = upload;
            this.notifyTableInfo = notifyTableInfo;
            this.ch = ch;
        }

        @Override
        public void writeTo(OutputStream outstream) throws IOException {
            JsonGenerator gen = jsonFactory.createGenerator(outstream,
                    JsonEncoding.UTF8);
            gen.writeStartObject();
            gen.writeObjectFieldStart(_.d);
            gen.writeObjectFieldStart(_.__sync);
            gen.writeBooleanField(_.moreChangesAvailable, false);
            gen.writeStringField(_.serverBlob, serverBlob);
            gen.writeEndObject(); // sync
            gen.writeArrayFieldStart(_.results);
            if (upload) {
                for (TableInfo tab : ch.getAllTables()) {
                    if (tab.scope.toLowerCase().equals(scope.toLowerCase()))
                        tab.GetChanges(db, gen, notifyTableInfo);
                }
            }
            gen.writeEndArray();// result
            gen.writeEndObject(); // d
            gen.writeEndObject();
            gen.close();
        }
    }

    protected static class Result {
        private InputStream inputStream;
        private int status;

        public Result(InputStream inputStream, int status) {
            this.inputStream = inputStream;
            this.status = status;
        }

        public InputStream getInputStream() {
            return inputStream;
        }

        public int getStatus() {
            return status;
        }

    }

    private final static class GzipDecompressingEntity extends
            HttpEntityWrapper {

        public GzipDecompressingEntity(final HttpEntity entity) {
            super(entity);
        }

        @Override
        public InputStream getContent() throws IOException,
                IllegalStateException {
            InputStream wrappedin = wrappedEntity.getContent();
            return new GZIPInputStream(wrappedin);
        }

        @Override
        public long getContentLength() {
            return -1;
        }

    }

    final class OpenHelper extends SQLiteOpenHelper {

        public OpenHelper() {
            super(getContext(), contentHelper.DATABASE_NAME, null,
                    contentHelper.DATABASE_VERSION);
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
