package pl.selvin.android.listsyncsample.syncadapter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;

import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.provider.ListProvider;

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
        try {
            provider.update(ListProvider.getHelper().getSyncUri("DefaultScopeSyncService",
                    "defaultscope"), null, String.format("?userid=%s",
                    mAccountManager.blockingGetAuthToken(account,
                            Constants.AUTHTOKEN_TYPE, true)), null);

        } catch (AuthenticatorException e) {
            Log.e(TAG, e.getMessage());
            syncResult.stats.numAuthExceptions++;
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            syncResult.stats.numIoExceptions++;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            syncResult.stats.numIoExceptions++;
        }
        getContext().sendOrderedBroadcast(
                new Intent(Constants.SYNCACTION_STOP), null);
    }
}