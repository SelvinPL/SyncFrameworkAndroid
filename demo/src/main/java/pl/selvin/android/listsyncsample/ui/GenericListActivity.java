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

package pl.selvin.android.listsyncsample.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import androidx.fragment.app.Fragment;
import android.view.MenuItem;

import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.app.IListFragmentCommon;
import pl.selvin.android.listsyncsample.app.ListFragmentCommon;

public class GenericListActivity extends BaseActivity implements ListFragmentCommon.IDetailsUiProvider {

    private static final String FRAGMENT_CLASS = "fragment_class";
    private static final String SUPPORT_ADDING = "support_adding";
    private static final String HOME_AS_UP = "home_as_up";
    private static final String FRAGMENT_ARGS = "fragment_args";
    private final static String LIST_FRAGMENT_TAG = "LIST_FRAGMENT_TAG";
    private IListFragmentCommon listFragment = null;
    private ISearchSupport searchSupport = null;

    public static Intent createPickIntent(Context context, Class<?> fragmentClass, Bundle fragmentArgs, Parcelable passThrough) {
        final Intent ret = new Intent(context, GenericListActivity.class);
        ret.setAction(Intent.ACTION_PICK);
        ret.putExtra(FRAGMENT_CLASS, fragmentClass.getName());
        ret.putExtra(FRAGMENT_ARGS, ListFragmentCommon.Builder.createFromBundle(fragmentArgs).setSupportPick(true).putPassThrough(passThrough).build());
        return ret;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        boolean pick = false;
        if (Intent.ACTION_PICK.equals(intent.getAction()))
            pick = true;
        setContentView(R.layout.activity_list);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Bundle extras = intent.getExtras();
        listFragment = (IListFragmentCommon) getSupportFragmentManager().findFragmentByTag(LIST_FRAGMENT_TAG);
        if (savedInstanceState == null) {
            Bundle fragmentArgs = extras == null ? null : extras.getBundle(FRAGMENT_ARGS);
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
                final boolean homeAsUp = extras.getBoolean(HOME_AS_UP, true);
                if (getSupportActionBar() != null)
                    getSupportActionBar().setDisplayHomeAsUpEnabled(homeAsUp);
                final boolean supportAdding = extras.getBoolean(SUPPORT_ADDING, true);
                try {
                    if (fragmentArgs == null)
                        fragmentArgs = ListFragmentCommon.Builder.create().setSupportPick(pick).setSupportEdit(supportAdding && !pick).build();
                    else
                        fragmentArgs = ListFragmentCommon.Builder.createFromBundle(fragmentArgs).setSupportPick(pick).setSupportEdit(supportAdding && !pick).build();
                    listFragment = (IListFragmentCommon) Fragment.instantiate(this, className, null);
                    listFragment.setArguments(fragmentArgs);
                    getSupportFragmentManager().beginTransaction().add(R.id.list, (Fragment) listFragment, LIST_FRAGMENT_TAG).commit();
                    if (!pick)
                        startSync(500, false);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    finish();
                }
            } else {
                finish();
            }
        } else {
            listFragment = (IListFragmentCommon) getSupportFragmentManager().findFragmentByTag(LIST_FRAGMENT_TAG);
        }
        if (listFragment instanceof ISearchSupport)
            searchSupport = (ISearchSupport) listFragment;
    }

    @Override
    public boolean onSearchRequested() {
        if (searchSupport != null) {
            searchSupport.onSearchRequested();
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (searchSupport != null) {
            if (searchSupport.backOrHideSearch()) {
                return;
            }
        }
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean showDetails(Uri itemUri, Class<? extends Fragment> fragmentClass, Bundle args, boolean editable) {
        final Bundle uriArgs = new Bundle();
        uriArgs.putParcelable(GenericDetailsActivity.ITEM_URI, itemUri);
        startActivity(GenericDetailsActivity.createIntent(this, listFragment.getDetailsClass(), uriArgs));
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        return true;
    }

    public interface ISearchSupport {
        void onSearchRequested();

        boolean backOrHideSearch();
    }
}
