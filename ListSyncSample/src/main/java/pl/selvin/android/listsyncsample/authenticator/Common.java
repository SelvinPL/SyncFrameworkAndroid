package pl.selvin.android.listsyncsample.authenticator;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import pl.selvin.android.listsyncsample.Constants;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

public class Common {
	public static void sendResult(final Boolean result, final Handler handler,
			final Context context) {
		if (handler == null || context == null) {
			return;
		}
		handler.post(new Runnable() {
			public void run() {
				((IAuthenticationResult) context)
						.onAuthenticationResult(result);
			}
		});
	}

	public interface IAuthenticationResult {
		void onAuthenticationResult(boolean result);
	}

	public static boolean authenticate(String username, Handler handler,
			final Context context) {
		HttpRequestBase request = new HttpGet(Constants.SERVICE_URI
				+ "/Login.ashx?username=" + username);
		request.setHeader("Accept", "application/json");
		request.setHeader("Content-type", "application/json; charset=utf-8");
		DefaultHttpClient httpClient = new DefaultHttpClient();
		try {
			HttpResponse response = httpClient.execute(request);
			String bufstring = EntityUtils.toString(response.getEntity(),
					"UTF-8");
			Log.d("ListSync", bufstring);
			mAuthtoken = bufstring;
			sendResult(true, handler, context);
			return true;

		} catch (Exception e) {
			Log.e("Auth", e.getLocalizedMessage());
		}
		sendResult(false, handler, context);
		return false;
	}

	private static String mAuthtoken;

	public static String getAuthtoken() {
		return mAuthtoken;
	}

	public static Thread performOnBackgroundThread(final Runnable runnable) {
		final Thread t = new Thread() {
			@Override
			public void run() {
				try {
					runnable.run();
				} finally {

				}
			}
		};
		t.start();
		return t;
	}

	public static Thread attemptAuth(final String username,
			final Handler handler, final Context context) {
		final Runnable runnable = new Runnable() {
			public void run() {
				authenticate(username, handler, context);
			}
		};
		return performOnBackgroundThread(runnable);
	}
}
