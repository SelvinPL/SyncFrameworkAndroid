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

import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;

import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.provider.Database.Tag;
import pl.selvin.android.listsyncsample.provider.Database.TagItemMapping;
import pl.selvin.android.listsyncsample.provider.ListProvider;
import pl.selvin.android.syncframework.content.SYNC;

public class UnusedTagListFragment extends DialogFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String USER_ID = "USER_ID";
    private static final String ITEM_ID = "ITEM_ID";
    private SimpleCursorAdapter adapter;

    public static UnusedTagListFragment newInstance(String itemId, String userId) {
        final UnusedTagListFragment frag = new UnusedTagListFragment();
        final Bundle args = new Bundle();
        args.putString(USER_ID, userId);
        args.putString(ITEM_ID, itemId);
        frag.setArguments(args);
        return frag;
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Context ctx = new ContextThemeWrapper(getContext(), R.style.AppTheme_Dialog_Alert);
        adapter = new SimpleCursorAdapter(ctx,
                androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, null,
                new String[]{Tag.NAME}, new int[]{android.R.id.text1}, 1);
        getLoaderManager().initLoader(0, null, this);
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        return new AlertDialog.Builder(getContext(), R.style.AppTheme_Dialog_Alert).setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int position) {
                final ContentValues values = new ContentValues(3);
                final long tagId = adapter.getItemId(position);
                final String userId = getArguments().getString(USER_ID);
                final String itemId = getArguments().getString(ITEM_ID);
                values.put(TagItemMapping.TAG_ID, tagId);
                values.put(TagItemMapping.USER_ID, userId);
                values.put(TagItemMapping.ITEM_ID, itemId);
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

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(getContext(), ListProvider.getHelper().getDirUri(Tag.TagNotUsed),
                new String[]{BaseColumns._ID, Tag.NAME}, null,
                new String[]{getArguments().getString(ITEM_ID)}, Tag.NAME);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        adapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        adapter.swapCursor(null);
    }
}
