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
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;

import pl.selvin.android.listsyncsample.Constants.StringUtil;
import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.app.CursorRecyclerViewAdapter;
import pl.selvin.android.listsyncsample.app.GenericDialogFragment;
import pl.selvin.android.listsyncsample.app.ListFragmentCommon;
import pl.selvin.android.listsyncsample.provider.Database.Item;
import pl.selvin.android.listsyncsample.provider.Database.Priority;
import pl.selvin.android.listsyncsample.provider.Database.Status;
import pl.selvin.android.listsyncsample.provider.Database.Tag;
import pl.selvin.android.listsyncsample.provider.Database.TagItemMapping;
import pl.selvin.android.listsyncsample.provider.ListProvider;
import pl.selvin.android.listsyncsample.syncadapter.SyncService;
import pl.selvin.android.listsyncsample.utils.DateTimeUtils;
import pl.selvin.android.listsyncsample.utils.SpinnerHelper;
import pl.selvin.android.listsyncsample.utils.Ui;

public class ItemDetailsFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor>, GenericDialogFragment.DatePicker.Callback, GenericDialogFragment.TimePicker.Callback, View.OnClickListener {

    private static final int MAIN_LOADER_ID = 61000;
    private static final int PRIORITIES_LOADER_ID = 61001;
    private static final int STATUSES_LOADER_ID = 61002;
    private static final int TAGS_LOADER_ID = 61003;
    private static final int UNUSED_LOADER_ID = 61004;
    private EditText mName, mDescription;
    private TextView mStartDate, mStartTime, mEndDate, mEndTime;
    private SpinnerHelper mPriority, mStatus;
    private TagsAdapter mTagsAdapter;
    private Uri mItemUri;
    private String mItemID;
    private static final int spinnerRowResource = android.support.v7.appcompat.R.layout.support_simple_spinner_dropdown_item;

    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.item_fragment, container, false);
        mName = Ui.getView(root, R.id.name);
        mDescription = Ui.getView(root, R.id.description);
        mPriority = new SpinnerHelper(Ui.<Spinner>getView(root, R.id.priority), PRIORITIES_LOADER_ID,
                new SimpleCursorAdapter(getActivity(), spinnerRowResource, null, new String[]{Priority.NAME},
                        new int[]{android.R.id.text1}, 1), android.R.layout.simple_spinner_dropdown_item) {
            @Override
            public Loader<Cursor> getCursorLoader() {
                return new CursorLoader(getActivity(), ListProvider.getHelper().getDirUri(Priority.TABLE_NAME, false),
                        new String[]{BaseColumns._ID, Priority.ID, Priority.NAME}, null, null, Priority.ID + " ASC");
            }
        };

        mStatus = new SpinnerHelper(Ui.<Spinner>getView(root, R.id.status), STATUSES_LOADER_ID,
                new SimpleCursorAdapter(getActivity(), spinnerRowResource, null, new String[]{Status.NAME},
                        new int[]{android.R.id.text1}, 1), android.R.layout.simple_spinner_dropdown_item) {
            @Override
            public Loader<Cursor> getCursorLoader() {
                return new CursorLoader(getActivity(), ListProvider.getHelper().getDirUri(Status.TABLE_NAME, false),
                        new String[]{BaseColumns._ID, Status.ID, Status.NAME}, null, null, Status.ID + " ASC");
            }
        };

        RecyclerView recyclerView = Ui.getView(root, R.id.tags);
        final StaggeredGridLayoutManager gridLayoutManager = new StaggeredGridLayoutManager(4, StaggeredGridLayoutManager.HORIZONTAL);
        recyclerView.setLayoutManager(gridLayoutManager);
        mTagsAdapter = new TagsAdapter(getActivity(), getChildFragmentManager(), SyncService.getUserId(getContext()));
        recyclerView.setAdapter(mTagsAdapter);
        mStartDate = Ui.getView(root, R.id.start_date);
        mStartTime = Ui.getView(root, R.id.start_time);
        mEndDate = Ui.getView(root, R.id.end_date);
        mEndTime = Ui.getView(root, R.id.end_time);

        mStartDate.setOnClickListener(this);
        mStartTime.setOnClickListener(this);
        mEndDate.setOnClickListener(this);
        mEndTime.setOnClickListener(this);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        final LoaderManager loader = getLoaderManager();
        mPriority.initLoader(loader);
        mStatus.initLoader(loader);
        loader.initLoader(MAIN_LOADER_ID, null, this);
    }

    public Uri getItemUri() {
        return mItemUri == null ? mItemUri = getArguments().getParcelable(GenericDetailsActivity.ITEM_URI) : mItemUri;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case MAIN_LOADER_ID:
                return new CursorLoader(getActivity(), getItemUri(), new String[]{Item.NAME,
                        Item.DESCRIPTION, Item.PRIORITY,
                        Item.STATUS, Item.STARTDATE,
                        Item.ENDDATE, Item.ID}, null, null, null);
            case TAGS_LOADER_ID:
                return new CursorLoader(getActivity(), ListProvider.getHelper().getDirUri(TagItemMapping.TagItemMappingWithNames, false), new String[]{BaseColumns._ID,
                        Tag.NAME}, TagItemMapping.ITEMID + "=?", new String[]{mItemID}, Tag.NAME);
            case UNUSED_LOADER_ID:
                return new CursorLoader(getContext(), ListProvider.getHelper().getDirUri(Tag.TagNotUsed),
                        new String[]{BaseColumns._ID}, null, new String[]{mItemID}, null);
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        switch (loader.getId()) {
            case MAIN_LOADER_ID:
                if (cursor.moveToFirst()) {
                    mName.setText(cursor.getString(0));
                    mDescription.setText(cursor.getString(1));
                    mPriority.setSelectedId(cursor.getInt(2));
                    mStatus.setSelectedId(cursor.getInt(3));
                    String time = cursor.getString(4);
                    Calendar cal = null;
                    try {
                        cal = DateTimeUtils.fromLong(time);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    mStartDate.setTag(cal);
                    mStartDate.setText(DateTimeUtils.toShort(cal));
                    mStartTime.setTag(cal);
                    mStartTime.setText(DateTimeUtils.toTime(cal));
                    time = cursor.getString(5);
                    cal = Calendar.getInstance();
                    try {
                        cal = DateTimeUtils.fromLong(time);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    mEndDate.setTag(cal);
                    mEndDate.setText(DateTimeUtils.toShort(cal));
                    mEndTime.setTag(cal);
                    mEndTime.setText(DateTimeUtils.toTime(cal));
                    mItemID = cursor.getString(6);
                    mTagsAdapter.setItemId(mItemID);
                    getLoaderManager().initLoader(TAGS_LOADER_ID, null, this);
                    getLoaderManager().initLoader(UNUSED_LOADER_ID, null, this);
                } else
                    getActivity().finish();
                break;
            case TAGS_LOADER_ID:
                mTagsAdapter.swapCursor(cursor);
                break;
            case UNUSED_LOADER_ID:
                mTagsAdapter.setNewItemVisible(cursor.moveToFirst());
                break;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        switch (loader.getId()) {
            case TAGS_LOADER_ID:
                mTagsAdapter.changeCursor(null);
                break;
        }
    }

    @Override
    public void onClick(View v) {
        final int vId = v.getId();
        switch (vId) {
            case R.id.start_date:
            case R.id.end_date:
                GenericDialogFragment.DatePicker.newInstance(vId, (Calendar) v.getTag()).show(getChildFragmentManager(), "DATE_PICKER_TAG_" + vId);
                break;
            case R.id.start_time:
            case R.id.end_time:
                GenericDialogFragment.TimePicker.newInstance(vId, (Calendar) v.getTag()).show(getChildFragmentManager(), "TIME_PICKER_TAG_" + vId);
                break;
        }
    }

    @Override
    public boolean onAction(int ID, boolean canceled, Calendar date) {
        if (!canceled) {
            switch (ID) {
                case R.id.start_date:
                    mStartDate.setText(DateTimeUtils.toShort(date));
                    mStartDate.setTag(date);
                    mStartTime.setTag(date);
                    break;
                case R.id.end_date:
                    mEndDate.setText(DateTimeUtils.toShort(date));
                    mEndDate.setTag(date);
                    mEndTime.setTag(date);
                    break;
                case R.id.start_time:
                    mStartTime.setText(DateTimeUtils.toTime(date));
                    mStartDate.setTag(date);
                    mStartTime.setTag(date);
                    break;
                case R.id.end_time:
                    mEndTime.setText(DateTimeUtils.toTime(date));
                    mEndDate.setTag(date);
                    mEndTime.setTag(date);
                    break;
            }
        }
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        final String name = mName.getText().toString();
        final String description = mDescription.getText().toString();
        final long priority = mPriority.Spinner.getSelectedItemId();
        final long status = mStatus.Spinner.getSelectedItemId();
        final String startDate = DateTimeUtils.toLong((Calendar) mStartDate.getTag());
        final String endDate = DateTimeUtils.toLong((Calendar) mEndDate.getTag());
        final ContentValues values = new ContentValues();
        final StringBuilder where = new StringBuilder();
        final ArrayList<String> whereArgs = new ArrayList<>(6);
        values.put(Item.NAME, name);
        where.append(Item.NAME).append("!=?");
        whereArgs.add(name);
        values.put(Item.DESCRIPTION, description);
        where.append(" OR IFNULL(").append(Item.DESCRIPTION).append(", '')!=?");
        whereArgs.add(description);
        values.put(Item.PRIORITY, priority);
        where.append(" OR ").append(Item.PRIORITY).append("!=?");
        whereArgs.add(Long.toString(priority));
        values.put(Item.STATUS, status);
        where.append(" OR ").append(Item.STATUS).append("!=?");
        whereArgs.add(Long.toString(status));
        values.put(Item.STARTDATE, startDate);
        where.append(" OR IFNULL(").append(Item.STARTDATE).append(", '')!=?");
        whereArgs.add(startDate);
        values.put(Item.ENDDATE, endDate);
        where.append(" OR IFNULL(").append(Item.ENDDATE).append(", '')!=?");
        whereArgs.add(endDate);
        getActivity().getContentResolver().update(getItemUri(), values, where.toString(), whereArgs.toArray(new String[whereArgs.size()]));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (getActivity().isFinishing()) {
            final String name = mName.getText().toString();
            final String description = mDescription.getText().toString();
            //if element is new and name and description is empty - delete item
            if (ListFragmentCommon.checkIsNewElement(getItemUri()) && StringUtil.EMPTY.equals(name) && StringUtil.EMPTY.equals(description))
                getActivity().getContentResolver().delete(getItemUri(), null, null);
        }
    }

    private static class TagsAdapter extends CursorRecyclerViewAdapter<TagsAdapter.ViewHolderBase> {
        private final FragmentManager mManager;
        private final String mUserId;
        private String mItemId;
        private boolean mNewItemVisibility = false;

        @Override
        public int getItemCount() {
            return super.getItemCount() + (mNewItemVisibility ? 1 : 0);
        }

        public int getItemViewType(int position) {
            return position == 0 && mNewItemVisibility ? R.layout.tags_row_new : R.layout.tags_row;
        }

        TagsAdapter(Context context, FragmentManager manager, String userId) {
            super(context, null);
            mManager = manager;
            mUserId = userId;
            setHasStableIds(true);
        }

        void setNewItemVisible(boolean newItemVisibility) {
            final boolean old = mNewItemVisibility;
            mNewItemVisibility = newItemVisibility;
            if (old != newItemVisibility) {
                notifyDataSetChanged();
            }
        }

        @Override
        public long getItemId(int position) {
            return position == 0 && mNewItemVisibility ? -1 : super.getItemId(realPosition(position));
        }

        @Override
        public void onBindViewHolder(ViewHolderBase viewHolder, Cursor cursor) {
            ((TextView) viewHolder.itemView).setText(cursor.getString(1));
        }

        private int realPosition(int position) {
            return mNewItemVisibility ? position - 1 : position;
        }

        @Override
        public void onBindViewHolder(ViewHolderBase viewHolder, int position) {
            if (position == 0 && mNewItemVisibility)
                return;
            super.onBindViewHolder(viewHolder, realPosition(position));
        }

        @Override
        public ViewHolderBase onCreateViewHolder(ViewGroup parent, int viewType) {
            final View itemView = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
            if (viewType == R.layout.tags_row) {
                return new ItemViewHolder(itemView);
            } else {
                return new NewItemViewHolder(itemView);
            }
        }

        void setItemId(String itemId) {
            mItemId = itemId;
        }

        static abstract class ViewHolderBase extends RecyclerView.ViewHolder implements android.view.View.OnClickListener {
            ViewHolderBase(View itemView) {
                super(itemView);
                itemView.setOnClickListener(this);
            }
        }

        private class NewItemViewHolder extends ViewHolderBase {
            NewItemViewHolder(View itemView) {
                super(itemView);
            }

            @Override
            public void onClick(final View view) {
                UnusedTagListFragment.newInstance(mItemId, mUserId).show(mManager, "TAG");
            }
        }

        private static class ItemViewHolder extends ViewHolderBase {

            ItemViewHolder(View itemView) {
                super(itemView);
            }

            @Override
            public void onClick(final View view) {
                final long id = getItemId();
                view.getContext().getContentResolver().delete(ListProvider.getHelper().getDirUri(TagItemMapping.TABLE_NAME, false), "ROWID=?", new String[]{Long.toString(id)});
            }
        }
    }
}
