package pl.selvin.android.listsyncsample.support;

import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.authenticator.Common;
import pl.selvin.android.listsyncsample.authenticator.Common.IAuthenticationResult;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;

public class BaseAuthenticatorActivity extends Activity implements
		IAuthenticationResult {

	private Thread mAuthThread;

	private final Handler mHandler = new Handler();
	private TextView mMessage;

	private EditText mUsernameEdit;
	private String mUsername;

	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		requestWindowFeature(Window.FEATURE_LEFT_ICON);
		setContentView(R.layout.login_activity);
		getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON,
				android.R.drawable.ic_dialog_alert);

		mMessage = (TextView) findViewById(R.id.message);
		mUsernameEdit = (EditText) findViewById(R.id.username_edit);
		mMessage.setText(getMessage());
	}

	private CharSequence getMessage() {
		getString(R.string.label);
		if (TextUtils.isEmpty(mUsername)) {
			final CharSequence msg = getText(R.string.login_activity_newaccount_text);
			return msg;
		}
		return null;
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		final ProgressDialog dialog = new ProgressDialog(this);
		dialog.setMessage(getText(R.string.ui_activity_authenticating));
		dialog.setIndeterminate(true);
		dialog.setCancelable(true);
		dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				// Log.i(TAG, "dialog cancel has been invoked");
				if (mAuthThread != null) {
					mAuthThread.interrupt();

					finish();
				}

			}
		});
		return dialog;
	}

	public void handleLogin(View view) {
		mUsername = mUsernameEdit.getText().toString();
		if (TextUtils.isEmpty(mUsername)) {
			mMessage.setText(getMessage());
		} else {
			showDialog(0);

			// Start authenticating...
			mAuthThread = Common.attemptAuth(mUsername, mHandler, this);
		}
	}

	public void onAuthenticationResult(boolean result) {
		dismissDialog(0);
		finish();
	}

	public void finish() {
		Intent intent = new Intent(BROADCAST);
		if (Common.getAuthtoken() != null) {
			intent.putExtra(AccountManager.KEY_AUTHTOKEN, Common.getAuthtoken());
		}
		sendBroadcast(intent);
		super.finish();
	}

	public static final String BROADCAST = "PRE_FROYO_LOGIN";
}
