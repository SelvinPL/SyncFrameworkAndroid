/*
 * Copyright (c) 2014-2016 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package pl.selvin.android.listsyncsample.provider;

import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.HashMap;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import pl.selvin.android.autocontentprovider.content.ContentHelper;
import pl.selvin.android.autocontentprovider.db.TableInfo;
import pl.selvin.android.autocontentprovider.log.Logger;
import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.HelperFactoryProvider;
import pl.selvin.android.listsyncsample.provider.Database.Tag;
import pl.selvin.android.listsyncsample.provider.Database.TagItemMapping;
import pl.selvin.android.syncframework.content.BaseContentProvider;
import pl.selvin.android.syncframework.content.RequestExecutor;
import pl.selvin.android.syncframework.content.SYNC;
import pl.selvin.android.syncframework.content.SyncContentHelper;

public class ListProvider extends BaseContentProvider {
    private final static SyncContentHelper helperInstance = SyncContentHelper.getInstance(Database.class, Constants.AUTHORITY, "list_db", 19, Constants.SERVICE_URI);
    private final HashMap<String, String> TAG_ITEM_MAPPING_WITH_NAMES = new HashMap<>();
    private final static int TAG_ITEM_MAPPING_WITH_NAMES_MATCH = 1;
    private final static int TAG_NOT_USED_MATCH = 2;
    private final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    private final static RequestExecutor executor = new RequestExecutor() {
        private final OkHttpClient client = new OkHttpClient();

        @Override
        public Result execute(int requestMethod, String serviceRequestUrl, final ISyncContentProducer syncContentProducer) throws IOException {
            final Request.Builder requestBuilder = new Request.Builder().url(serviceRequestUrl)
                    .addHeader("Cache-Control", "no-store,no-cache").addHeader("Pragma", "no-cache").addHeader("Accept", "application/json");
            switch (requestMethod) {
                case HTTP_POST:
                    requestBuilder.post(new RequestBody() {
                        @Override
                        public MediaType contentType() {
                            return MediaType.parse("application/json; charset=utf-8");
                        }

                        @Override
                        public void writeTo(@NonNull BufferedSink sink) throws IOException {
                            syncContentProducer.writeTo(sink.outputStream());
                        }
                    });
                    break;
            }
            final Response response = client.newCall(requestBuilder.build()).execute();

            final ResponseBody body = response.body();
            if (body != null) {
                final String error;
                try {
                    error = response.isSuccessful() ? null : body.string();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                return new Result(new BufferedInputStream(body.source().inputStream(), 4 * 1024 * 1024), response.code(), error) {
                    @Override
                    public void close() {
                        body.close();
                    }
                };
            }
            throw new RuntimeException("Response body is null");
        }
    };

    public ListProvider() {
        super(getHelper(), new Logger("SYNC"), new HelperFactoryProvider(), executor);
        MATCHER.addURI(Constants.AUTHORITY, TagItemMapping.TagItemMappingWithNames, TAG_ITEM_MAPPING_WITH_NAMES_MATCH);
        MATCHER.addURI(Constants.AUTHORITY, Tag.TagNotUsed, TAG_NOT_USED_MATCH);
        TableInfo tableInfo = contentHelper.getTableFromType(String.format("%s.%s", TagItemMapping.SCOPE, TagItemMapping.TABLE_NAME));
        TAG_ITEM_MAPPING_WITH_NAMES.putAll(tableInfo.map);
        tableInfo = contentHelper.getTableFromType(String.format("%s.%s", Tag.SCOPE, Tag.TABLE_NAME));
        for (final String key : tableInfo.map.keySet()) {
            if (!key.equals(BaseColumns._ID)) {
                TAG_ITEM_MAPPING_WITH_NAMES.put(key, tableInfo.map.get(key));
            }
        }
    }

    @Override
    public String getType(@NonNull Uri uri) {
        switch (MATCHER.match(uri)) {
            case TAG_ITEM_MAPPING_WITH_NAMES_MATCH:
                return super.getType(contentHelper.getDirUri(TagItemMapping.TABLE_NAME));
            case TAG_NOT_USED_MATCH:
                return super.getType(contentHelper.getDirUri(Tag.TABLE_NAME));
            default:
                return super.getType(uri);
        }
    }

    public static synchronized SyncContentHelper getHelper() {
        return helperInstance;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        final HashMap<String, String> projectionMap;
        String limit = uri.getQueryParameter(ContentHelper.PARAMETER_LIMIT);
        switch (MATCHER.match(uri)) {
            case TAG_ITEM_MAPPING_WITH_NAMES_MATCH:
                builder.setTables(TagItemMapping.TABLE_NAME + "  INNER JOIN "
                        + Tag.TABLE_NAME + " ON "
                        + Tag.ID + "=" + TagItemMapping.TAG_ID);
                projectionMap = TAG_ITEM_MAPPING_WITH_NAMES;
                builder.appendWhere(TagItemMapping.TABLE_NAME + "." + SYNC.isDeleted + "=0");
                break;
            case TAG_NOT_USED_MATCH:
                final Cursor cursor = super.query(contentHelper.getDirUri(Tag.TABLE_NAME), projection,
                        "(NOT EXISTS(SELECT 1 FROM " + TagItemMapping.TABLE_NAME + " WHERE " + TagItemMapping.TAG_ID
                                + "=" + Tag.ID + " AND " + TagItemMapping.ITEM_ID + "=? AND [" +
                                TagItemMapping.TABLE_NAME + "]." + SYNC.isDeleted + "=0))",
                        selectionArgs, sortOrder);
                if (cursor != null && getContext() != null) {
                    cursor.setNotificationUri(getContext().getContentResolver(), uri);
                }
                return cursor;
            default:
                return super.query(uri, projection, selection, selectionArgs, sortOrder);
        }
        builder.setProjectionMap(projectionMap);
        logger.LogQuery(clazz, uri, builder, projection, selection, selectionArgs, null, null, sortOrder, limit);
        String q = builder.buildQuery(projection, selection, null, null, sortOrder, limit);

        final Cursor cursor = getReadableDatabase().query(q, selectionArgs);
        if (cursor != null && getContext() != null) {
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return cursor;
    }
}
