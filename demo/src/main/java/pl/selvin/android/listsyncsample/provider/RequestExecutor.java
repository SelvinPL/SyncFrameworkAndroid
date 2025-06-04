package pl.selvin.android.listsyncsample.provider;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.authenticator.NetworkOperations;
import pl.selvin.android.listsyncsample.network.HttpClient;
import pl.selvin.android.syncframework.content.BaseContentProvider;

public class RequestExecutor implements pl.selvin.android.syncframework.content.RequestExecutor {
	public final static String ACCOUNT_PARAMETER = "ACCOUNT_PARAMETER";

	@Override
	@NonNull
	public Result execute(@NonNull Context context, @Nullable final BaseContentProvider.ISyncContentProducer syncContentProducer, @NonNull Bundle parameters) throws IOException, AuthenticatorException {
		final Account account = BundleCompat.getParcelable(parameters, RequestExecutor.ACCOUNT_PARAMETER, Account.class);
		int requestMethod = parameters.getInt(RequestExecutor.REQUEST_METHOD_PARAMETER);
		final String scope = parameters.getString(RequestExecutor.SCOPE_PARAMETER);
		final String requestType = parameters.getString(RequestExecutor.REQUEST_TYPE_PARAMETER);
		final AccountManager accountManager = AccountManager.get(context);
		final String userid = accountManager.getUserData(account, NetworkOperations.LoginResponse.USER_ID);
		final String serviceRequestUrl = String.format("%s/DefaultScopeSyncService.svc/%s/%s?userid=%s", Constants.SERVICE_URI, scope, requestType, userid);
		final Request.Builder requestBuilder = new Request.Builder().url(serviceRequestUrl)
				.addHeader("Cache-Control", "no-store,no-cache")
				.addHeader("Pragma", "no-cache")
				.addHeader("Accept", "application/json");

		if (requestMethod == RequestExecutor.POST && syncContentProducer != null) {
			requestBuilder.post(new RequestBody() {
				@Override
				public MediaType contentType() {
					return MediaType.parse("application/json; charset=utf-8");
				}

				@Override
				public void writeTo(@NonNull BufferedSink sink) throws IOException {
					syncContentProducer.writeTo(sink);
				}
			});
		}
		final Response response = HttpClient.DEFAULT.newCall(requestBuilder.build()).execute();
		final ResponseBody body = response.body();
		if (body != null) {
			final String error;
			try {
				error = response.isSuccessful() ? null : body.string();
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
			return new Result(body.source(), response.code(), error);
		}
		response.close();
		throw new RuntimeException("Response body is null");
	}
}
