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

import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.provider.Database;
import pl.selvin.android.listsyncsample.provider.Database.Item;
import pl.selvin.android.listsyncsample.provider.ListProvider;


public class ListFragment extends android.support.v4.app.ListFragment implements
        LoaderManager.LoaderCallbacks<Cursor> {

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
        holder = new ViewHolder(root);
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
        getLoaderManager().initLoader(Constants.Loaders.Lists.Info, null, this);
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

    String listID = null;

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
            case Constants.Loaders.Lists.Info:
                return new CursorLoader(getActivity(), ListProvider.getHelper().getItemUri(
                        Database.List.TABLE_NAME, getShownId()), new String[]{
                        BaseColumns._ID, Database.List.NAME,
                        Database.List.DESCRIPTION, Database.List.CREATEDATE,
                        Database.List.ID}, null, null, null);
            case Constants.Loaders.Items.List:
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

            case Constants.Loaders.Lists.Info:
                if (data.moveToFirst() && holder != null) {
                    listID = data.getString(4);
                    holder.tName.setText(data.getString(1));
                    holder.tDescription.setText(data.getString(2));
                    holder.tCreatedDate.setText(data.getString(3));
                    getLoaderManager().initLoader(Constants.Loaders.Items.List,
                            null, this);
                }
                break;
            case Constants.Loaders.Items.List:
                ((SimpleCursorAdapter) getListAdapter()).swapCursor(data);
                if (isResumed()) {
                    setListShown(true);
                } else {
                    setListShownNoAnimation(true);
                }
                break;
        }
    }

    final static String EMPTY = "";

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        switch (loader.getId()) {

            case Constants.Loaders.Lists.Info:
                ((SimpleCursorAdapter) getListAdapter()).swapCursor(null);
                return;
            case Constants.Loaders.Items.List:
                if (holder != null) {
                    holder.tName.setText(EMPTY);
                    holder.tDescription.setText(EMPTY);
                    holder.tCreatedDate.setText(EMPTY);
                }
                listID = null;
        }

    }

    static class ViewHolder {
        public ViewHolder(View root) {
            tName = (TextView) root.findViewById(R.id.tName);
            tDescription = (TextView) root.findViewById(R.id.tDescription);
            tCreatedDate = (TextView) root.findViewById(R.id.tCreatedDate);
        }

        public TextView tName;
        public TextView tDescription;
        public TextView tCreatedDate;
    }

    ViewHolder holder = null;
}
