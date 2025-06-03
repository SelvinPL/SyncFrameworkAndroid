package pl.selvin.android.listsyncsample.network;

import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import pl.selvin.android.listsyncsample.BuildConfig;

public class HttpClient {
	public static final OkHttpClient DEFAULT;

	static {
		final int connectTimeout = 3;
		final int timeout = 15;
		final String USER_AGENT = String.format("%s(%s)(%s)(%s)", BuildConfig.APPLICATION_ID, BuildConfig.BUILD_TYPE, BuildConfig.FLAVOR, BuildConfig.VERSION_NAME);
		final OkHttpClient.Builder builder = new OkHttpClient.Builder().addInterceptor(
				chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build()));
		DEFAULT = builder
				.connectTimeout(connectTimeout, TimeUnit.SECONDS)
				.callTimeout(timeout, TimeUnit.MINUTES)
				.readTimeout(timeout, TimeUnit.MINUTES)
				.writeTimeout(timeout, TimeUnit.MINUTES)
				.connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
				.build();
	}
}
