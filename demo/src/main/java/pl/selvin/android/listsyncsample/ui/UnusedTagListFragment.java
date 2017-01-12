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

import android.app.Dialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.provider.Database.Tag;
import pl.selvin.android.listsyncsample.provider.Database.TagItemMapping;
import pl.selvin.android.listsyncsample.provider.ListProvider;
import pl.selvin.android.syncframework.content.SYNC;

public class UnusedTagListFragment extends DialogFragment {

    private static final String USER_ID = "USER_ID";
    private static final String ITEM_ID = "ITEM_ID";

    public static UnusedTagListFragment newInstance(String itemId, String userId) {
        final UnusedTagListFragment frag = new UnusedTagListFragment();
        final Bundle args = new Bundle();
        args.putString(USER_ID, userId);
        args.putString(ITEM_ID, itemId);
        frag.setArguments(args);
        return frag;
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final SimpleCursorAdapter adapter = new SimpleCursorAdapter(getContext(),
                android.support.v7.appcompat.R.layout.support_simple_spinner_dropdown_item,
                getContext().getContentResolver().query(ListProvider.getHelper().getDirUri(Tag.TagNotUsed),
                        new String[]{BaseColumns._ID, Tag.NAME}, null,
                        new String[]{getArguments().getString(ITEM_ID)}, Tag.NAME),
                new String[]{Tag.NAME}, new int[]{android.R.id.text1}, 1) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View root = super.getView(position, convertView, parent);
                ((TextView) root).setTextColor(ResourcesCompat.getColor(getContext().getResources(),
                        android.R.color.white,
                        getContext().getTheme()));
                return root;
            }
        };
        return new AlertDialog.Builder(getContext()).setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int position) {
                final ContentValues values = new ContentValues(3);
                final long tagId = adapter.getItemId(position);
                final String userId = getArguments().getString(USER_ID);
                final String itemId = getArguments().getString(ITEM_ID);
                values.put(TagItemMapping.TAGID, tagId);
                values.put(TagItemMapping.USERID, userId);
                values.put(TagItemMapping.ITEMID, itemId);
                values.put(SYNC.isDeleted, 0);
                final Uri uri = ListProvider.getHelper().getDirUri(TagItemMapping.TABLE_NAME, false, true);
                getContext().getContentResolver().insert(uri, values);
            }
        }).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        }).setCancelable(true).setTitle(R.string.add_new_tag).create();
    }
}
