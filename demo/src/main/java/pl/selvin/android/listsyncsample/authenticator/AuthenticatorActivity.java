package pl.selvin.android.listsyncsample.authenticator;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import com.google.android.material.textfield.TextInputLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.utils.Ui;

public class AuthenticatorActivity extends AccountAuthenticatorActivityAppCompat implements View.OnClickListener {
    public static final String PARAM_CONFIRM_CREDENTIALS = "confirmCredentials";
    public static final String PARAM_USERNAME = "username";
    public static final String PARAM_AUTHTOKEN_TYPE = "authtokenType";
    private static final String TAG = "AuthenticatorActivity";
    protected boolean mRequestNewAccount = false;
    private AccountManager mAccountManager;
    private UserLoginTask mAuthTask = null;
    private Boolean mConfirmCredentials = false;
    private String mPassword;
    private TextInputLayout mPasswordInput;
    private String mUsername;
    private View mProgressView;
    private TextInputLayout mUsernameInput;
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
        mUsernameInput = Ui.getView(this, R.id.username_layout);
        mUsernameInput.setEnabled(mRequestNewAccount);
        mPasswordInput = Ui.getView(this, R.id.password_layout);
        mOKButton = Ui.getView(this, R.id.ok_button);
        mOKButton.setOnClickListener(this);
        mProgressView = Ui.getView(this, R.id.working);
        if (!TextUtils.isEmpty(mUsername)) requireUsernameEdit().setText(mUsername);
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

    @NonNull
    EditText requirePasswordEdit()
    {
        final EditText editText = mPasswordInput != null ? mPasswordInput.getEditText() : null;
        if (editText == null) {
            throw new IllegalStateException("No EditText for UsernameInput.");
        }
        return editText;
    }

    @NonNull
    EditText requireUsernameEdit()
    {
        final EditText editText = mUsernameInput != null ? mUsernameInput.getEditText() : null;
        if (editText == null) {
            throw new IllegalStateException("No EditText for UsernameInput.");
        }
        return editText;
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
                //noinspection ConstantConditions
                finishConfirmCredentials(success);
            }
        } else {
            mUsernameInput.setError(getString(R.string.login_error_invalid_login_or_password));
        }
    }


    private void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }
        mUsernameInput.setError(null);
        mPasswordInput.setError(null);

        if (mRequestNewAccount) {
            mUsername = requireUsernameEdit().getText().toString().trim();
        }
        mPassword = requirePasswordEdit().getText().toString().trim();

        boolean cancel = false;
        View focusView = null;

        final String fakePassword = getString(R.string.fake_password);
        if (TextUtils.isEmpty(mPassword)) {
            mPasswordInput.setError(getString(R.string.login_error_field_required));
            focusView = mPasswordInput;
            cancel = true;
        } else if (!mPassword.equals(fakePassword)) {
            mPasswordInput.setError(getString(R.string.login_error_invalid_password, fakePassword));
            focusView = mPasswordInput;
            cancel = true;
        }

        if (TextUtils.isEmpty(mUsername)) {
            mUsernameInput.setError(getString(R.string.login_error_field_required));
            focusView = mUsernameInput;
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
        mPasswordInput.setEnabled(false);
        mUsernameInput.setEnabled(false);
        mOKButton.setEnabled(false);
    }

    private void hideProgress() {
        if(isFinishing())
            return;
        mProgressView.setVisibility(View.GONE);
        mPasswordInput.setEnabled(true);
        mUsernameInput.setEnabled(mRequestNewAccount);
        mOKButton.setEnabled(true);
    }

    static class UserLoginTask extends AsyncTask<Void, Void, Bundle> {

        //it doesn't leak ... see attach/detach
        @SuppressLint("StaticFieldLeak")
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

    public static Bundle authenticate(String username, @SuppressWarnings("unused") String password) {
        final Bundle bundle = new Bundle();
        final OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(Constants.SERVICE_URI + "/Login.ashx?username=" + username)
                .addHeader("Accept", "application/json").addHeader("Content-type", "application/json; charset=utf-8").build();
        try {
            final Response response = client.newCall(request).execute();
            final ResponseBody body = response.body();
            if(body != null){
                bundle.putBoolean(LoginResponse.SUCCESS, true);
                bundle.putString(LoginResponse.USER_ID, body.string());
            } else {
                bundle.putBoolean(LoginResponse.SUCCESS, false);
                bundle.putString(LoginResponse.ERROR, "Response has no body");
            }
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
