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
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;

import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.provider.ListProvider;
import pl.selvin.android.listsyncsample.utils.StaticHelpers;
import pl.selvin.android.listsyncsample.utils.UnknownLoaderIdException;

public abstract class ListFragmentCommon extends ListFragmentEx implements LoaderManager.LoaderCallbacks<Cursor>, IListFragmentCommon,
		GenericDialogFragment.ConfirmDelete.Callback, InsertLoader.ICreator {
	public static final String EXTRA_ID = "EXTRA_ID";
	public static final String EXTRA_POS = "EXTRA_POS";
	public static final String EXTRA_ROW = "EXTRA_ROW";
	public static final String IS_EDITABLE = "IS_EDITABLE";
	protected static final int DELETE_CALLBACK_ID = 101010;
	private static final String ID = "LIST_COMMON_ID";
	private static final String IS_NEW_ELEMENT = "IS_NEW_ELEMENT";
	private static final String TRUE = "true";
	protected final ListFragmentCommonSetup setup;

	private final LoaderManager.LoaderCallbacks<Uri> mInsertLoaderCallback = new LoaderManager.LoaderCallbacks<Uri>() {

		@NonNull
		@Override
		public Loader<Uri> onCreateLoader(int id, Bundle args) {
			return new InsertLoader(getActivity(), args, ListFragmentCommon.this);
		}

		@Override
		public void onLoadFinished(@NonNull Loader<Uri> loader, final Uri data) {
			getLoaderManager().destroyLoader(loader.getId());
			finishCreateNewElement(data);
		}

		@Override
		public void onLoaderReset(@NonNull Loader<Uri> loader) {
			((InsertLoader) loader).cleanUp();
		}
	};
	protected boolean supportPick;
	protected boolean supportEdit;
	private long currentId = -1;

	protected ListFragmentCommon(ListFragmentCommonSetup setup) {
		this.setup = setup;
	}

	public static ListFragmentCommonSetup.Builder createSetupBuilder(int loaderID, String tableName, @StringRes int emptyText) {
		return ListFragmentCommonSetup.createBuilder(loaderID, tableName, emptyText);
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
		if (setup.supportDetails) {
			boolean handled = false;
			final Bundle args = new Bundle();
			args.putBoolean(IS_EDITABLE, editable);
			if (getParentFragment() != null && getParentFragment() instanceof IDetailsUiProvider)
				handled = ((IDetailsUiProvider) getParentFragment()).showDetails(uri, setup.detailsClass, args, editable);
			if (!handled && getActivity() instanceof IDetailsUiProvider)
				((IDetailsUiProvider) getActivity()).showDetails(uri, setup.detailsClass, args, editable);
		}
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		if (supportEdit) {
			final View wrapped = createListViewWrapper(inflater, container, savedInstanceState);
			final FloatingActionButton addNewButton = wrapped.findViewById(R.id.button_add_new);
			addNewButton.setOnClickListener(v -> startCreateNewItem());
			return wrapped;
		}
		return super.onCreateView(inflater, container, savedInstanceState);
	}

	protected void startCreateNewItem() {
		createNewElementAsync();
	}

	protected void createNewElementAsync() {
		getLoaderManager().restartLoader(setup.insertLoaderID, null, mInsertLoaderCallback);
	}

	private void finishCreateNewElement(Uri itemUri) {
		showDetails(itemUri, true);
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		if (supportPick)
			requireActivity().setTitle(setup.pickTitle);
		setEmptyText(getText(setup.emptyText));
		getListView().setFastScrollEnabled(true);
		final TypedValue typedValue = new TypedValue();
		requireActivity().getTheme().resolveAttribute(R.attr.list_item_background, typedValue, true);
		getListView().setSelector(typedValue.resourceId);
		getListView().setOnItemLongClickListener(
				(parent, view1, position, id) -> {
					final Cursor cursor = (Cursor) parent.getItemAtPosition(position);
					final Uri uri = getItemUri(cursor, id);
					if (!onItemClick(parent, view1, position, id, uri, getEditable(cursor), true)) {
						if (supportPick) {
							showDetails(uri, false);
							return true;
						}
						if (supportEdit) {
							if (getEditable(cursor)) {
								GenericDialogFragment.ConfirmDelete.newInstance(DELETE_CALLBACK_ID, setup.deletionTitle, setup.deletionMessage, getItemUri(cursor, id))
										.show(getChildFragmentManager(), GenericDialogFragment.DIALOG_FRAGMENT_TAG);
							}
							return true;
						}
					} else
						return true;
					return false;
				});
		if (supportEdit) {
			ListView listView = getListView();
			listView.setNextFocusRightId(R.id.button_add_new);
		}
		getListView().setOnItemClickListener((parent, view12, position, id) -> {
			final Cursor cursor = (Cursor) parent.getItemAtPosition(position);
			final Uri currentUri = getItemUri(cursor, id);
			final boolean editable = getEditable(cursor);
			if (!ListFragmentCommon.this.onItemClick(parent, view12, position, id, currentUri, editable, false)) {
				if (supportPick) {
					final Intent ret = new Intent();
					ret.setData(currentUri);
					ret.putExtra(EXTRA_ID, id);
					ret.putExtra(EXTRA_POS, position);
					ret.putExtra(EXTRA_ROW, StaticHelpers.cursorRowToBundle(cursor));
					requireActivity().setResult(Activity.RESULT_OK, ret);
					requireActivity().finish();
					return;
				}
				currentId = id;
				if (setup.supportDetails) {
					showDetails(currentUri, editable);
				}
			}
		});
		setListShownNoAnimation(false);
	}

	@NonNull
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		if (id == setup.loaderID) {
			setListShownNoAnimation(false);
			return getLoader(args);
		}
		throw new UnknownLoaderIdException();
	}

	@NonNull
	protected abstract Loader<Cursor> getLoader(Bundle args);

	protected Uri getItemUri(Cursor cursor, long id) {
		return ListProvider.getHelper().getItemUri(setup.tableName, false, id);
	}

	protected abstract boolean getEditable(Cursor cursor);

	public Uri createNewElement(Bundle args) {
		throw new UnsupportedOperationException(getClass() + ".createNewElement");
	}

	@Override
	public void onResume() {
		super.onResume();
		if (!setup.deferredLoading) {
			initMainLoader();
		}
	}

	protected void initMainLoader() {
		getLoaderManager().initLoader(setup.loaderID, null, this);
	}

	protected void restartMainLoader() {
		getLoaderManager().restartLoader(setup.loaderID, null, this);
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
		if (loader.getId() == setup.loaderID)
			finishLoading(data);
	}

	@Override
	public void onLoaderReset(@NonNull Loader<Cursor> loader) {
		if (loader.getId() == setup.loaderID) {
			final Cursor cursor = ((CursorAdapter) Objects.requireNonNull(getListAdapter())).swapCursor(null);
			if (cursor != null)
				cursor.close();
		}
	}

	private View createListViewWrapper(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final ViewGroup wrapper = (ViewGroup) inflater.inflate(R.layout.common_list_fragment, container, false);
		wrapper.addView(super.onCreateView(inflater, wrapper, savedInstanceState), 0);
		return wrapper;
	}

	public boolean onAction(int ID, boolean canceled) {
		if (DELETE_CALLBACK_ID == ID) {
			showSnackbar(canceled ? R.string.deletion_cancelled : R.string.deleted, Snackbar.LENGTH_SHORT);
			if (!canceled)
				setListShownNoAnimation(false);
			return true;
		}
		return false;
	}

	/** @noinspection SameParameterValue*/
	protected void showSnackbar(@StringRes int snackBarText, int length) {
		final View view = getView();
		if (view != null) {
			Snackbar.make(view, snackBarText, length).show();
		}
	}

	public Class<? extends Fragment> getDetailsClass() {
		return setup.detailsClass;
	}

	public interface IDetailsUiProvider {
		boolean showDetails(Uri itemUri, Class<? extends Fragment> fragmentClass, Bundle args, boolean editable);
	}

	public static class Builder {
		final Bundle bundle;

		private Builder() {
			this(new Bundle());
		}

		private Builder(Bundle in) {
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

		public Bundle build() {
			return bundle;
		}
	}
}