/*
 Copyright (c) 2014-2016 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */

package pl.selvin.android.listsyncsample.utils;

import android.database.Cursor;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Spinner;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.cursoradapter.widget.ResourceCursorAdapter;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

public abstract class SpinnerHelper implements LoaderManager.LoaderCallbacks<Cursor> {

	public final Spinner Spinner;
	private final int mLoaderID;
	private boolean mDataLoaded = false;
	private long mId = AdapterView.INVALID_ROW_ID;
	private String mValue = null;
	private String mColumn = BaseColumns._ID;


	public SpinnerHelper(Spinner spinner, int loaderID, ResourceCursorAdapter adapter, @LayoutRes int dropDownLayout) {
		this(spinner, loaderID, adapter, dropDownLayout, null);
	}

	public SpinnerHelper(Spinner spinner, int loaderID, ResourceCursorAdapter adapter, @LayoutRes int dropDownLayout, OnItemSelectedListener onSelected) {
		Spinner = spinner;
		mLoaderID = loaderID;
		Spinner.setOnItemSelectedListener(onSelected);
		Spinner.setAdapter(adapter);
		adapter.setDropDownViewResource(dropDownLayout);

	}


	public void initLoader(final LoaderManager loaderManager) {
		initLoader(loaderManager, null);
	}

	public void initLoader(final LoaderManager loaderManager, Bundle bundle) {
		mDataLoaded = false;
		loaderManager.initLoader(mLoaderID, bundle, this);
	}

	public abstract Loader<Cursor> getCursorLoader();

	public void setSelectedId(long id) {
		mColumn = BaseColumns._ID;
		mId = id;
		mValue = null;
		setSelectionById(mDataLoaded, id, mColumn);
	}

	private void setSelectionByValue(String value, final String columnName) {
		AdapterViewHelper.setSelectionByValue(Spinner, true, value, columnName);
	}

	private void setSelectionById(final boolean loaded, long id, final String columnName) {
		AdapterViewHelper.setSelectionById(Spinner, loaded, id, columnName);
	}

	@NonNull
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		if (id == mLoaderID) {
			return getCursorLoader();
		}
		throw new RuntimeException("Wrong loader id!");
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
		if (loader.getId() == mLoaderID) {
			((CursorAdapter) Spinner.getAdapter()).swapCursor(cursor);
			setSelectionByValue(mValue, mColumn);
			setSelectionById(true, mId, mColumn);
			mDataLoaded = true;
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		if (loader.getId() == mLoaderID) {
			final Cursor oldCursor = ((CursorAdapter) Spinner.getAdapter()).swapCursor(null);
			if (oldCursor != null)
				oldCursor.close();
			mDataLoaded = false;
		}
	}
}
