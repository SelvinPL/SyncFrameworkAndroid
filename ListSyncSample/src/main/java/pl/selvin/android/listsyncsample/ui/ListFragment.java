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
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.provider.Database;
import pl.selvin.android.listsyncsample.provider.Database.Item;
import pl.selvin.android.listsyncsample.provider.ListProvider;


public class ListFragment extends android.support.v4.app.ListFragment implements
        LoaderManager.LoaderCallbacks<Cursor> {

    final static String EMPTY = "";
    private final static int ListLoaderId = 1;
    private final static int ListItemsLoaderId = 2;
    TextView tName;
    TextView tDescription;
    TextView tCreatedDate;
    String listID = null;

    public static ListFragment newInstance(long id, boolean dual) {
        ListFragment f = new ListFragment();

        // Supply index input as an argument.
        Bundle args = new Bundle();
        args.putLong("id", id);
        args.putBoolean("dual", dual);
        f.setArguments(args);
        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // if (container == null) {
        // return null;
        // }
        View superroot = super.onCreateView(inflater, container,
                savedInstanceState);
        LinearLayout root = (LinearLayout) inflater.inflate(
                R.layout.list_activity, null);
        tName = (TextView) root.findViewById(R.id.tName);
        tDescription = (TextView) root.findViewById(R.id.tDescription);
        tCreatedDate = (TextView) root.findViewById(R.id.tCreatedDate);
        root.addView(superroot);
        if (!isDualPane())
            root.findViewById(R.id.header).setVisibility(View.VISIBLE);
        return root;
    }

    public Long getShownId() {
        return getArguments().getLong("id");
    }

    public boolean isDualPane() {
        return getArguments().getBoolean("dual");
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        getActivity().getMenuInflater().inflate(R.menu.item_edit_delete, menu);
        menu.setHeaderTitle("Item");
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (getShownId() != 0)
            inflater.inflate(R.menu.item, menu);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getLoaderManager().initLoader(ListLoaderId, null, this);
        registerForContextMenu(getListView());
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        setListAdapter(new SimpleCursorAdapter(getActivity(), R.layout.itemrow,
                null, new String[]{Database.Item.NAME,
                Database.Item.DESCRIPTION}, new int[]{R.id.tName,
                R.id.tDescription}, 0));
        setListShown(false);
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_edit:
                AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo) item
                        .getMenuInfo();
                Intent intent = new Intent(Intent.ACTION_EDIT,
                        ListProvider.getHelper().getItemUri(Database.Item.TABLE_NAME,
                                menuInfo.id), getActivity(), EditItemActivity.class);
                startActivity(intent);
                return true;
            case R.id.menu_item_delete:
                menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
                ConfirmDeleteDialog.newInstance(
                        R.string.ui_alert_delete_item,
                        ListProvider.getHelper().getItemUri(Database.Item.TABLE_NAME,
                                menuInfo.id)).show(getFragmentManager(), "dialog");
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ui_menu_newitem:
                if (listID != null) {
                    Intent intent = new Intent(Intent.ACTION_INSERT, null,
                            getActivity(), EditItemActivity.class);
                    intent.putExtra(Item.LISTID, listID);
                    startActivity(intent);
                } else {
                    Toast.makeText(getActivity(),
                            "Can't add new item to unsaved list!",
                            Toast.LENGTH_LONG).show();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Intent itemIntent = new Intent(getActivity(), ItemActivity.class);
        itemIntent.setData(ListProvider
                .getHelper().getItemUri(Database.Item.TABLE_NAME, id));
        startActivity(itemIntent);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case ListLoaderId:
                return new CursorLoader(getActivity(), ListProvider.getHelper().getItemUri(
                        Database.List.TABLE_NAME, getShownId()), new String[]{
                        BaseColumns._ID, Database.List.NAME,
                        Database.List.DESCRIPTION, Database.List.CREATEDATE,
                        Database.List.ID}, null, null, null);
            case ListItemsLoaderId:
                setListShown(false);
                return new CursorLoader(getActivity(),
                        ListProvider.getHelper().getDirUri(Database.Item.TABLE_NAME),
                        new String[]{BaseColumns._ID, Database.Item.ID,
                                Database.Item.NAME, Database.Item.DESCRIPTION},
                        Database.Item.LISTID + "=?", new String[]{listID},
                        Database.Item.NAME);
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        switch (loader.getId()) {

            case ListLoaderId:
                if (data.moveToFirst() && tName != null) {
                    listID = data.getString(4);
                    tName.setText(data.getString(1));
                    tDescription.setText(data.getString(2));
                    tCreatedDate.setText(data.getString(3));
                    getLoaderManager().initLoader(ListItemsLoaderId,
                            null, this);
                }
                break;
            case ListItemsLoaderId:
                ((SimpleCursorAdapter) getListAdapter()).swapCursor(data);
                if (isResumed()) {
                    setListShown(true);
                } else {
                    setListShownNoAnimation(true);
                }
                break;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        switch (loader.getId()) {

            case ListLoaderId:
                ((SimpleCursorAdapter) getListAdapter()).swapCursor(null);
                return;
            case ListItemsLoaderId:
                if (tName != null) {
                    tName.setText(EMPTY);
                    tDescription.setText(EMPTY);
                    tCreatedDate.setText(EMPTY);
                }
                listID = null;
        }

    }
}
