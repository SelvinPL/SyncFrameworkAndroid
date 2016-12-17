/***
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
import android.widget.ListAdapter;

import java.util.Calendar;
import java.util.UUID;

import pl.selvin.android.listsyncsample.Constants.StringUtil;
import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.app.ListFragmentCommon;
import pl.selvin.android.listsyncsample.provider.Database.Item;
import pl.selvin.android.listsyncsample.provider.ListProvider;
import pl.selvin.android.listsyncsample.syncadapter.SyncService;
import pl.selvin.android.listsyncsample.utils.DateTimeUtils;

public class ItemsListFragment extends ListFragmentCommon {
    private static final String LIST_ID = "LIST_ID";

    public ItemsListFragment() {
        super(60000, Item.TABLE_NAME, R.string.items_list_empty, ItemDetailsFragment.class, R.string.item_deletion_title, R.string.item_deletion_message);
    }

    public static Bundle createArgs(String id) {
        final Bundle args = new Bundle();
        args.putString(LIST_ID, id);
        return args;
    }

    public String getShownId() {
        return getArguments().getString(LIST_ID);
    }

    @Override
    protected ListAdapter createListAdapter() {
        return new SimpleCursorAdapter(getActivity(), R.layout.item_row,
                null, new String[]{Item.NAME,
                Item.DESCRIPTION}, new int[]{R.id.name,
                R.id.description}, 0);
    }

    @Override
    protected Loader<Cursor> getLoader(Bundle args) {
        return new CursorLoader(getActivity(),
                ListProvider.getHelper().getDirUri(Item.TABLE_NAME),
                new String[]{BaseColumns._ID, Item.ID,
                        Item.NAME, Item.DESCRIPTION},
                Item.LISTID + "=?", new String[]{getShownId()},
                Item.NAME);
    }

    @Override
    protected boolean getEditable(Cursor cursor) {
        return true;
    }

    @Override
    public Uri createNewElement(Bundle args) {
        final ContentValues values = new ContentValues();
        values.put(Item.NAME, StringUtil.EMPTY);
        values.put(Item.DESCRIPTION, StringUtil.EMPTY);
        values.put(Item.PRIORITY, 1);
        values.put(Item.STATUS, 1);
        Calendar cal = DateTimeUtils.getToday();
        values.put(Item.STARTDATE, DateTimeUtils.toLong(cal));
        cal.add(Calendar.DATE, 1);
        values.put(Item.ENDDATE, DateTimeUtils.toLong(cal));
        values.put(Item.ID, UUID.randomUUID().toString());
        values.put(Item.LISTID, getShownId());
        values.put(Item.USERID, SyncService.getUserId(getActivity()));
        return getActivity().getContentResolver().insert(ListProvider.getHelper().getDirUri(Item.TABLE_NAME), values);
    }

    @Override
    protected Uri getItemUri(Cursor cursor, long id) {
        if (cursor.getLong(0) == id)
            return ListProvider.getHelper().getItemUri(Item.TABLE_NAME, false, cursor.getString(1));
        return super.getItemUri(cursor, id);
    }
}
