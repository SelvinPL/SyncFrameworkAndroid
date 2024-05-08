package pl.selvin.android.listsyncsample.provider;

import androidx.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import pl.selvin.android.syncframework.content.BaseContentProvider;

public class RequestExecutor implements pl.selvin.android.syncframework.content.RequestExecutor {
	private final OkHttpClient client = new OkHttpClient();

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
			return new Result(new BufferedInputStream(body.source().inputStream(), 4 * 1024 * 1024), response.code(), error) {
				@Override
				public void close() {
					body.close();
				}
			};
		}
		throw new RuntimeException("Response body is null");
	}
}
