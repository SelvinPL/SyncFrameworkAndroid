package pl.selvin.android.listsyncsample.authenticator;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import pl.selvin.android.listsyncsample.Constants;

public class NetworkOperations {

	public static void authenticateAsync(String username, String password, AuthenticatorActivity.AuthenticateCallback callback) {
		createAuthCall(username, password).enqueue(new Callback() {
			@Override
			public void onFailure(@NonNull Call call, @NonNull IOException e) {
				callback.onResult(createAuthResultFromException(e));
			}

			@Override
			public void onResponse(@NonNull Call call, @NonNull Response response) {
				try {
					callback.onResult(parseAuthResult(response));
				} catch (Exception e) {
					callback.onResult(createAuthResultFromException(e));
				}
			}
		});
	}

	private static Call createAuthCall(final String username, final String password) {
		final OkHttpClient client = new OkHttpClient();
		Request request = new Request.Builder().url(Constants.SERVICE_URI + "/Login.ashx?username=" + username)
				.addHeader("Accept", "application/json").addHeader("Content-type", "application/json; charset=utf-8").build();
		return client.newCall(request);
	}

	@NonNull
	private static Bundle parseAuthResult(final Response response) {
		try {
			final Bundle bundle;
			if (response.isSuccessful()) {
				final ResponseBody body = response.body();
				if (body != null) {
					bundle = createAuthResultSucceeded(body.string());
				} else {
					bundle = createAuthResultFailed("Response has no body");
				}
			} else {
				bundle = createAuthResultFailed("HTTP error: " + response.code());
				Log.d("status", "" + response.code());
			}
			return bundle;
		} catch (Exception e) {
			return createAuthResultFromException(e);
		}
	}

	private static Bundle createAuthResultFromException(Exception e) {
		final Bundle bundle = new Bundle();
		bundle.putBoolean(LoginResponse.SUCCESS, false);
		bundle.putString(LoginResponse.ERROR, "IOException: " + e.getMessage());
		e.printStackTrace();
		return bundle;
	}

	private static Bundle createAuthResultFailed(String message) {
		final Bundle bundle = new Bundle();
		bundle.putBoolean(LoginResponse.SUCCESS, false);
		bundle.putString(LoginResponse.ERROR, message);
		return bundle;
	}

	private static Bundle createAuthResultSucceeded(String userID) {
		final Bundle bundle = new Bundle();
		bundle.putBoolean(LoginResponse.SUCCESS, true);
		bundle.putString(LoginResponse.USER_ID, userID);
		return bundle;
	}

	public static Bundle authenticate(String username, String password) {
		try {
			return parseAuthResult(createAuthCall(username, password).execute());
		} catch (IOException e) {
			return createAuthResultFromException(e);
		}
	}

	public interface LoginResponse {
		String SUCCESS = "SUCCESS";
		String USER_ID = "USER_ID";
		String ERROR = "ERROR";
	}
}
