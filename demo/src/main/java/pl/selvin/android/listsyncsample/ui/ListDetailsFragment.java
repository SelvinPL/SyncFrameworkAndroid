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
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import pl.selvin.android.listsyncsample.Constants.StringUtil;
import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.app.ListFragmentCommon;
import pl.selvin.android.listsyncsample.provider.Database.List;
import pl.selvin.android.listsyncsample.utils.Ui;

public class ListDetailsFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>,
        ListFragmentCommon.IDetailsUiProvider {
    private static final String ITEMS_FRAGMENT_TAG = "ITEMS_FRAGMENT_TAG";
    private Uri mItemUri;
    private EditText mName, mDescription;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.list_fragment, container, false);
        mName = Ui.getView(root, R.id.name);
        mDescription = Ui.getView(root, R.id.description);
        return root;
    }

    public Uri getItemUri() {
        return mItemUri == null ? mItemUri = getArguments().getParcelable(GenericDetailsActivity.ITEM_URI) : mItemUri;
    }

    @Override
    public void onResume() {
        super.onResume();
        getLoaderManager().initLoader(51000, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(getActivity(), getItemUri(),
                new String[]{List.NAME, List.DESCRIPTION, List.ID}, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (cursor.moveToFirst()) {
            mName.setText(cursor.getString(cursor.getColumnIndex(List.NAME)));
            mDescription.setText(cursor.getString(cursor.getColumnIndex(List.DESCRIPTION)));
            Fragment fragment = getChildFragmentManager().findFragmentByTag(ITEMS_FRAGMENT_TAG);
            if (fragment == null) {
                final Bundle fragmentArgs = ListFragmentCommon.Builder.createFromBundle(
                        ItemsListFragment.createArgs(cursor.getString(cursor.getColumnIndex(List.ID))))
                        .setSupportEdit(true).build();
                fragment = Fragment.instantiate(getActivity(), ItemsListFragment.class.getName(), null);
                fragment.setArguments(fragmentArgs);
                getChildFragmentManager().beginTransaction().add(R.id.items, fragment, ITEMS_FRAGMENT_TAG).commit();
            }
        } else
            getActivity().finish();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }

    @Override
    public void onPause() {
        super.onPause();
        final ContentValues values = new ContentValues();
        final String description = mDescription.getText().toString();
        final String name = mName.getText().toString();

        values.put(List.NAME, name);
        values.put(List.DESCRIPTION, description);
        getActivity().getContentResolver().update(getItemUri(), values,
                String.format("(%s<>? OR %s<>?)", List.NAME, List.DESCRIPTION), new String[]{name, description});
    }

    @Override
    public boolean showDetails(Uri itemUri, Class<? extends Fragment> fragmentClass, Bundle args, boolean editable) {
        final Bundle uriArgs = new Bundle();
        uriArgs.putParcelable(GenericDetailsActivity.ITEM_URI, itemUri);
        startActivity(GenericDetailsActivity.createIntent(getActivity(), fragmentClass, uriArgs));
        return true;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if(getActivity().isFinishing()){
            final String name = mName.getText().toString();
            final String description = mDescription.getText().toString();
            //if element is new and name and description is empty - delete item
            if(ListFragmentCommon.checkIsNewElement(getItemUri()) && StringUtil.EMPTY.equals(name) && StringUtil.EMPTY.equals(description))
                getActivity().getContentResolver().delete(getItemUri(), null,null);
        }
    }
}
