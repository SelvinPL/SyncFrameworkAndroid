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

package pl.selvin.android.listsyncsample.ui;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.provider.Database;
import pl.selvin.android.listsyncsample.provider.ListProvider;

public class ListsListFragment extends android.support.v4.app.ListFragment implements
        LoaderManager.LoaderCallbacks<Cursor> {
    private static final int ListsListLoaderId = 1;
    boolean mDualPane;
    int mCurIndex;
    long mCurId;

    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getLoaderManager().initLoader(ListsListLoaderId, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(getActivity(),
                ListProvider.getHelper().getDirUri(Database.List.TABLE_NAME), new String[]{
                BaseColumns._ID, Database.List.NAME,
                Database.List.DESCRIPTION, Database.List.CREATEDATE},
                null, null, Database.List.NAME);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        ((SimpleCursorAdapter) getListAdapter()).swapCursor(data);
        if (isResumed()) {
            setListShown(true);
        } else {
            setListShownNoAnimation(true);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        ((SimpleCursorAdapter) getListAdapter()).swapCursor(null);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ui_menu_newlist:
                Intent intent = new Intent(Intent.ACTION_INSERT, null,
                        getActivity(), EditListActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        setListAdapter(new SimpleCursorAdapter(getActivity(), R.layout.listrow,
                null, new String[]{Database.List.NAME,
                Database.List.DESCRIPTION, Database.List.CREATEDATE},
                new int[]{R.id.tName, R.id.tDescription, R.id.tCreatedDate},
                0));
        setListShown(false);
        registerForContextMenu(getListView());
        View detailsFrame = getActivity().findViewById(R.id.details);
        mDualPane = detailsFrame != null
                && detailsFrame.getVisibility() == View.VISIBLE;
        if (savedInstanceState != null) {
            mCurIndex = savedInstanceState.getInt("index");
            mCurId = savedInstanceState.getLong("id");
        }

        if (mDualPane) {
            getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            showDetails(mCurIndex, mCurId);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("index", mCurIndex);
        outState.putLong("id", mCurId);
    }

    void showDetails(int index, long id) {
        mCurIndex = index;
        mCurId = id;
        if (mDualPane) {
            getListView().setItemChecked(index, true);
            ListFragment details = (ListFragment) getFragmentManager()
                    .findFragmentById(R.id.details);
            if (details == null || details.getShownId() != id) {
                details = ListFragment.newInstance(id, mDualPane);
                FragmentTransaction ft = getFragmentManager()
                        .beginTransaction();
                ft.replace(R.id.details, details);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                ft.commit();

            }
        } else {
            // CustomersListFragment details = new CustomersListFragment();
            // details.show(getFragmentManager(), "dialog");
            Intent listIntent = new Intent(getActivity(), ListActivity.class);
            listIntent.setData(ListProvider.getHelper().getItemUri(
                    Database.List.TABLE_NAME, id));
            startActivity(listIntent);
        }
        // }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        getActivity().getMenuInflater().inflate(R.menu.list_edit_delete, menu);
        menu.setHeaderTitle("List");
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        showDetails(position, id);
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_list_edit:
                AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo) item
                        .getMenuInfo();
                Intent intent = new Intent(Intent.ACTION_EDIT,
                        ListProvider.getHelper().getItemUri(Database.List.TABLE_NAME,
                                menuInfo.id), getActivity(), EditListActivity.class);
                startActivity(intent);
                return true;
            case R.id.menu_list_delete:
                menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
                ConfirmDeleteDialog.newInstance(
                        R.string.ui_alert_delete_list,
                        ListProvider.getHelper().getItemUri(Database.List.TABLE_NAME,
                                menuInfo.id)).show(getFragmentManager(), "dialog");
                return true;
        }
        return false;

    }
}
