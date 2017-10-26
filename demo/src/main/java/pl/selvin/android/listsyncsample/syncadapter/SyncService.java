/*
 * Copyright (c) 2014-2016 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package pl.selvin.android.listsyncsample.syncadapter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.authenticator.AuthenticatorActivity;
import pl.selvin.android.listsyncsample.provider.ListProvider;
import pl.selvin.android.syncframework.content.SyncStats;

public class SyncService extends Service {
    public static final String ISYNCSERVICE_BINDER = "ISYNCSERVICE_BINDER";
    public static final int SYNC_IDLE = 0;
    public static final int SYNC_ACTIVE = 1;
    public static final int SYNC_PENDING = 2;

    private static final Object sSyncAdapterLock = new Object();

    private final static String TAG = "SyncService";
    private static SyncAdapter sSyncAdapter;
    private final RemoteCallbackList<ISyncStatusObserver> mObservers = new RemoteCallbackList<>();
    private int mLastStatus = SYNC_IDLE;

    private final ISyncService.Stub mBinder = new ISyncService.Stub() {
        public void addSyncStatusObserver(ISyncStatusObserver cb) {
            if (cb != null) {
                mObservers.register(cb);
                try {
                    cb.onStatusChanged(mLastStatus);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

        public void removeSyncStatusObserver(ISyncStatusObserver cb) {
            if (cb != null) mObservers.unregister(cb);
        }

        public int getLastStatus() {
            return mLastStatus;
        }
    };

    /*
     * {@inheritDoc}
     */
    @Override
    public IBinder onBind(Intent intent) {
        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new SyncAdapter(getApplicationContext(), true);
            }
        }
        sSyncAdapter.setService(this);
        if (intent.hasExtra(ISYNCSERVICE_BINDER))
            return mBinder;
        return sSyncAdapter.getSyncAdapterBinder();
    }

    public static String getUserId(Context context) {
        AccountManager am = AccountManager.get(context);
        Account[] ac = null;
        try {
            ac = am.getAccountsByType(Constants.ACCOUNT_TYPE);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        if (ac != null && ac.length > 0) {
            return am.getUserData(ac[0], AuthenticatorActivity.LoginResponse.USER_ID);
        }
        return null;
    }

    public static Account getAccount(Context context) {
        AccountManager am = AccountManager.get(context);
        Account[] ac = null;
        try {
            ac = am.getAccountsByType(Constants.ACCOUNT_TYPE);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        if (ac != null && ac.length > 0) {
            return ac[0];
        }
        return null;
    }

    private void fireStatusChanged() {
        final int N = mObservers.beginBroadcast();
        for (int i = 0; i < N; i++) {
            try {
                mObservers.getBroadcastItem(i).onStatusChanged(mLastStatus);
            } catch (RemoteException ignore) {
            }
        }
        mObservers.finishBroadcast();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        mObservers.kill();
        super.onDestroy();
    }

    static class SyncAdapter extends AbstractThreadedSyncAdapter {

        private SyncService mService = null;

        SyncAdapter(Context context, boolean autoInitialize) {
            super(context, autoInitialize);
        }

        void setService(SyncService service) {
            mService = service;
        }

        @Override
        synchronized public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
            mService.mLastStatus = SYNC_ACTIVE;
            mService.fireStatusChanged();
            SyncStats stats = new SyncStats();

            String parameters = null;
            try {
                parameters = String.format("?userid=%s", getUserId(getContext()));
            } catch (Exception ex) {
                stats.numIoExceptions++;
            }
            if (parameters != null) {
                final ListProvider mtProvider = (ListProvider) provider.getLocalContentProvider();
                if (mtProvider != null) {
                    try {
                        mtProvider.sync("DefaultScopeSyncService", "defaultscope", parameters, stats);
                    } catch (Exception ex) {
                        stats.numIoExceptions++;
                    }
                } else {
                    try {
                        final Uri uri = ListProvider.getHelper().getSyncUri("DefaultScopeSyncService", "defaultscope");

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                            Bundle syncParams = new Bundle();
                            syncParams.putParcelable(ListProvider.SYNC_SYNCSTATS, stats);
                            syncParams = provider.call(uri.toString(), parameters, syncParams);
                            stats = syncParams.getParcelable(ListProvider.SYNC_SYNCSTATS);
                        } else {
                            if (provider.update(uri, null, parameters, null) != 0) {
                                stats.numParseExceptions++;
                            }
                        }
                    } catch (RemoteException e) {
                        stats.numParseExceptions++;
                    }
                }
            }
            syncResult.stats.numAuthExceptions = stats.numAuthExceptions;
            syncResult.stats.numConflictDetectedExceptions = stats.numConflictDetectedExceptions;
            syncResult.stats.numDeletes = stats.numDeletes;
            syncResult.stats.numEntries = stats.numEntries;
            syncResult.stats.numInserts = stats.numInserts;
            syncResult.stats.numIoExceptions = stats.numIoExceptions;
            syncResult.stats.numParseExceptions = stats.numParseExceptions;
            syncResult.stats.numSkippedEntries = stats.numSkippedEntries;
            syncResult.stats.numUpdates = stats.numUpdates;
            Log.v("SyncStats: ", stats.toString());
            Log.d("SyncResult: ", syncResult.toString());

            mService.mLastStatus = SYNC_IDLE;
            mService.fireStatusChanged();
        }
    }
}