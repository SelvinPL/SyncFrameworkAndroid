package pl.selvin.android.ListSyncSample.support;

import pl.selvin.android.ListSyncSample.Constants;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.content.ContentResolver;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class SyncHelperFroyo extends SyncHelper {

	private static final String TAG = SyncHelperFroyo.class.getName();

	public SyncHelperFroyo(Activity activity) {
		super(activity);
	}

	@Override
	public void doSync() {
		AccountManager am = AccountManager.get(mActivity);
		Account[] ac = am.getAccountsByType(Constants.ACCOUNT_TYPE);
		if (ac.length > 0) {
			ContentResolver.requestSync(ac[0], Constants.AUTHORITY,
					new Bundle());
		} else {
			try {
				am.addAccount(Constants.ACCOUNT_TYPE, Constants.AUTHTOKEN_TYPE,
						null, null, mActivity,
						new AccountManagerCallback<Bundle>() {
							@Override
							public void run(AccountManagerFuture<Bundle> ret) {
								try {
									Log.d(TAG, ret.getResult() + " ");
									doSync();
								} catch (Exception ex) {
									// Log.e(TAG, ex.getMessage());
									Toast.makeText(
											mActivity,
											"Can't create ListSync account ... closing",
											Toast.LENGTH_LONG).show();
									mActivity.finish();
								}

							}
						}, null);

			} catch (Exception ex) {
				Log.e(TAG, ex.getMessage());
				Toast.makeText(mActivity,
						"Can't open ListSync account ... closing",
						Toast.LENGTH_LONG).show();
				mActivity.finish();
			}
		}
	}

	class UserIDRunnale implements Runnable {
		UserIDRunnale(final Account ac) {
			this.ac = ac;
		}

		String UserID = null;
		Exception ex = null;
		final Account ac;

		public String getUserID() {
			return UserID;
		}

		public Exception getException() {
			return ex;
		}

		@Override
		public void run() {
			try {
				UserID = AccountManager.get(mActivity).blockingGetAuthToken(ac,
						Constants.AUTHTOKEN_TYPE, true);
			} catch (Exception e) {
				ex = e;
			}
		}
	}

	public String getUserId() throws Exception {

		AccountManager am = AccountManager.get(mActivity);
		final Account[] ac = am.getAccountsByType(Constants.ACCOUNT_TYPE);
		if (ac.length != 0) {
			UserIDRunnale uid = new UserIDRunnale(ac[0]);
			Thread th = new Thread(uid); 
			th.start();				//since 3.1 or so calling blockingGetAuthToken on UI thread will throw illegal state exception
			th.join();				//just hack to not getting illegal state exception ... DO NOT do this in production code
			if (uid.getUserID() != null)
				return uid.getUserID();
			if (uid.getException() != null)
				throw uid.getException();
		}
		throw new Exception("Can't open ListSync account ... closing");
	}
}
