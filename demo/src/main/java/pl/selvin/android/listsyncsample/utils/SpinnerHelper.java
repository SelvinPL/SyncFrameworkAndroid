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
import androidx.annotation.LayoutRes;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.cursoradapter.widget.ResourceCursorAdapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Spinner;

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

    public void restartLoader(final LoaderManager loaderManager) {
        restartLoader(loaderManager, null);
    }

    public void restartLoader(final LoaderManager loaderManager, Bundle bundle) {
        mDataLoaded = false;
        loaderManager.restartLoader(mLoaderID, bundle, this);
    }

    public abstract Loader<Cursor> getCursorLoader();

    public void setSelectedId(long id) {
        mColumn = BaseColumns._ID;
        mId = id;
        mValue = null;
        setSelectionById(mDataLoaded, id, mColumn);
    }

    public void setSelectedValue(String value, String columnName) {
        mValue = value;
        mColumn = columnName;
        mId = AdapterView.INVALID_ROW_ID;
        setSelectionByValue(mDataLoaded, value, columnName);
    }

    private void setSelectionByValue(final boolean loaded, String value, final String columnName) {
        AdapterViewHelper.setSelectionByValue(Spinner, loaded, value, columnName);
    }

    private void setSelectionById(final boolean loaded, long id, final String columnName) {
        AdapterViewHelper.setSelectionById(Spinner, loaded, id, columnName);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id == mLoaderID) {
            return getCursorLoader();
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (loader.getId() == mLoaderID) {
            ((CursorAdapter) Spinner.getAdapter()).swapCursor(cursor);
            setSelectionByValue(true, mValue, mColumn);
            setSelectionById(true, mId, mColumn);
            mDataLoaded = true;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (loader.getId() == mLoaderID) {
            ((CursorAdapter) Spinner.getAdapter()).swapCursor(null);
            mDataLoaded = false;
        }
    }

    public boolean isDataLoaded() {
        return mDataLoaded;
    }
}
