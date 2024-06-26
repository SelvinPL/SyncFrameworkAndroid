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

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;

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
import pl.selvin.android.listsyncsample.provider.implementation.TagItemMappingWithNamesProvider;
import pl.selvin.android.listsyncsample.provider.implementation.TagNotUsedProvider;
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
	private static final int spinnerRowResource = androidx.appcompat.R.layout.support_simple_spinner_dropdown_item;
	private EditText mName, mDescription;
	private TextView mStartDate, mStartTime, mEndDate, mEndTime;
	private SpinnerHelper mPriority, mStatus;
	private TagsAdapter mTagsAdapter;
	private Uri mItemUri;
	private String mItemID;

	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		final View root = inflater.inflate(R.layout.item_fragment, container, false);
		mName = Ui.getView(root, R.id.name);
		mDescription = Ui.getView(root, R.id.description);
		mPriority = new SpinnerHelper(Ui.getView(root, R.id.priority), PRIORITIES_LOADER_ID,
				new SimpleCursorAdapter(requireActivity(), spinnerRowResource, null, new String[]{Priority.NAME},
						new int[]{android.R.id.text1}, 1), spinnerRowResource) {
			@Override
			public Loader<Cursor> getCursorLoader() {
				return new CursorLoader(requireActivity(), ListProvider.getHelper().getDirUri(Priority.TABLE_NAME, false),
						new String[]{BaseColumns._ID, Priority.ID, Priority.NAME}, null, null, Priority.ID + " ASC");
			}
		};

		mStatus = new SpinnerHelper(Ui.getView(root, R.id.status), STATUSES_LOADER_ID,
				new SimpleCursorAdapter(requireActivity(), spinnerRowResource, null, new String[]{Status.NAME},
						new int[]{android.R.id.text1}, 1), spinnerRowResource) {
			@Override
			public Loader<Cursor> getCursorLoader() {
				return new CursorLoader(requireActivity(), ListProvider.getHelper().getDirUri(Status.TABLE_NAME, false),
						new String[]{BaseColumns._ID, Status.ID, Status.NAME}, null, null, Status.ID + " ASC");
			}
		};

		final RecyclerView recyclerView = Ui.getView(root, R.id.tags);
		final FlexboxLayoutManager layoutManager = new FlexboxLayoutManager(getContext());
		layoutManager.setFlexDirection(FlexDirection.ROW);
		recyclerView.setLayoutManager(layoutManager);
		mTagsAdapter = new TagsAdapter(getChildFragmentManager(), SyncService.getUserId(getContext()));
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
		final LoaderManager loader = LoaderManager.getInstance(this);
		mPriority.initLoader(loader);
		mStatus.initLoader(loader);
		loader.initLoader(MAIN_LOADER_ID, null, this);
	}

	private Uri getItemUri() {
		return mItemUri == null ? mItemUri = requireArguments().getParcelable(GenericDetailsActivity.ITEM_URI) : mItemUri;
	}

	@NonNull
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		switch (id) {
			case MAIN_LOADER_ID:
				return new CursorLoader(requireContext(), getItemUri(), new String[]{Item.NAME,
						Item.DESCRIPTION, Item.PRIORITY,
						Item.STATUS, Item.START_DATE,
						Item.END_DATE, Item.ID}, null, null, null);
			case TAGS_LOADER_ID:
				return new CursorLoader(requireContext(), TagItemMappingWithNamesProvider.getDirUri(), new String[]{BaseColumns._ID,
						Tag.NAME}, TagItemMapping.ITEM_ID + "=?", new String[]{mItemID}, Tag.NAME);
			case UNUSED_LOADER_ID:
				return new CursorLoader(requireContext(), TagNotUsedProvider.getDirUri(),
						new String[]{BaseColumns._ID}, null, new String[]{mItemID}, null);
		}
		throw new RuntimeException("Unknown loader id: " + id);
	}

	@Override
	public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
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
					mStartTime.setTag(cal);
					if (cal != null) {
						mStartDate.setText(DateTimeUtils.toShort(cal));
						mStartTime.setText(DateTimeUtils.toTime(cal));
					}
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
					LoaderManager.getInstance(this).initLoader(TAGS_LOADER_ID, null, this);
					LoaderManager.getInstance(this).initLoader(UNUSED_LOADER_ID, null, this);
				} else
					requireActivity().finish();
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
	public void onLoaderReset(@NonNull Loader<Cursor> loader) {
		if (loader.getId() == TAGS_LOADER_ID) {
			mTagsAdapter.changeCursor(null);
		}
	}

	@Override
	public void onClick(View v) {
		final int vId = v.getId();
		if (vId == R.id.start_date || vId == R.id.end_date) {
			GenericDialogFragment.DatePicker.newInstance(vId, (Calendar) v.getTag()).show(getChildFragmentManager(), "DATE_PICKER_TAG_" + vId);
		} else if (vId == R.id.start_time || vId == R.id.end_time) {
			GenericDialogFragment.TimePicker.newInstance(vId, (Calendar) v.getTag()).show(getChildFragmentManager(), "TIME_PICKER_TAG_" + vId);
		}
	}

	@Override
	public boolean onAction(int ID, boolean canceled, Calendar date) {
		if (ID == R.id.start_date) {
			if (!canceled) {
				mStartDate.setText(DateTimeUtils.toShort(date));
				mStartDate.setTag(date);
				mStartTime.setTag(date);
			}
			return true;
		} else if (ID == R.id.end_date) {
			if (!canceled) {
				mEndDate.setText(DateTimeUtils.toShort(date));
				mEndDate.setTag(date);
				mEndTime.setTag(date);
			}
			return true;
		} else if (ID == R.id.start_time) {
			if (!canceled) {
				mStartTime.setText(DateTimeUtils.toTime(date));
				mStartDate.setTag(date);
				mStartTime.setTag(date);
			}
			return true;
		} else if (ID == R.id.end_time) {
			if (!canceled) {
				mEndTime.setText(DateTimeUtils.toTime(date));
				mEndDate.setTag(date);
				mEndTime.setTag(date);
			}
			return true;
		}
		return false;
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
		values.put(Item.START_DATE, startDate);
		where.append(" OR IFNULL(").append(Item.START_DATE).append(", '')!=?");
		whereArgs.add(startDate);
		values.put(Item.END_DATE, endDate);
		where.append(" OR IFNULL(").append(Item.END_DATE).append(", '')!=?");
		whereArgs.add(endDate);
		requireActivity().getContentResolver().update(getItemUri(), values, where.toString(), whereArgs.toArray(new String[0]));
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (requireActivity().isFinishing()) {
			final String name = mName.getText().toString();
			final String description = mDescription.getText().toString();
			//if element is new and name and description is empty - delete item
			if (ListFragmentCommon.checkIsNewElement(getItemUri()) && StringUtil.isNullOrWhitespace(name) && StringUtil.isNullOrWhitespace(description))
				requireActivity().getContentResolver().delete(getItemUri(), null, null);
		}
	}

	private static class TagsAdapter extends CursorRecyclerViewAdapter<TagsAdapter.ViewHolderBase> {
		private final FragmentManager mManager;
		private final String mUserId;
		private String mItemId;
		private boolean mNewItemVisibility = false;

		TagsAdapter(FragmentManager manager, String userId) {
			mManager = manager;
			mUserId = userId;
			setHasStableIds(true);
		}

		@Override
		public int getItemCount() {
			return super.getItemCount() + (mNewItemVisibility ? 1 : 0);
		}

		public int getItemViewType(int position) {
			return position == 0 && mNewItemVisibility ? R.layout.tags_row_new : R.layout.tags_row;
		}

		@SuppressLint("NotifyDataSetChanged")
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
			ViewGroup.LayoutParams lp = viewHolder.itemView.getLayoutParams();
			if (lp instanceof FlexboxLayoutManager.LayoutParams) {
				FlexboxLayoutManager.LayoutParams flexboxLp = (FlexboxLayoutManager.LayoutParams) viewHolder.itemView.getLayoutParams();
				flexboxLp.setFlexGrow(1.0f);
				flexboxLp.setAlignSelf(AlignItems.STRETCH);
			}
		}

		private int realPosition(int position) {
			return mNewItemVisibility ? position - 1 : position;
		}

		@Override
		public void onBindViewHolder(@NonNull ViewHolderBase viewHolder, int position) {
			if (position == 0 && mNewItemVisibility)
				return;
			super.onBindViewHolder(viewHolder, realPosition(position));
		}

		@NonNull
		@Override
		public ViewHolderBase onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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

		private class NewItemViewHolder extends ViewHolderBase {
			NewItemViewHolder(View itemView) {
				super(itemView);
			}

			@Override
			public void onClick(final View view) {
				UnusedTagListFragment.newInstance(mItemId, mUserId).show(mManager, "TAG");
			}
		}
	}
}