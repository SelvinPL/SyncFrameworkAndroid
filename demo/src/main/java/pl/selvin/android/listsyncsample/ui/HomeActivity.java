/*
 Copyright (c) 2014-2016 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */

package pl.selvin.android.listsyncsample.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;

import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.syncadapter.SyncService;

public class HomeActivity extends AppCompatActivity {


	final static String TAG = "HomeActivity";
	private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;


	static void startRealHome(Activity activity) {
		final Intent intent = new Intent();
		intent.setAction(activity.getString(R.string.application_id) + ".intent.action.MAIN");
		activity.startActivity(intent);
		activity.finish();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_home);
	}


	@Override
	protected void onResume() {
		super.onResume();
		if (accountExists()) {
			new Handler(getMainLooper()).postDelayed(() -> startRealHome(HomeActivity.this), 500);
		}
	}


	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == PLAY_SERVICES_RESOLUTION_REQUEST) {
			if (resultCode != RESULT_OK) {
				finish();
			}
		}
	}

	boolean accountExists() {
		Account ac = SyncService.getAccount(this);
		Log.d(TAG, "" + ac);
		if (ac == null) {
			final FragmentActivity thizz = this;
			new Handler(getMainLooper()).postDelayed(() -> {
				try {
					AccountManager am = AccountManager.get(thizz);
					am.addAccount(Constants.ACCOUNT_TYPE, Constants.AUTH_TOKEN_TYPE, null, null, thizz,
							ret -> {
								try {
									if (ret.getResult() == null) {
										finish();
									}
								} catch (Exception ex) {
									Toast.makeText(thizz, R.string.cannot_create_account, Toast.LENGTH_LONG).show();
									finish();
								}

							}, null);
				} catch (Exception ex) {
					Toast.makeText(thizz, R.string.cannot_create_account, Toast.LENGTH_LONG).show();
					finish();
				}
			}, 200);
			return false;
		}
		return true;
	}
}