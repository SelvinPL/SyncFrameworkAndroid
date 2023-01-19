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

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;

import pl.selvin.android.listsyncsample.R;

public class GenericDetailsActivity extends BaseActivity {

	public final static String ITEM_URI = "ITEM_URI";
	public static final String GENERIC_DETAILS = "GENERIC_DETAILS";
	private static final String FRAGMENT_CLASS = "fragment_class";
	private static final String FRAGMENT_ARGS = "fragment_args";

	public static Intent createIntent(Context context, Class<?> fragmentClass, Bundle fragmentArgs) {
		final Intent ret = new Intent(context, GenericDetailsActivity.class);
		ret.putExtra(FRAGMENT_CLASS, fragmentClass.getName());
		ret.putExtra(FRAGMENT_ARGS, fragmentArgs);
		return ret;
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_empty);
		if (getSupportActionBar() != null)
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		final Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		if (savedInstanceState == null) {
			Bundle args = extras == null ? null : extras.getBundle(FRAGMENT_ARGS);
			if (extras == null || !extras.containsKey(FRAGMENT_CLASS)) {
				try {
					final ActivityInfo app = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
					extras = app.metaData;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			if (extras != null) {
				final String className = extras.getString(FRAGMENT_CLASS);
				try {
					Fragment fragment = getSupportFragmentManager().findFragmentByTag("generic");
					if (fragment == null) {
						if (args == null)
							args = new Bundle();
						args.putBoolean(GENERIC_DETAILS, true);
						if (intent.getData() != null)
							args.putParcelable(ITEM_URI, intent.getData());
						final FragmentFactory fragmentFactory = getSupportFragmentManager().getFragmentFactory();
						fragment = fragmentFactory.instantiate(ClassLoader.getSystemClassLoader(), className);
						fragment.setArguments(args);
					}
					getSupportFragmentManager().beginTransaction().replace(R.id.content, fragment, "generic").commit();
				} catch (Exception ex) {
					ex.printStackTrace();
					finish();
				}
			} else {
				finish();
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
