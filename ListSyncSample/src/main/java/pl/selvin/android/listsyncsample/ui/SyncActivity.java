package pl.selvin.android.listsyncsample.ui;

import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.support.SyncHelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

public class SyncActivity extends ActionBarActivity {
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

	boolean registered = true;


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

	final SyncHelper syncHelper = SyncHelper.createInstance(this);

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.refresh:
			syncHelper.doSync();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	volatile boolean showMenu = true;

	BroadcastReceiver startSync = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			showMenu = false;
			invalidateOptionsMenu();
		}
	};

	AnimationDrawable anim = null;
	BroadcastReceiver stopSync = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			showMenu = true;
			invalidateOptionsMenu();
		}
	};

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