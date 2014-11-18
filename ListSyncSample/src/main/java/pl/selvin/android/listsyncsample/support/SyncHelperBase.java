/***
 Copyright (c) 2014 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */

package pl.selvin.android.listsyncsample.support;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.provider.ListProvider;

public class SyncHelperBase extends SyncHelper {

    private static final String TAG = SyncHelperBase.class.getName();

    public SyncHelperBase(Activity activity) {
        super(activity);
    }

    class Task extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {
            try {
                mActivity.getContentResolver().update(
                        ListProvider.getHelper().getSyncUri("DefaultScopeSyncService",
                                "defaultscope"), null,
                        String.format("?userid=%s", params[0]), null);

            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
            return null;
        }

        String ui;

        @Override
        protected void onPreExecute() {
            mActivity.sendOrderedBroadcast(new Intent(
                    Constants.SYNCACTION_START), null);
        }

        @Override
        protected void onPostExecute(Void result) {
            mActivity.sendOrderedBroadcast(
                    new Intent(Constants.SYNCACTION_STOP), null);
        }

    }

    @Override
    public void doSync() {
        try {
            String userID = getUserId();
            if (TextUtils.isEmpty(userID)) {
                new Handler().postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        BroadcastReceiver authenticatorReturn = new BroadcastReceiver() {
                            public void onReceive(Context context, Intent data) {
                                mActivity.getApplicationContext()
                                        .unregisterReceiver(this);
                                if (data.hasExtra(AccountManager.KEY_AUTHTOKEN)) {
                                    getPrefs()
                                            .edit()
                                            .putString(
                                                    AccountManager.KEY_AUTHTOKEN,
                                                    data.getStringExtra(AccountManager.KEY_AUTHTOKEN))
                                            .commit();
                                    doSync();
                                } else {
                                    Toast.makeText(
                                            mActivity,
                                            "Can't create ListSync account ... closing",
                                            Toast.LENGTH_SHORT).show();
                                    mActivity.finish();
                                }

                            }
                        };
                        mActivity.getApplicationContext().registerReceiver(
                                authenticatorReturn,
                                new IntentFilter(
                                        BaseAuthenticatorActivity.BROADCAST));
                        Intent intent = new Intent(mActivity,
                                BaseAuthenticatorActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        mActivity.startActivity(intent);
                    }

                }, 200);

            } else {
                new Task().execute(userID);
            }
        } catch (Exception ex) {
            Log.e(TAG, ex.getMessage());
            Toast.makeText(mActivity,
                    "Can't create ListSync account ... closing",
                    Toast.LENGTH_SHORT).show();
            mActivity.finish();
        }
    }

    private final static String prefName = "LISTSYNC_ACCOUNT";

    SharedPreferences getPrefs() {
        return mActivity.getSharedPreferences(prefName, Context.MODE_PRIVATE);
    }

    @Override
    public String getUserId() throws Exception {
        String userID = getPrefs()
                .getString(AccountManager.KEY_AUTHTOKEN, null);
        return userID;
    }
}
