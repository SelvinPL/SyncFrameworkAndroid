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

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.IOException;

import okio.BufferedSink;
import pl.selvin.android.listsyncsample.Setup;
import pl.selvin.android.syncframework.content.BaseContentProvider;
import pl.selvin.android.syncframework.content.ContentHelper;
import pl.selvin.android.syncframework.content.RequestExecutor;

public class ListProvider extends BaseContentProvider {
    private final static ContentHelper helperInstance = ContentHelper.getInstance(Setup.class);

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
            return new Result(response.body().source().inputStream(), response.code()) {
                @Override
                public void close() {
                    try {
                        inputBuffer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
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
