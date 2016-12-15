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

package pl.selvin.android.listsyncsample.syncadapter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;

import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.provider.ListProvider;
import pl.selvin.android.syncframework.content.SyncStats;

public class SyncAdapter extends AbstractThreadedSyncAdapter {

    static final String TAG = "ListSync";
    private final AccountManager mAccountManager;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mAccountManager = AccountManager.get(context);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
                              ContentProviderClient provider, SyncResult syncResult) {
        getContext().sendOrderedBroadcast(
                new Intent(Constants.SYNCACTION_START), null);
        SyncStats stats = new SyncStats();

        String parameters = null;
        try {
        parameters = String.format("?userid=%s", mAccountManager.blockingGetAuthToken(account, Constants.AUTHTOKEN_TYPE, true));
        } catch (Exception ex) {
            stats.numIoExceptions++;
        }
        if(parameters != null) {
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
        getContext().sendOrderedBroadcast(
                new Intent(Constants.SYNCACTION_STOP), null);
    }
}