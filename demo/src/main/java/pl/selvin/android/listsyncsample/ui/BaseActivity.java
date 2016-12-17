/***
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
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.util.Calendar;

import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.syncadapter.ISyncService;
import pl.selvin.android.listsyncsample.syncadapter.ISyncStatusObserver;
import pl.selvin.android.listsyncsample.syncadapter.SyncService;
import pl.selvin.android.listsyncsample.utils.StaticHelpers;
import pl.selvin.android.listsyncsample.utils.Ui;


public abstract class BaseActivity extends AppCompatActivity {

    private static final String LAST_SYNC_TIME = "LAST_SYNC_TIME";
    private static final long AUTO_SYNC_TIMER_MILLIS = 1000 * 60 * 5;
    private Toolbar mActionBarToolbar, mActionBarToolbar2;
    private ISyncService mService;
    private boolean mBound;
    private Menu mOptionsMenu;
    private Integer mStartLaterDelay = null;

    private final ISyncStatusObserver mObserver = new ISyncStatusObserver.Stub() {

        @Override
        public void onStatusChanged(final int status) throws RemoteException {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    BaseActivity.this.onStatusChanged(status);
                }
            });
        }
    };
    private final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d("SyncService", "onServiceConnected");
            //SyncService.LocalBinder binder = (SyncService.LocalBinder) service;
            mService = ISyncService.Stub.asInterface(service);
            mBound = true;
            try {
                mService.addSyncStatusObserver(mObserver);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            if (mStartLaterDelay != null) {
                startSync(mStartLaterDelay, false);
                mStartLaterDelay = null;
            }
        }


        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.d("SyncService", "onServiceDisconnected");
            mBound = false;
            mService = null;
        }
    };

    void setRefreshActionButtonStatus(int status) {
        if (mOptionsMenu == null) {
            return;
        }

        final MenuItem refreshItem = mOptionsMenu.findItem(R.id.menu_refresh);
        if (refreshItem != null) {
            switch (status) {
                case SyncService.SYNC_IDLE:
                    refreshItem.setEnabled(true);
                    MenuItemCompat.setActionView(refreshItem, null);
                    break;
                case SyncService.SYNC_ACTIVE:
                    if (MenuItemCompat.getActionView(refreshItem) == null)
                        MenuItemCompat.setActionView(refreshItem, R.layout.actionbar_progress);
                    break;
                case SyncService.SYNC_PENDING:
                    refreshItem.setEnabled(false);
                    MenuItemCompat.setActionView(refreshItem, null);
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            StaticHelpers.brandGlowEffect(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        getActionBarToolbar();
        mActionBarToolbar2 = Ui.getViewOrNull(this, R.id.toolbar2);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, SyncService.class);
        intent.putExtra(SyncService.ISYNCSERVICE_BINDER, true);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);

    }

    @Override
    protected void onStop() {
        if (mService != null)
            try {
                mService.removeSyncStatusObserver(mObserver);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        if (mBound) {
            unbindService(mConnection);
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mOptionsMenu = menu;
        if (menu.findItem(R.id.menu_refresh) == null) {
            getMenuInflater().inflate(R.menu.refresh, menu);
            menu.findItem(R.id.menu_refresh)
                    .setIcon(R.drawable.ic_refresh_white_36dp)
                    .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            startSync(true);
                            return false;
                        }
                    });
            if (mBound)
                try {
                    setRefreshActionButtonStatus(mService.getLastStatus());
                } catch (RemoteException re) {
                    re.printStackTrace();
                }
        }
        return super.onCreateOptionsMenu(menu);
    }

    public Toolbar getActionBarToolbar() {
        if (mActionBarToolbar == null) {
            mActionBarToolbar = Ui.getViewOrNull(this, R.id.toolbar1);
            if (mActionBarToolbar != null) {
                setSupportActionBar(mActionBarToolbar);
            }
        }
        return mActionBarToolbar;
    }

    public Toolbar getActionBarToolbar2() {
        return mActionBarToolbar2;
    }

    public void startSync(boolean force) {
        startSync(0, force);
    }

    public void startSync(final int delay, final boolean force) {
        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                if (mBound) {
                    try {
                        if (mService.getLastStatus() == SyncService.SYNC_IDLE) {
                            Account ac = SyncService.getAccount(getApplicationContext());
                            if (ac != null) {
                                final SharedPreferences pref = StaticHelpers.getPrefs(getApplicationContext());
                                final long now = Calendar.getInstance().getTime().getTime();
                                final long syncTime = pref.getLong(LAST_SYNC_TIME, -1);
                                if ((now - syncTime > AUTO_SYNC_TIMER_MILLIS) || force) {
                                    final Bundle settingsBundle = new Bundle();
                                    settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                                    settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                                    ContentResolver.requestSync(ac, Constants.AUTHORITY, Bundle.EMPTY);
                                    pref.edit().putLong(LAST_SYNC_TIME, now).apply();
                                }
                            } else
                                finish();
                        }
                    } catch (RemoteException re) {
                        re.printStackTrace();
                    }
                } else {
                    mStartLaterDelay = delay;
                }
            }
        }, delay);
    }

    public void onStatusChanged(final int status) {
        Log.d("onStatusChanged", "" + status);
        setRefreshActionButtonStatus(status);
    }

    public Integer getLastStatus() {
        if (mBound)
            try {

                return mService.getLastStatus();
            } catch (RemoteException re) {
                re.printStackTrace();
            }
        return null;
    }
}
