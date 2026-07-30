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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.core.util.Consumer;

import java.lang.ref.WeakReference;
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
import pl.selvin.android.listsyncsample.utils.Logging;

public class SyncService extends Service {
	public static final String SYNC_SERVICE_BINDER = "SYNC_SERVICE_BINDER";
	public static final int SYNC_IDLE = 0;
	public static final int SYNC_ACTIVE = 1;
	public static final int SYNC_PENDING = 2;

	private final static String TAG = "SyncService";
	private final static SyncServiceHolder syncServiceHolder = new SyncServiceHolder();
	private static SyncAdapter syncAdapter;
	private final RemoteCallbackList<ISyncStatusObserver> mObservers = new RemoteCallbackList<>();
	private final Handler handler = new Handler(Looper.getMainLooper());
	private final ISyncService.Stub mBinder = new SyncServiceStub(this);
	private int connections = 0;
	private final Runnable stopSelfRunnable = () -> {
		if (connections == 0)
			stopSelf();
	};
	private int mLastStatus = SYNC_IDLE;

	@Override
	public void onCreate() {
		super.onCreate();
		syncServiceHolder.registerService(this);
		if (syncAdapter == null) {
			syncAdapter = new SyncAdapter(getApplicationContext(), true, syncServiceHolder);
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		connections++;
		if (intent.hasExtra(SYNC_SERVICE_BINDER))
			return mBinder;
		return syncAdapter.getSyncAdapterBinder();
	}

	@Override
	public boolean onUnbind(Intent intent) {
		connections--;
		handler.postDelayed(stopSelfRunnable, 1000);
		return super.onUnbind(intent);
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
		syncServiceHolder.removeService(this);
		mObservers.kill();
		super.onDestroy();
	}

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
			Logging.log(e);
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

	private static class SyncServiceStub extends ISyncService.Stub {
		private final WeakReference<SyncService> serviceRef;

		private SyncServiceStub(SyncService service) {
			serviceRef = new WeakReference<>(service);
		}

		public void addSyncStatusObserver(ISyncStatusObserver cb) {
			final SyncService service = serviceRef.get();
			if (cb != null && service != null) {
				service.mObservers.register(cb);
				try {
					cb.onStatusChanged(service.mLastStatus);
				} catch (RemoteException e) {
					Logging.log(e);
				}
			}
		}

		public void removeSyncStatusObserver(ISyncStatusObserver cb) {
			final SyncService service = serviceRef.get();
			if (cb != null && service != null)
				service.mObservers.unregister(cb);
		}

		public int getLastStatus() {
			final SyncService service = serviceRef.get();
			if (service != null)
				return service.mLastStatus;
			else
				return SYNC_IDLE;
		}
	}

	static class SyncAdapter extends AbstractThreadedSyncAdapter {
		private static final long PING_DELAY_SECONDS = 60;
		private final ScheduledExecutorService pingExecutor;
		private final SyncServiceHolder syncServiceHolder;

		SyncAdapter(Context context, boolean autoInitialize, SyncServiceHolder syncServiceHolder) {
			super(context, autoInitialize);
			this.syncServiceHolder = syncServiceHolder;
			pingExecutor = Executors.newScheduledThreadPool(1);
		}

		@Override
		public synchronized void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
			final ScheduledFuture<?> scheduledFuture =
					pingExecutor.scheduleWithFixedDelay(this::doPing, PING_DELAY_SECONDS, PING_DELAY_SECONDS, TimeUnit.SECONDS);
			try {

				extras.putParcelable(RequestExecutor.ACCOUNT_PARAMETER, account);
				extras.putParcelable(RequestExecutor.SYNC_RESULT_PARAMETER, syncResult);
				extras.putString(RequestExecutor.SCOPE_PARAMETER, Database.DS);

				syncServiceHolder.Consume(syncService -> {
					syncService.mLastStatus = SYNC_ACTIVE;
					syncService.fireStatusChanged();
				});

				final ListProvider localContentProvider = (ListProvider) provider.getLocalContentProvider();
				if (localContentProvider != null) {
					try {
						localContentProvider.sync(extras);
					} catch (Exception ex) {
						syncResult.stats.numIoExceptions++;
						Logging.log(ex);
					}
				} else {
					try {
						final Bundle results = provider.call(ListProvider.getHelper().SYNC_URI.toString(), null, extras);
						if (results != null) {
							final SyncResult syncResultResult = BundleCompat.getParcelable(results, RequestExecutor.SYNC_RESULT_PARAMETER, SyncResult.class);
							copySyncResult(syncResultResult, syncResult);
						}
					} catch (RemoteException e) {
						syncResult.stats.numIoExceptions++;
						Logging.log(e);
					}
				}
				Log.v("SyncStats: ", syncResult.stats.toString());
				Log.d("SyncResult: ", syncResult.toString());
				syncServiceHolder.Consume(syncService -> {
					syncService.mLastStatus = SYNC_IDLE;
					syncService.fireStatusChanged();
				});
			} finally {
				scheduledFuture.cancel(true);
			}
		}

		private void doPing() {
			try {
				final Request.Builder requestBuilder = new Request.Builder().url(Constants.SERVICE_URI + "DefaultScopeSyncService.svc/$syncscopes")
						.method("HEAD", null).cacheControl(new CacheControl.Builder().noCache().noStore().build());
				final Response response = HttpClient.DEFAULT_OK_HTTP_CLIENT.newCall(requestBuilder.build()).execute();
				response.close();
			} catch (Exception ignore) {
			}
		}
	}

	private static class SyncServiceHolder {
		private SyncService syncService = null;

		public synchronized void registerService(SyncService syncService) {
			this.syncService = syncService;
		}

		public synchronized void removeService(SyncService syncService) {
			if (this.syncService == syncService) {
				this.syncService = null;
			}
		}

		public synchronized void Consume(@NonNull Consumer<SyncService> consumer) {
			if (syncService != null) {
				consumer.accept(syncService);
			}
		}
	}
}