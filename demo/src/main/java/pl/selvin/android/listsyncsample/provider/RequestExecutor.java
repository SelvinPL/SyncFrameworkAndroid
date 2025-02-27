package pl.selvin.android.listsyncsample.provider;

import androidx.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.CacheControl;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import pl.selvin.android.listsyncsample.BuildConfig;
import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.syncframework.content.BaseContentProvider;

public class RequestExecutor implements pl.selvin.android.syncframework.content.RequestExecutor {
	private final OkHttpClient client;

	public RequestExecutor() {
		final int connectTimeout = 3;
		final int timeout = 15;
		final String USER_AGENT = String.format("%s(%s)(%s)(%s)", BuildConfig.APPLICATION_ID, BuildConfig.BUILD_TYPE, BuildConfig.FLAVOR, BuildConfig.VERSION_NAME);
		final OkHttpClient.Builder builder = new OkHttpClient.Builder().addInterceptor(
				chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build()));
		client = builder
				.connectTimeout(connectTimeout, TimeUnit.SECONDS)
				.callTimeout(timeout, TimeUnit.MINUTES)
				.readTimeout(timeout, TimeUnit.MINUTES)
				.writeTimeout(timeout, TimeUnit.MINUTES)
				.connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
				.build();
	}

	@Override
	public Result execute(int requestMethod, String serviceRequestUrl, final BaseContentProvider.ISyncContentProducer syncContentProducer) throws IOException {
		final Request.Builder requestBuilder = new Request.Builder().url(serviceRequestUrl)
				.addHeader("Cache-Control", "no-store,no-cache").addHeader("Pragma", "no-cache").addHeader("Accept", "application/json");
		if (requestMethod == HTTP_POST) {
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
			return new Result(body.source().inputStream(), response.code(), error) {
				@Override
				public void close() {
					response.close();
				}
			};
		}
		response.close();
		throw new RuntimeException("Response body is null");
	}

	@Override
	public void doPing() {
		try {
			final Request.Builder requestBuilder = new Request.Builder().url(Constants.SERVICE_URI + "DefaultScopeSyncService.svc/$syncscopes")
					.method("HEAD", null).cacheControl(new CacheControl.Builder().noCache().noStore().build());
			final Response response = client.newCall(requestBuilder.build()).execute();
			response.close();
		} catch (Exception ignore) {
		}
	}
}
