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
import android.widget.ListAdapter;

import androidx.annotation.NonNull;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

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
		super(createSetupBuilder(60000, Item.TABLE_NAME, R.string.items_list_empty)
				.detailsClass(ItemDetailsFragment.class)
				.deletionTitle(R.string.item_deletion_title)
				.deletionMessage(R.string.item_deletion_message)
				.create());
	}

	static Bundle createArgs(String id) {
		final Bundle args = new Bundle();
		args.putString(LIST_ID, id);
		return args;
	}

	private String getShownId() {
		return requireArguments().getString(LIST_ID);
	}

	@Override
	protected ListAdapter createListAdapter() {
		return new SimpleCursorAdapter(requireActivity(), R.layout.item_row,
				null, new String[]{Item.NAME,
				Item.DESCRIPTION}, new int[]{R.id.name,
				R.id.description}, 0);
	}

	@NonNull
	@Override
	protected Loader<Cursor> getLoader(Bundle args) {
		return new CursorLoader(requireActivity(),
				ListProvider.getHelper().getDirUri(Item.TABLE_NAME),
				new String[]{BaseColumns._ID, Item.ID,
						Item.NAME, Item.DESCRIPTION},
				Item.LIST_ID + "=?", new String[]{getShownId()},
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
		Calendar cal = DateTimeUtils.today();
		values.put(Item.START_DATE, DateTimeUtils.toLong(cal));
		cal.add(Calendar.DATE, 1);
		values.put(Item.END_DATE, DateTimeUtils.toLong(cal));
		values.put(Item.ID, UUID.randomUUID().toString());
		values.put(Item.LIST_ID, getShownId());
		values.put(Item.USER_ID, SyncService.getUserId(getActivity()));
		final Uri insertUri = requireActivity().getContentResolver().insert(ListProvider.getHelper().getDirUri(Item.TABLE_NAME), values);
		if (insertUri != null)
			return ListFragmentCommon.appendIsNewElement(insertUri);
		return null;
	}

	@Override
	protected Uri getItemUri(Cursor cursor, long id) {
		if (cursor.getLong(0) == id)
			return ListProvider.getHelper().getItemUri(Item.TABLE_NAME, false, cursor.getString(1));
		return super.getItemUri(cursor, id);
	}
}