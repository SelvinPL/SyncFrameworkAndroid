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
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.CacheControl;
import okhttp3.Request;
import okhttp3.Response;
import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.authenticator.NetworkOperations;
import pl.selvin.android.listsyncsample.network.HttpClient;
import pl.selvin.android.listsyncsample.provider.Database;
import pl.selvin.android.listsyncsample.provider.ListProvider;
import pl.selvin.android.listsyncsample.provider.RequestExecutor;

public class SyncService extends Service {
	public static final String SYNC_SERVICE_BINDER = "SYNC_SERVICE_BINDER";
	public static final int SYNC_IDLE = 0;
	public static final int SYNC_ACTIVE = 1;
	public static final int SYNC_PENDING = 2;

	private static final Object sSyncAdapterLock = new Object();
	private static final long PING_DELAY_SECONDS = 60;

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

	public static String getUserId(Context context) {
		Account account = getAccount(context);
		if (account != null) {
			AccountManager accountManager = AccountManager.get(context);
			return accountManager.getUserData(account, NetworkOperations.LoginResponse.USER_ID);
		}
		return null;
	}

	public static Account getAccount(Context context) {
		AccountManager accountManager = AccountManager.get(context);
		Account[] accounts = null;
		try {
			accounts = accountManager.getAccountsByType(Constants.ACCOUNT_TYPE);
		} catch (SecurityException e) {
			e.printStackTrace();
		}
		if (accounts != null && accounts.length > 0) {
			return accounts[0];
		}
		return null;
	}

	static void copySyncResult(@Nullable SyncResult source, @NonNull SyncResult destination) {
		if (source != null && source != destination) {
			destination.tooManyDeletions = source.tooManyDeletions;
			destination.tooManyRetries = source.tooManyRetries;
			destination.fullSyncRequested = source.fullSyncRequested;
			destination.partialSyncUnavailable = source.partialSyncUnavailable;
			destination.moreRecordsToGet = source.moreRecordsToGet;
			destination.delayUntil = source.delayUntil;
			destination.stats.numAuthExceptions = source.stats.numAuthExceptions;
			destination.stats.numIoExceptions = source.stats.numIoExceptions;
			destination.stats.numParseExceptions = source.stats.numParseExceptions;
			destination.stats.numConflictDetectedExceptions = source.stats.numConflictDetectedExceptions;
			destination.stats.numInserts = source.stats.numInserts;
			destination.stats.numUpdates = source.stats.numUpdates;
			destination.stats.numDeletes = source.stats.numDeletes;
			destination.stats.numEntries = source.stats.numEntries;
			destination.stats.numSkippedEntries = source.stats.numSkippedEntries;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		synchronized (sSyncAdapterLock) {
			if (sSyncAdapter == null) {
				sSyncAdapter = new SyncAdapter(this);
			} else {
				sSyncAdapter.setService(this);
			}
		}

		if (intent.hasExtra(SYNC_SERVICE_BINDER))
			return mBinder;
		return sSyncAdapter.getSyncAdapterBinder();
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

		private final ScheduledExecutorService pingExecutor;
		private SyncService mService;

		SyncAdapter(SyncService service) {
			super(service.getApplicationContext(), true);
			mService = service;
			pingExecutor = Executors.newScheduledThreadPool(1);
		}

		void setService(SyncService service) {
			mService = service;
		}

		@Override
		synchronized public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
			final ScheduledFuture<?> scheduledFuture =
					pingExecutor.scheduleWithFixedDelay(this::doPing, PING_DELAY_SECONDS, PING_DELAY_SECONDS, TimeUnit.SECONDS);
			mService.mLastStatus = SYNC_ACTIVE;
			mService.fireStatusChanged();
			extras.putParcelable(RequestExecutor.ACCOUNT_PARAMETER, account);
			extras.putParcelable(RequestExecutor.SYNC_RESULT_PARAMETER, syncResult);
			extras.putString(RequestExecutor.SCOPE_PARAMETER, Database.DS);

			final ListProvider listProvider = (ListProvider) provider.getLocalContentProvider();
			if (listProvider != null) {
				try {
					final Bundle results = listProvider.sync(extras);
				} catch (Exception ex) {
					syncResult.stats.numIoExceptions++;
					ex.printStackTrace();
				}
			} else {
				try {
					final Bundle results = provider.call(ListProvider.getHelper().SYNC_URI.toString(), null, extras);
					if (results != null) {
						final SyncResult syncResultResult = results.getParcelable(RequestExecutor.SYNC_RESULT_PARAMETER);
						copySyncResult(syncResultResult, syncResult);
					}
				} catch (RemoteException e) {
					syncResult.stats.numIoExceptions++;
					e.printStackTrace();
				}
			}
			Log.v("SyncStats: ", syncResult.stats.toString());
			Log.d("SyncResult: ", syncResult.toString());

			mService.mLastStatus = SYNC_IDLE;
			mService.fireStatusChanged();
		}

		private void doPing() {
			try {
				final Request.Builder requestBuilder = new Request.Builder().url(Constants.SERVICE_URI + "DefaultScopeSyncService.svc/$syncscopes")
						.method("HEAD", null).cacheControl(new CacheControl.Builder().noCache().noStore().build());
				final Response response = HttpClient.DEFAULT.newCall(requestBuilder.build()).execute();
				response.close();
			} catch (Exception ignore) {
			}
		}
	}
}