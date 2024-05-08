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

import java.util.UUID;

import pl.selvin.android.listsyncsample.Constants.StringUtil;
import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.app.ListFragmentCommon;
import pl.selvin.android.listsyncsample.app.SearchComponent;
import pl.selvin.android.listsyncsample.provider.Database;
import pl.selvin.android.listsyncsample.provider.Database.List;
import pl.selvin.android.listsyncsample.provider.ListProvider;
import pl.selvin.android.listsyncsample.syncadapter.SyncService;
import pl.selvin.android.listsyncsample.utils.DateTimeUtils;

@SuppressWarnings("unused")
public class ListsListFragment extends ListFragmentCommon implements SearchComponent.Callback {
	private final static String SEARCH_VIEW_ICONIFIED = "SEARCH_VIEW_ICONIFIED";
	private static final String CURRENT_FILTER = "CURRENT_FILTER";
	private String currentFilter = StringUtil.EMPTY;

	public ListsListFragment() {
		super(createSetupBuilder(50000, List.TABLE_NAME, R.string.lists_list_empty)
				.detailsClass(ListDetailsFragment.class)
				.deletionTitle(R.string.list_deletion_title)
				.deletionMessage(R.string.list_deletion_message)
				.deferredLoading().create());
	}


	@NonNull
	@Override
	protected Loader<Cursor> getLoader(Bundle args) {
		return new CursorLoader(requireActivity(),
				ListProvider.getHelper().getDirUri(List.TABLE_NAME), new String[]{
				BaseColumns._ID, List.ID, List.NAME,
				List.DESCRIPTION, List.CREATED_DATE
		}, String.format("%s LIKE ?1 OR %s LIKE ?1", List.NAME, List.DESCRIPTION),
				new String[]{"%" + currentFilter + "%"}, List.NAME);
	}

	@Override
	protected boolean getEditable(Cursor cursor) {
		return true;
	}


	@Override
	protected ListAdapter createListAdapter() {
		return new SimpleCursorAdapter(requireActivity(), R.layout.list_row,
				null, new String[]{List.NAME,
				List.DESCRIPTION, List.CREATED_DATE},
				new int[]{R.id.name, R.id.description, R.id.created_date},
				0);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		SearchComponent.install(this, R.string.lists_search_hint);
	}

	@Override
	public Uri createNewElement(Bundle args) {
		ContentValues values = new ContentValues();
		values.put(Database.List.NAME, StringUtil.EMPTY);
		values.put(Database.List.DESCRIPTION, StringUtil.EMPTY);
		values.put(Database.List.ID, UUID.randomUUID().toString());
		values.put(Database.List.USER_ID, SyncService.getUserId(getActivity()));
		values.put(Database.List.CREATED_DATE, DateTimeUtils.getNowLong());
		final Uri insertUri = requireActivity().getContentResolver().insert(ListProvider.getHelper().getDirUri(List.TABLE_NAME, false), values);
		if (insertUri != null)
			return ListFragmentCommon.appendIsNewElement(insertUri);
		return null;
	}

	@Override
	protected Uri getItemUri(Cursor cursor, long id) {
		if (cursor.getLong(0) == id)
			return ListProvider.getHelper().getItemUri(List.TABLE_NAME, false, cursor.getString(1));
		return super.getItemUri(cursor, id);
	}

	@Override
	public void queryChanged(String query, boolean restored) {
		currentFilter = query;
		if (restored)

			initMainLoader();
		else
			restartMainLoader();
	}
}