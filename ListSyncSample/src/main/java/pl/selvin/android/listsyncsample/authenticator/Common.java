package pl.selvin.android.listsyncsample.authenticator;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import pl.selvin.android.listsyncsample.Constants;

public class Common {
    private static String mAuthtoken;

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

    public static boolean authenticate(String username, Handler handler, final Context context) {
        final OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(Constants.SERVICE_URI + "/Login.ashx?username=" + username)
                .addHeader("Accept", "application/json").addHeader("Content-type", "application/json; charset=utf-8").build();
        try {
            final Response response = client.newCall(request).execute();
            mAuthtoken = response.body().string();
            Log.d("ListSync", mAuthtoken);
            sendResult(true, handler, context);
            return true;

        } catch (Exception e) {
            Log.e("Auth", e.getLocalizedMessage());
        }
        sendResult(false, handler, context);
        return false;
    }

    public static String getAuthtoken() {
        return mAuthtoken;
    }

    public static Thread performOnBackgroundThread(final Runnable runnable) {
        final Thread t = new Thread() {
            @Override
            public void run() {
                runnable.run();
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

    public interface IAuthenticationResult {
        void onAuthenticationResult(boolean result);
    }
}
