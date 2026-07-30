package pl.selvin.android.listsyncsample.network;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import pl.selvin.android.listsyncsample.BuildConfig;

public class HttpClient {
	public static final OkHttpClient DEFAULT_OK_HTTP_CLIENT;

	static {
		final int connectTimeout = 15;
		final int timeout = 45;
		final String USER_AGENT = String.format("%s(%s)(%s)(%s)", BuildConfig.APPLICATION_ID, BuildConfig.BUILD_TYPE, BuildConfig.FLAVOR, BuildConfig.VERSION_NAME);
		final OkHttpClient.Builder builder = new OkHttpClient.Builder().addInterceptor(
				chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build()));
		//noinspection ConstantConditions
		if (BuildConfig.LOGGING_INTERCEPTOR != null)
			builder.addNetworkInterceptor(BuildConfig.LOGGING_INTERCEPTOR);
		DEFAULT_OK_HTTP_CLIENT = builder
				.retryOnConnectionFailure(false)
				.connectTimeout(connectTimeout, TimeUnit.SECONDS)
				.callTimeout(timeout, TimeUnit.MINUTES)
				.readTimeout(timeout, TimeUnit.MINUTES)
				.writeTimeout(timeout, TimeUnit.MINUTES)
				.build();
	}
}
