/***
 * Copyright (c) 2014 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package pl.selvin.android.listsyncsample.provider;

import android.database.Cursor;
import android.net.Uri;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import pl.selvin.android.listsyncsample.Setup;
import pl.selvin.android.syncframework.content.BaseContentProvider;
import pl.selvin.android.syncframework.content.ContentHelper;
import pl.selvin.android.syncframework.content.RequestExecutor;

public class ListProvider extends BaseContentProvider {
    private final static ContentHelper helperInstance = ContentHelper.getInstance(Setup.class, null);

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
                        public void writeTo(BufferedSink sink) throws IOException {
                            syncContentProducer.writeTo(sink.outputStream());
                        }
                    });
                    break;
            }
            final Response response = client.newCall(requestBuilder.build()).execute();

            final ResponseBody body = response.body();
            String error = null;
            try {
                error = response.isSuccessful() ? null : response.body().string();
            } catch (Exception ignore) {

            }
            return new Result(response.body().source().inputStream(), response.code(), error) {
                @Override
                public void close() {
                    body.close();
                }
            };
        }
    };

    public ListProvider() {
        super(getHelper(), executor);
    }

    public static synchronized ContentHelper getHelper() {
        return helperInstance;
    }
    // we don't need implementation ... base class do everything on it's own
    // this is just class which we are pointing in xml files as Provider
    // since one day i'll move BaseContentProvider to library

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return super
                .query(uri, projection, selection, selectionArgs, sortOrder);
    }
}
