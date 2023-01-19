package pl.selvin.android.listsyncsample.authenticator;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.provider.ListProvider;
import pl.selvin.android.listsyncsample.utils.StaticHelpers;

class Authenticator extends AbstractAccountAuthenticator {

    private final static String TAG = "MobileTrader Auth";
    private final static Class<?> AuthenticatorActivityClass = AuthenticatorActivity.class;
    private final Context mContext;

    Authenticator(Context context) {
        super(context);
        mContext = context;
    }

    @NonNull
    @Override
    public Bundle getAccountRemovalAllowed(AccountAuthenticatorResponse response, Account account) throws NetworkErrorException {
        Bundle ret = super.getAccountRemovalAllowed(response, account);
        if (ret.getBoolean(AccountManager.KEY_BOOLEAN_RESULT)) {
            mContext.getContentResolver().delete(ListProvider.getHelper().CLEAR_URI, null, null);
            StaticHelpers.getPrefs(mContext).edit().clear().apply();
        }
        return ret;

    }

    @SuppressLint("MissingPermission")
    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] requiredFeatures, Bundle options) {
        Log.v(TAG, "addAccount()");

        final AccountManager am = AccountManager.get(mContext);
        final Intent intent = new Intent(mContext, AuthenticatorActivityClass);

        if (am.getAccountsByType(accountType).length == 0) {
            intent.putExtra(AuthenticatorActivity.PARAM_AUTH_TOKEN_TYPE, authTokenType);
            intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
            final Bundle bundle = new Bundle();
            bundle.putParcelable(AccountManager.KEY_INTENT, intent);
            return bundle;
        }

        final Bundle bundle = new Bundle();
        bundle.putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION);
        bundle.putString(AccountManager.KEY_ERROR_MESSAGE, mContext.getString(R.string.authenticator_account_exists));
        return bundle;
    }

    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) {
        Log.v(TAG, "confirmCredentials()");
        return null;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        Log.v(TAG, "editProperties()");
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle loginOptions) {
        Log.v(TAG, "getAuthToken()");
        if (!authTokenType.equals(Constants.AUTH_TOKEN_TYPE)) {
            final Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ERROR_MESSAGE, "invalid authTokenType");
            return result;
        }

        final AccountManager am = AccountManager.get(mContext);
        final String password = am.getPassword(account);
        if (password != null) {
            final Bundle bundle = NetworkOperations.authenticate(account.name, password);
            if (bundle.getBoolean(NetworkOperations.LoginResponse.SUCCESS)) {
                final Bundle result = new Bundle();
                result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
                result.putString(AccountManager.KEY_ACCOUNT_TYPE, Constants.ACCOUNT_TYPE);
                result.putBundle(AccountManager.KEY_USERDATA, bundle);
                return result;
            }
        }
        final Intent intent = new Intent(mContext, AuthenticatorActivityClass);
        intent.putExtra(AuthenticatorActivity.PARAM_USERNAME, account.name);
        intent.putExtra(AuthenticatorActivity.PARAM_AUTH_TOKEN_TYPE, authTokenType);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        Log.v(TAG, "getAuthTokenLabel()");
        return null;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features) {
        Log.v(TAG, "hasFeatures()");
        final Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
        return result;
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle loginOptions) {
        Log.v(TAG, "updateCredentials()");
        return null;
    }

}
