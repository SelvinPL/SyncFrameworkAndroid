package pl.selvin.android.listsyncsample.authenticator;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.utils.Ui;

public class AuthenticatorActivity extends AccountAuthenticatorActivityAppCompat implements View.OnClickListener {
    public static final String PARAM_CONFIRM_CREDENTIALS = "confirmCredentials";
    public static final String PARAM_PASSWORD = "password";
    public static final String PARAM_USERNAME = "username";
    public static final String PARAM_AUTHTOKEN_TYPE = "authtokenType";
    private static final String TAG = "AuthenticatorActivity";
    protected boolean mRequestNewAccount = false;
    private AccountManager mAccountManager;
    private UserLoginTask mAuthTask = null;
    private Boolean mConfirmCredentials = false;
    private String mPassword;
    private TextInputLayout mPasswordEdit;
    private String mUsername;
    private View mProgressView;
    private TextInputLayout mUsernameEdit;
    private Button mOKButton;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mAccountManager = AccountManager.get(this);
        final Intent intent = getIntent();
        mUsername = intent.getStringExtra(PARAM_USERNAME);
        mRequestNewAccount = mUsername == null;
        mConfirmCredentials = intent.getBooleanExtra(PARAM_CONFIRM_CREDENTIALS, false);
        setContentView(R.layout.activity_login);
        mUsernameEdit = Ui.getView(this, R.id.username_layout);
        mUsernameEdit.setEnabled(mRequestNewAccount);
        mPasswordEdit = Ui.getView(this, R.id.password_layout);
        mOKButton = Ui.getView(this, R.id.ok_button);
        mOKButton.setOnClickListener(this);
        mProgressView = Ui.getView(this, R.id.working);
        if (!TextUtils.isEmpty(mUsername)) mUsernameEdit.getEditText().setText(mUsername);
        mAuthTask = (UserLoginTask) getLastNonConfigurationInstance();
        if (mAuthTask != null) {
            mAuthTask.attach(this);
            if (mAuthTask.isRunning)
                showProgress();
            else {
                if (mAuthTask.mResults != null) {
                    onAuthenticationResult(mAuthTask.mResults);
                }
            }
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        if (mAuthTask != null)
            mAuthTask.detach();
        return (mAuthTask);
    }

    @Override
    public void onClick(View view) {
        attemptLogin();
    }

    private void finishConfirmCredentials(boolean result) {
        final Account account = new Account(mUsername, Constants.ACCOUNT_TYPE);
        mAccountManager.setPassword(account, mPassword);
        final Intent intent = new Intent();
        intent.putExtra(AccountManager.KEY_BOOLEAN_RESULT, result);
        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }

    private void finishLogin(Bundle results) {

        Log.i(TAG, "finishLogin()");
        final Account account = new Account(mUsername, Constants.ACCOUNT_TYPE);
        if (mRequestNewAccount) {
            results.remove(LoginResponse.SUCCESS);
            mAccountManager.addAccountExplicitly(account, mPassword, results);
            ContentResolver.setSyncAutomatically(account, Constants.AUTHORITY, true);
        } else {
            mAccountManager.setPassword(account, mPassword);
        }
        final Intent intent = new Intent();
        intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, mUsername);
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, Constants.ACCOUNT_TYPE);
        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }

    private void onAuthenticationResult(Bundle results) {

        boolean success = (results != null) && results.getBoolean(LoginResponse.SUCCESS);
        mAuthTask = null;
        hideProgress();

        if (success) {
            if (!mConfirmCredentials) {
                finishLogin(results);
            } else {
                finishConfirmCredentials(success);
            }
        } else {
            mUsernameEdit.setError(getString(R.string.login_error_invalid_login_or_password));
        }
    }


    private void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }
        mUsernameEdit.setError(null);
        mPasswordEdit.setError(null);

        if (mRequestNewAccount) {
            mUsername = mUsernameEdit.getEditText().getText().toString().trim();
        }
        mPassword = mPasswordEdit.getEditText().getText().toString().trim();

        boolean cancel = false;
        View focusView = null;

        final String fakePassword = getString(R.string.fake_password);
        if (TextUtils.isEmpty(mPassword)) {
            mPasswordEdit.setError(getString(R.string.login_error_field_required));
            focusView = mPasswordEdit;
            cancel = true;
        } else if (!mPassword.equals(fakePassword)) {
            mPasswordEdit.setError(getString(R.string.login_error_invalid_password, fakePassword));
            focusView = mPasswordEdit;
            cancel = true;
        }

        if (TextUtils.isEmpty(mUsername)) {
            mUsernameEdit.setError(getString(R.string.login_error_field_required));
            focusView = mUsernameEdit;
            cancel = true;
        }

        if (cancel) {
            focusView.requestFocus();
        } else {
            showProgress();
            mAuthTask = new UserLoginTask(this);
            mAuthTask.execute((Void) null);
        }
    }

    private void onAuthenticationCancel() {
        mAuthTask = null;
        hideProgress();
    }

    private void showProgress() {
        mProgressView.setVisibility(View.VISIBLE);
        mPasswordEdit.setEnabled(false);
        mUsernameEdit.setEnabled(false);
        mOKButton.setEnabled(false);
    }

    private void hideProgress() {
        if(isFinishing())
            return;
        mProgressView.setVisibility(View.GONE);
        mPasswordEdit.setEnabled(true);
        mUsernameEdit.setEnabled(mRequestNewAccount);
        mOKButton.setEnabled(true);
    }

    static class UserLoginTask extends AsyncTask<Void, Void, Bundle> {

        AuthenticatorActivity activity = null;
        boolean isRunning;
        Bundle mResults = null;

        UserLoginTask(AuthenticatorActivity activity) {
            attach(activity);
        }

        void attach(AuthenticatorActivity activity) {
            this.activity = activity;
        }

        void detach() {
            activity = null;
        }

        @Override
        protected Bundle doInBackground(Void... params) {
            try {
                isRunning = true;
                if (activity != null)
                    return authenticate(activity.mUsername, activity.mPassword);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(final Bundle results) {
            isRunning = false;
            mResults = results;
            if (activity != null)
                activity.onAuthenticationResult(results);
        }

        @Override
        protected void onCancelled() {
            if (activity != null)
                activity.onAuthenticationCancel();
        }
    }

    public static Bundle authenticate(String username, String password) {
        final Bundle bundle = new Bundle();
        final OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(Constants.SERVICE_URI + "/Login.ashx?username=" + username)
                .addHeader("Accept", "application/json").addHeader("Content-type", "application/json; charset=utf-8").build();
        try {
            final Response response = client.newCall(request).execute();
            bundle.putBoolean(LoginResponse.SUCCESS, true);
            bundle.putString(LoginResponse.USER_ID, response.body().string());
            return bundle;
        } catch (Exception e) {
            bundle.putString(LoginResponse.ERROR, e.getMessage());
            e.printStackTrace();
        }
        bundle.putBoolean(LoginResponse.SUCCESS, false);
        return  bundle;
    }

    public interface LoginResponse {
        String SUCCESS = "SUCCESS";
        String USER_ID = "USER_ID";
        String ERROR = "ERROR";
    }
}
