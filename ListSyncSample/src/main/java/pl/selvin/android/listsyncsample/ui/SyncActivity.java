/***
 * Copyright (c) 2014 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package pl.selvin.android.listsyncsample.ui;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.support.SyncHelper;

@SuppressLint("Registered")
public class SyncActivity extends AppCompatActivity {
    final SyncHelper syncHelper = SyncHelper.createInstance(this);
    boolean registered = true;
    volatile boolean showMenu = true;
    BroadcastReceiver startSync = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            showMenu = false;
            invalidateOptionsMenu();
        }
    };
    BroadcastReceiver stopSync = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            showMenu = true;
            invalidateOptionsMenu();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerRecivers();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                syncHelper.doSync();
            }

        }, 200);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.refresh, menu);
        return super.onCreateOptionsMenu(menu);
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.refresh);
        item.setVisible(true);
        if (!showMenu) {
            MenuItemCompat.setActionView(item, R.layout.actionbar_progress);
        } else {
            MenuItemCompat.setActionView(item, null);
        }
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.refresh:
                syncHelper.doSync();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void onResume() {
        super.onResume();
        if (!registered) {
            registerRecivers();
        }
    }

    private void registerRecivers() {
        registerReceiver(startSync,
                new IntentFilter(Constants.SYNCACTION_START));
        registerReceiver(stopSync, new IntentFilter(Constants.SYNCACTION_STOP));
        registered = true;
    }

    protected void onPause() {
        super.onPause();
        unregisterReceiver(startSync);
        unregisterReceiver(stopSync);
        registered = false;
    }
}