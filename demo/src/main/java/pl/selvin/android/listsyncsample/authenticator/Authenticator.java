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
import android.support.annotation.NonNull;

import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.provider.ListProvider;

/**
 * This class is an implementation of AbstractAccountAuthenticator for
 * authenticating accounts in the com.example.android.samplesync domain.
 */
@SuppressLint("NewApi")
class Authenticator extends AbstractAccountAuthenticator {
    private final Context mContext;

    Authenticator(Context context) {
        super(context);
        mContext = context;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Bundle getAccountRemovalAllowed(
            AccountAuthenticatorResponse response, Account account)
            throws NetworkErrorException {
        Bundle ret = super.getAccountRemovalAllowed(response, account);
        if (ret.getBoolean(AccountManager.KEY_BOOLEAN_RESULT)) {
            try {
                //temporary fix for account deletetion and clearing data
                //next move is to return intent ...
                final String[] pckgs = mContext.getPackageManager().getPackagesForUid(1000);
                for (final String pckg : pckgs) {
                    mContext.grantUriPermission(pckg,
                            ListProvider.getHelper().CLEAR_URI,
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                }
                mContext.getContentResolver().delete(ListProvider.getHelper().CLEAR_URI,
                        null, null);
                mContext.revokeUriPermission(ListProvider.getHelper().CLEAR_URI,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return ret;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response,
                             String accountType, String authTokenType,
                             String[] requiredFeatures, Bundle options) {
        final AccountManager am = AccountManager.get(mContext);
        final Intent intent = new Intent(mContext, AuthenticatorActivity.class);

        if (am.getAccountsByType(accountType).length == 0) {

            intent.putExtra(AuthenticatorActivity.PARAM_AUTHTOKEN_TYPE,
                    authTokenType);
            intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
                    response);
            final Bundle bundle = new Bundle();
            bundle.putParcelable(AccountManager.KEY_INTENT, intent);
            return bundle;
        }
        final Bundle bundle = new Bundle();

        bundle.putInt(AccountManager.KEY_ERROR_CODE,
                AccountManager.ERROR_CODE_BAD_REQUEST);
        bundle.putString(AccountManager.KEY_ERROR_MESSAGE, mContext.getString(R.string.account_exists));
        return bundle;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response,
                                     Account account, Bundle options) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response,
                                 String accountType) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response,
                               Account account, String authTokenType, Bundle loginOptions) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAuthTokenLabel(String authTokenType) {
        if (authTokenType.equals(Constants.AUTH_TOKEN_TYPE)) {
            return mContext.getString(R.string.label);
        }
        return null;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response,
                              Account account, String[] features) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response,
                                    Account account, String authTokenType, Bundle loginOptions) {
        return null;
    }

}
