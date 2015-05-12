/**
 * Copyright (c) 2015 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package pl.selvin.android.syncframework.content;

import android.os.Build;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
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
import java.util.zip.GZIPInputStream;

abstract class RequestExecutor {

    static RequestExecutor instance;

    static {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.FROYO)
            instance = new RequestExecutorAppache();
        else
            instance = new RequestExecutorURL();
    }

    public static RequestExecutor getInstance() {
        return instance;
    }

    abstract public BaseContentProvider.Result execute(int requestMethod, String serviceRequestUrl,
                                                       final BaseContentProvider
                                                               .SyncContentProducer
                                                               syncContentProducer)
            throws IOException;

    @SuppressWarnings("deprecation")
    static class RequestExecutorAppache extends RequestExecutor {
        private final static DefaultHttpClient Instance;

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

        @Override
        public BaseContentProvider.Result execute(int requestMethod, String serviceRequestUrl,
                                                  final BaseContentProvider.SyncContentProducer
                                                          syncContentProducer)
                throws IOException {
            final HttpRequestBase request;
            switch (requestMethod) {
                case BaseContentProvider.HTTP_POST:
                    request = new HttpPost(serviceRequestUrl);
                    ((HttpPost) request).setEntity(new EntityTemplate(
                            new ContentProducer() {
                                @Override
                                public void writeTo(OutputStream outputStream) throws IOException {
                                    syncContentProducer.writeTo(outputStream);
                                }
                            }));
                    break;
                case BaseContentProvider.HTTP_GET:
                    request = new HttpGet(serviceRequestUrl);
                    break;
                default:
                    request = null;
            }
            HttpResponse response = Instance.execute(request);
            return new BaseContentProvider.Result(response.getEntity().getContent(), response
                    .getStatusLine().getStatusCode());
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
    }

    private static class RequestExecutorURL extends RequestExecutor {
        @Override
        public BaseContentProvider.Result execute(int requestMethod, String serviceRequestUrl,
                                                  BaseContentProvider.SyncContentProducer
                                                          syncContentProducer)
                throws IOException {
            final HttpURLConnection request = (HttpURLConnection) new URL(
                    serviceRequestUrl).openConnection();
            request.addRequestProperty("Cache-Control", "no-store,no-cache");
            request.addRequestProperty("Pragma", "no-cache");
            request.addRequestProperty("Accept-Encoding", "gzip");
            request.addRequestProperty("Accept", "application/json");
            request.addRequestProperty("Content-type",
                    "application/json; charset=utf-8");
            request.setUseCaches(false);
            switch (requestMethod) {
                case BaseContentProvider.HTTP_POST:
                    request.setDoOutput(true);
                    request.setRequestMethod("POST");
                    syncContentProducer.writeTo(request.getOutputStream());
                    break;
                case BaseContentProvider.HTTP_GET:
                    break;
            }
            final InputStream stream = request.getInputStream();
            return new BaseContentProvider.Result(
                    "gzip".equals(request.getContentEncoding()) ? new GZIPInputStream(
                            stream) : stream, request.getResponseCode());
        }
    }
}
