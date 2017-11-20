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

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.widget.SearchView;
import android.view.View;
import android.widget.ListAdapter;

import java.util.UUID;

import pl.selvin.android.listsyncsample.Constants.StringUtil;
import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.app.ListFragmentCommon;
import pl.selvin.android.listsyncsample.provider.Database;
import pl.selvin.android.listsyncsample.provider.Database.List;
import pl.selvin.android.listsyncsample.provider.ListProvider;
import pl.selvin.android.listsyncsample.syncadapter.SyncService;
import pl.selvin.android.listsyncsample.utils.DateTimeUtils;
import pl.selvin.android.listsyncsample.utils.Ui;

public class ListsListFragment extends ListFragmentCommon implements SearchView.OnQueryTextListener,
        GenericListActivity.ISearchSupport {
    final static String SEARCH_VIEW_ICONIFIED = "SEARCH_VIEW_ICONIFIED";
    private static final String CURRENT_FILTER = "CURRENT_FILTER";
    SearchView mSearchView = null;
    String mCurrentFilter = StringUtil.EMPTY;

    public ListsListFragment() {
        super(50000, List.TABLE_NAME, R.string.lists_list_empty, ListDetailsFragment.class, R.string.list_deletion_title, R.string.list_deletion_message);
    }


    @Override
    protected Loader<Cursor> getLoader(Bundle args) {
        return new CursorLoader(getActivity(),
                ListProvider.getHelper().getDirUri(List.TABLE_NAME), new String[]{
                BaseColumns._ID, List.ID, List.NAME,
                List.DESCRIPTION, List.CREATED_DATE
        }, String.format("%s LIKE ?1 OR %s LIKE ?1", List.NAME, List.DESCRIPTION),
                new String[]{"%" + mCurrentFilter + "%"}, List.NAME);
    }

    @Override
    protected boolean getEditable(Cursor cursor) {
        return true;
    }


    @Override
    protected ListAdapter createListAdapter() {
        return new SimpleCursorAdapter(getActivity(), R.layout.list_row,
                null, new String[]{List.NAME,
                List.DESCRIPTION, List.CREATED_DATE},
                new int[]{R.id.name, R.id.description, R.id.created_date},
                0);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (savedInstanceState != null) {
            mCurrentFilter = savedInstanceState.getString(CURRENT_FILTER);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSearchView = Ui.getView(getActivity(), R.id.search_view);
        mSearchView.setVisibility(View.VISIBLE);
        mSearchView.setQueryHint(getString(R.string.lists_search_hint));
        mSearchView.setOnQueryTextListener(this);
        if (savedInstanceState != null)
            mSearchView.setIconified(savedInstanceState.getBoolean(SEARCH_VIEW_ICONIFIED, true));

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSearchView != null)
            outState.putBoolean(SEARCH_VIEW_ICONIFIED, mSearchView.isIconified());
        outState.putString(CURRENT_FILTER, mCurrentFilter);
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (!mCurrentFilter.equals(newText)) {
            mCurrentFilter = newText;
            getLoaderManager().restartLoader(loaderID, null, this);
        }
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public void onSearchRequested() {

        if (mSearchView != null && mSearchView.isIconified())
            mSearchView.setIconified(false);
    }

    @Override
    public boolean backOrHideSearch() {
        if (mSearchView != null) {
            if (!mSearchView.isIconified()) {
                mSearchView.setIconified(true);
                mSearchView.post(new Runnable() {
                    @Override
                    public void run() {
                        mSearchView.clearFocus();
                        mSearchView.setIconified(true);
                    }
                });
                return true;
            }
        }
        return false;
    }

    @Override
    public Uri createNewElement(Bundle args) {
        ContentValues values = new ContentValues();
        values.put(Database.List.NAME, StringUtil.EMPTY);
        values.put(Database.List.DESCRIPTION, StringUtil.EMPTY);
        values.put(Database.List.ID, UUID.randomUUID().toString());
        values.put(Database.List.USER_ID, SyncService.getUserId(getActivity()));
        values.put(Database.List.CREATED_DATE, DateTimeUtils.getNowLong());
        return getActivity().getContentResolver().insert(ListProvider.getHelper().getDirUri(List.TABLE_NAME, false), values);
    }

    @Override
    protected Uri getItemUri(Cursor cursor, long id) {
        if (cursor.getLong(0) == id)
            return ListProvider.getHelper().getItemUri(List.TABLE_NAME, false, cursor.getString(1));
        return super.getItemUri(cursor, id);
    }
}
