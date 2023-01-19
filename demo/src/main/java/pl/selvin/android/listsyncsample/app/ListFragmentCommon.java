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

package pl.selvin.android.listsyncsample.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.ListFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.AsyncTaskLoader;
import androidx.loader.content.Loader;

import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;

import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.provider.ListProvider;
import pl.selvin.android.listsyncsample.utils.StaticHelpers;
import pl.selvin.android.listsyncsample.utils.Ui;

@SuppressWarnings({"WeakerAccess, unused", "RedundantSuppression"})
public abstract class ListFragmentCommon extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor>, IListFragmentCommon, GenericDialogFragment.ConfirmDelete.Callback {
	public final static String EXTRA_ID = "EXTRA_ID";
	public final static String EXTRA_POS = "EXTRA_POS";
	public final static String EXTRA_ROW = "EXTRA_ROW";
	public final static String EXTRA_PASS_THROUGH = "EXTRA_PASS_THROUGH";
	private final static String ID = "LIST_COMMON_ID";

	private final static int DELETE_CALLBACK_ID = 111111;

	private static final String IS_NEW_ELEMENT = "IS_NEW_ELEMENT";
	private static final String TRUE = "true";
	protected final String tableName;
	protected final int pickTitle;
	protected final boolean supportDetails;
	protected final int emptyText;
	protected final int loaderID;
	protected final int insertLoaderID;
	private final boolean deferredLoading;
	private final Class<? extends Fragment> detailsClass;
	private final int deletionMessage;
	private final int deletionTitle;
	private final LoaderManager.LoaderCallbacks<Uri> mInsertLoaderCallback = new LoaderManager.LoaderCallbacks<Uri>() {

		@NonNull
		@Override
		public Loader<Uri> onCreateLoader(int id, Bundle args) {
			return new InsertLoader(getActivity(), args, ListFragmentCommon.this);
		}

		@Override
		public void onLoadFinished(@NonNull Loader<Uri> loader, final Uri data) {
			new Handler().post(() -> finishCreateNewElement(data));
		}

		@Override
		public void onLoaderReset(@NonNull Loader<Uri> loader) {
			((InsertLoader) loader).cleanUp();
		}
	};
	protected boolean supportPick;
	protected boolean supportEdit;
	private long currentId = -1;

	protected ListFragmentCommon(int loaderID, String tableName, @StringRes int emptyText, Class<? extends Fragment> detailsClass, @StringRes int pickTitle) {
		this(loaderID, tableName, emptyText, detailsClass, -1, -1, pickTitle, false);
	}


	protected ListFragmentCommon(int loaderID, String tableName, @StringRes int emptyText, Class<? extends Fragment> detailsClass, @StringRes int pickTitle, boolean deferredLoading) {
		this(loaderID, tableName, emptyText, detailsClass, -1, -1, pickTitle, deferredLoading);
	}

	protected ListFragmentCommon(int loaderID, String tableName, @StringRes int emptyText) {
		this(loaderID, tableName, emptyText, null, -1, -1, -1, false);
	}

	protected ListFragmentCommon(int loaderID, String tableName, @StringRes int emptyText, @StringRes int pickTitle) {
		this(loaderID, tableName, emptyText, null, -1, -1, pickTitle, false);
	}

	protected ListFragmentCommon(int loaderID, String tableName, @StringRes int emptyText, Class<? extends Fragment> detailsClass,
								 @StringRes int deletionTitle, @StringRes int deletionMessage) {
		this(loaderID, tableName, emptyText, detailsClass, deletionTitle, deletionMessage, -1, false);
	}

	protected ListFragmentCommon(int loaderID, String tableName, @StringRes int emptyText, Class<? extends Fragment> detailsClass,
								 @StringRes int deletionTitle, @StringRes int deletionMessage, @StringRes int pickTitle) {
		this(loaderID, tableName, emptyText, detailsClass, deletionTitle, deletionMessage, pickTitle, false);

	}

	protected ListFragmentCommon(int loaderID, String tableName, @StringRes int emptyText, Class<? extends Fragment> detailsClass,
								 @StringRes int deletionTitle, @StringRes int deletionMessage, @StringRes int pickTitle, boolean deferredLoading) {
		this.deletionTitle = deletionTitle;
		this.deletionMessage = deletionMessage;
		this.detailsClass = detailsClass;
		this.tableName = tableName;
		this.emptyText = emptyText;
		this.loaderID = loaderID;
		this.insertLoaderID = loaderID | 0xf00;
		this.pickTitle = pickTitle;
		this.deferredLoading = deferredLoading;
		supportDetails = detailsClass != null;
	}

	public static Uri appendIsNewElement(Uri base) {
		return base.buildUpon().appendQueryParameter(IS_NEW_ELEMENT, TRUE).build();
	}

	public static boolean checkIsNewElement(Uri base) {
		return base != null && TRUE.equals(base.getQueryParameter(IS_NEW_ELEMENT));
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putLong(ID, currentId);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			currentId = savedInstanceState.getLong(ID, -1);
		}
		if (getArguments() != null) {
			supportPick = getArguments().getBoolean(LIST_FRAGMENT_SUPPORTS_PICK, supportPick);
			supportEdit = getArguments().getBoolean(LIST_FRAGMENT_SUPPORTS_EDIT, supportEdit);
		}
		setListAdapter(createListAdapter());
	}

	protected abstract ListAdapter createListAdapter();

	protected void showDetails(Uri uri, boolean editable) {
		boolean handled = false;
		if (getParentFragment() != null && getParentFragment() instanceof IDetailsUiProvider)
			handled = ((IDetailsUiProvider) getParentFragment()).showDetails(uri, getDetailsClass(), null, editable);
		if (!handled && getActivity() instanceof IDetailsUiProvider)
			((IDetailsUiProvider) getActivity()).showDetails(uri, getDetailsClass(), null, editable);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		if (supportEdit) {
			final View wrapped = createListViewWrapper(inflater, container, savedInstanceState);
			Ui.getView(wrapped, R.id.button_add_new).setOnClickListener(v -> startCreateNewItem());
			return wrapped;
		}
		return super.onCreateView(inflater, container, savedInstanceState);
	}

	protected void startCreateNewItem() {
		createNewElementAsync(null);
	}

	@SuppressWarnings("SameParameterValue")
	protected void createNewElementAsync(final Bundle args) {
		LoaderManager.getInstance(this).restartLoader(insertLoaderID, args, mInsertLoaderCallback);
	}

	private void finishCreateNewElement(Uri itemUri) {
		showDetails(itemUri, true);
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		if (supportPick)
			requireActivity().setTitle(pickTitle);
		setEmptyText(getText(emptyText));
		getListView().setFastScrollEnabled(true);
		final TypedValue typedValue = new TypedValue();
		requireActivity().getTheme().resolveAttribute(R.attr.list_item_background, typedValue, true);
		getListView().setSelector(typedValue.resourceId);
		getListView().setOnItemLongClickListener(
				(parent, viewClicked, position, id) -> {
					if (supportPick) {
						final Cursor cursor = (Cursor) parent.getItemAtPosition(position);
						final Uri uri = getItemUri(cursor, id);
						if (!ListFragmentCommon.this.onItemClick(parent, viewClicked, position, id, uri, false, false)) {
							showDetails(uri, false);
							return true;
						}
						return false;
					}
					final Cursor cursor = (Cursor) parent.getItemAtPosition(position);
					if (supportEdit) {
						if (getEditable(cursor)) {
							GenericDialogFragment.ConfirmDelete.newInstance(DELETE_CALLBACK_ID, deletionTitle, deletionMessage, getItemUri(cursor, id))
									.show(getChildFragmentManager(), GenericDialogFragment.DIALOG_FRAGMENT_TAG);
						}
						return true;
					}
					return onItemClick(parent, viewClicked, position, id, getItemUri(cursor, id), getEditable(cursor), true);
				});
		getListView().setOnItemClickListener((parent, viewClicked, position, id) -> {
			final Cursor cursor = (Cursor) parent.getItemAtPosition(position);
			final Uri currentUri = getItemUri(cursor, id);
			final boolean editable = getEditable(cursor);
			if (supportPick) {
				final Intent ret = new Intent();
				ret.setData(currentUri);
				final Parcelable parcelable = requireArguments().getParcelable(LIST_FRAGMENT_PASS_THROUGH);
				ret.putExtra(EXTRA_PASS_THROUGH, parcelable);
				ret.putExtra(EXTRA_ID, id);
				ret.putExtra(EXTRA_POS, position);
				ret.putExtra(EXTRA_ROW, StaticHelpers.cursorRowToBundle(cursor));
				requireActivity().setResult(Activity.RESULT_OK, ret);
				requireActivity().finish();
				return;
			}
			currentId = id;
			if (!ListFragmentCommon.this.onItemClick(parent, viewClicked, position, id, currentUri, editable, false)) {
				if (supportDetails) {
					showDetails(currentUri, editable);
				}
			}
		});
		setListShownNoAnimation(false);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (!deferredLoading)
			LoaderManager.getInstance(this).initLoader(loaderID, null, this);
	}

	@NonNull
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		if (id == loaderID) {
			setListShown(false);
			return getLoader(args);
		}
		throw new RuntimeException("Unknown loader id: " + id);
	}

	protected abstract Loader<Cursor> getLoader(Bundle args);

	@SuppressWarnings("UnusedParameters")
	protected boolean onItemClick(AdapterView<?> parent, View view, int position, long id, Uri currentUri, boolean editable, boolean longClick) {
		return false;
	}

	protected Uri getItemUri(@SuppressWarnings("UnusedParameters") Cursor cursor, long id) {
		return ListProvider.getHelper().getItemUri(tableName, false, id);
	}

	protected abstract boolean getEditable(Cursor cursor);

	public Uri createNewElement(Bundle args) {
		throw new UnsupportedOperationException(getClass() + ".createNewElement");
	}

	protected void finishLoading(final Cursor data) {
		((CursorAdapter) Objects.requireNonNull(getListAdapter())).swapCursor(data);
		if (isResumed()) {
			setListShown(true);
		} else {
			setListShownNoAnimation(true);
		}
	}

	@Override
	public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
		if (loader.getId() == loaderID)
			finishLoading(data);
	}

	@Override
	public void onLoaderReset(@NonNull Loader<Cursor> loader) {
		if (loader.getId() == loaderID)
			((CursorAdapter) Objects.requireNonNull(getListAdapter())).swapCursor(null).close();
	}

	private View createListViewWrapper(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final ViewGroup wrapper = (ViewGroup) inflater.inflate(R.layout.common_list_fragment, container, false);
		wrapper.addView(super.onCreateView(inflater, wrapper, savedInstanceState), 0);
		return wrapper;
	}

	public Class<? extends Fragment> getDetailsClass() {
		return detailsClass;
	}

	public boolean onAction(int ID, boolean canceled) {
		if (DELETE_CALLBACK_ID == ID) {
			final View view = getView();
			if (view != null) {
				final int snackBarText;
				if (canceled) {
					snackBarText = R.string.deletion_cancelled;
				} else {
					snackBarText = R.string.deleted;
				}
				Snackbar.make(view, snackBarText, Snackbar.LENGTH_SHORT).show();
			}
			return true;
		}
		return false;
	}

	public interface IDetailsUiProvider {
		boolean showDetails(Uri itemUri, Class<? extends Fragment> fragmentClass, Bundle args, boolean editable);
	}

	private static class InsertLoader extends AsyncTaskLoader<Uri> {
		private Bundle args;
		private ListFragmentCommon list;
		private Uri mUri = null;
		private boolean wasStarted = false;

		InsertLoader(Context context, Bundle args, ListFragmentCommon list) {
			super(context);
			this.args = args;
			this.list = list;
		}

		@Override
		public Uri loadInBackground() {
			if (list != null) {
				mUri = list.createNewElement(args);
				cleanUp();
			}
			return mUri;
		}

		@Override
		protected void onStartLoading() {
			if ((takeContentChanged() || mUri == null) && !wasStarted) {
				wasStarted = true;
				forceLoad();
			}
		}

		void cleanUp() {
			list = null;
			args = null;
		}
	}

	public static class Builder {
		final Bundle bundle;

		Builder() {
			this(new Bundle());
		}

		Builder(Bundle in) {
			bundle = in;
		}

		public static Builder create() {
			return new Builder();
		}

		public static Builder createFromBundle(Bundle in) {
			return in != null ? new Builder(in) : new Builder();
		}

		public Builder setSupportPick(boolean value) {
			bundle.putBoolean(LIST_FRAGMENT_SUPPORTS_PICK, value);
			return this;
		}

		public Builder setSupportEdit(boolean value) {
			bundle.putBoolean(LIST_FRAGMENT_SUPPORTS_EDIT, value);
			return this;
		}

		public Builder putPassThrough(Parcelable value) {
			bundle.putParcelable(LIST_FRAGMENT_PASS_THROUGH, value);
			return this;
		}

		public Bundle build() {
			return bundle;
		}
	}
}