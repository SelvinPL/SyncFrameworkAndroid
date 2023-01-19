package pl.selvin.android.listsyncsample.authenticator;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;

import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.utils.Ui;

public class AuthenticatorActivity extends AppCompatActivity {
	public static final String PARAM_CONFIRM_CREDENTIALS = "confirmCredentials";
	public static final String PARAM_USERNAME = "username";
	public static final String PARAM_AUTH_TOKEN_TYPE = "authTokenType";
	private boolean requestNewAccount = false;
	private AccountAuthenticatorResponse accountAuthenticatorResponse = null;
	private Bundle resultBundle = null;
	private AccountManager accountManager;
	private UserLoginViewModel model = null;
	private Boolean confirmCredentials = false;
	private String mPassword;
	private TextInputLayout mPasswordEdit;
	private String mUsername;
	private View mProgressView;
	private TextInputLayout mUsernameEdit;
	private Button mOKButton;

	public AuthenticatorActivity() {

	}

	public void finish() {
		if (accountAuthenticatorResponse != null) {
			if (resultBundle != null) {
				accountAuthenticatorResponse.onResult(resultBundle);
			} else {
				accountAuthenticatorResponse.onError(AccountManager.ERROR_CODE_CANCELED, "canceled");
			}
			accountAuthenticatorResponse = null;
		}
		super.finish();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		model = new ViewModelProvider(this).get(UserLoginViewModel.class);
		accountAuthenticatorResponse = getIntent().getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);

		if (accountAuthenticatorResponse != null) {
			accountAuthenticatorResponse.onRequestContinued();
		}
		accountManager = AccountManager.get(this);

		final Intent intent = getIntent();
		mUsername = intent.getStringExtra(PARAM_USERNAME);
		requestNewAccount = mUsername == null;
		confirmCredentials = intent.getBooleanExtra(PARAM_CONFIRM_CREDENTIALS, false);
		setContentView(R.layout.activity_login);
		mUsernameEdit = Ui.getView(this, R.id.username_layout);
		mUsernameEdit.setEnabled(requestNewAccount);
		mPasswordEdit = Ui.getView(this, R.id.password_layout);
		mOKButton = Ui.getView(this, R.id.ok_button);
		mOKButton.setOnClickListener((v -> attemptLogin()));
		mProgressView = Ui.getView(this, R.id.working);
		if (!TextUtils.isEmpty(mUsername))
			Objects.requireNonNull(mUsernameEdit.getEditText()).setText(mUsername);
		if (model.isRunning())
			showProgress(true);
		model.getResult().observe(this, result -> {
			if (!model.isResultProcessed() && result != null)
				onAuthenticationResult(result);
		});
	}

	@SuppressLint("MissingPermission")
	private void finishConfirmCredentials(boolean result) {
		final Account account = new Account(mUsername, Constants.ACCOUNT_TYPE);
		accountManager.setPassword(account, mPassword);
		final Intent intent = new Intent();
		intent.putExtra(AccountManager.KEY_BOOLEAN_RESULT, result);
		resultBundle = intent.getExtras();
		setResult(RESULT_OK, intent);
		finish();
	}

	@SuppressLint("MissingPermission")
	private void finishLogin(Bundle results) {
		final Account account = new Account(mUsername, Constants.ACCOUNT_TYPE);
		if (requestNewAccount) {
			results.remove(NetworkOperations.LoginResponse.SUCCESS);
			accountManager.addAccountExplicitly(account, mPassword, results);
			ContentResolver.setSyncAutomatically(account, Constants.AUTHORITY, true);
		} else {
			accountManager.setPassword(account, mPassword);
		}
		final Intent intent = new Intent();
		intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, mUsername);
		intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, Constants.ACCOUNT_TYPE);
		resultBundle = intent.getExtras();
		setResult(RESULT_OK, intent);
		finish();
	}

	private void onAuthenticationResult(Bundle results) {
		boolean success = (results != null) && results.getBoolean(NetworkOperations.LoginResponse.SUCCESS);
		showProgress(false);
		if (success) {
			if (!confirmCredentials)
				finishLogin(results);
		} else {
			mUsernameEdit.setError(results == null ? getString(R.string.generic_error_message) : results.getString(NetworkOperations.LoginResponse.ERROR));
		}
		if (confirmCredentials)
			finishConfirmCredentials(success);
		model.setResultProcessed(true);
	}


	private void attemptLogin() {
		if (model.isRunning())
			return;

		// Reset errors.
		mUsernameEdit.setError(null);
		mPasswordEdit.setError(null);

		if (requestNewAccount) {
			mUsername = Objects.requireNonNull(mUsernameEdit.getEditText()).getText().toString().trim();
		}
		mPassword = Objects.requireNonNull(mPasswordEdit.getEditText()).getText().toString().trim();

		boolean cancel = false;
		View focusView = null;

		// Check for a valid password, if the user entered one.
		if (TextUtils.isEmpty(mPassword)) {
			mPasswordEdit.setError(getString(R.string.login_error_field_required));
			focusView = mPasswordEdit;
			cancel = true;
		}

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

		// Check for a valid email address.
		if (TextUtils.isEmpty(mUsername)) {
			mUsernameEdit.setError(getString(R.string.login_error_field_required));
			focusView = mUsernameEdit;
			cancel = true;
		}

		if (cancel) {
			// There was an error; don't attempt login and focus the first
			// form field with an error.
			focusView.requestFocus();
		} else {
			// Show a progress spinner, and kick off a background task to
			// perform the user login attempt.
			showProgress(true);
			model.login(mUsername, mPassword);
		}
	}

	private void showProgress(boolean show) {
		mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
		mPasswordEdit.setEnabled(!show);
		mUsernameEdit.setEnabled(!show && requestNewAccount);
		mOKButton.setEnabled(!show);
	}

	public interface AuthenticateCallback {
		void onResult(Bundle result);
	}

	public static class UserLoginViewModel extends ViewModel {
		private final MutableLiveData<Bundle> result = new MutableLiveData<>();
		private boolean running;
		private boolean resultProcessed;
		private boolean cancelled;

		public void login(final String username, final String password) {
			cancelled = false;
			running = true;
			NetworkOperations.authenticateAsync(username, password, res -> {
				if (!cancelled) {
					resultProcessed = false;
					result.postValue(res);
				}
				running = false;
			});
		}

		public MutableLiveData<Bundle> getResult() {
			return result;
		}

		public boolean isRunning() {
			return running;
		}

		public boolean isResultProcessed() {
			return resultProcessed;
		}

		public void setResultProcessed(boolean resultProcessed) {
			this.resultProcessed = resultProcessed;
		}
	}
}