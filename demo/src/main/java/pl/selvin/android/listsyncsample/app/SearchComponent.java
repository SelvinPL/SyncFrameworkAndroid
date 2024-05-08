package pl.selvin.android.listsyncsample.app;

import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.savedstate.SavedStateRegistry;

import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.R;

public class SearchComponent implements DefaultLifecycleObserver, SearchView.OnQueryTextListener,
		SavedStateRegistry.SavedStateProvider, SearchView.OnCloseListener, View.OnClickListener {
	private static final String QUERY = "SEARCH_COMPONENT_QUERY";
	private static final String ICONIFIED = "SEARCH_COMPONENT_ICONIFIED";
	@StringRes
	private final int hint;
	private boolean iconified;
	private Callback callback;
	private SearchView searchView;
	final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(false) {
		@Override
		public void handleOnBackPressed() {
			if (searchView != null) {
				searchView.clearFocus();
				searchView.setIconified(true);
				//second time because if there is a text then searchView clears first
				searchView.setIconified(true);
			}
		}
	};
	private String query;
	private String newQuery;
	private final Runnable postQuery = () -> {
		if (setQuery(newQuery)) {
			if (callback != null)
				callback.queryChanged(query, false);
		}
	};
	private boolean restored;

	private SearchComponent(@StringRes int hint) {
		this.hint = hint;
	}

	public static void install(Callback callback, @StringRes int hint) {
		callback.getLifecycle().addObserver(new SearchComponent(hint));
	}

	@Override
	public void onCreate(@NonNull LifecycleOwner owner) {
		if (!(owner instanceof Callback)) {
			throw new RuntimeException("LifecycleOwner needs to implement " + Callback.class.getName());
		}
		callback = (Callback) owner;
		callback.requireActivity().getOnBackPressedDispatcher().addCallback(owner, onBackPressedCallback);
		final SavedStateRegistry savedStateRegistry = callback.getSavedStateRegistry();
		final String saveKey = callback.getSaveKey();
		savedStateRegistry.registerSavedStateProvider(saveKey, this);
		final Bundle savedState = savedStateRegistry.consumeRestoredStateForKey(saveKey);
		if (savedState != null) {
			newQuery = query = savedState.getString(QUERY, Constants.StringUtil.EMPTY);
			iconified = savedState.getBoolean(ICONIFIED);
			restored = true;
		} else {
			newQuery = query = Constants.StringUtil.EMPTY;
			iconified = true;
			restored = false;
		}
	}

	private Callback requireCallback(@NonNull LifecycleOwner owner) {
		if (!(owner instanceof Callback)) {
			throw new RuntimeException("LifecycleOwner needs to implement " + Callback.class.getName());
		}
		return (Callback) owner;
	}

	@Override
	public void onResume(@NonNull LifecycleOwner owner) {
		callback = requireCallback(owner);
		final FragmentActivity activity = callback.requireActivity();
		searchView = activity.findViewById(R.id.search_view);
		searchView.setVisibility(View.VISIBLE);
		searchView.setQueryHint(activity.getString(hint));
		searchView.setIconified(iconified);
		if (!iconified) {
			searchView.setQuery(query, false);
		} else {
			searchView.setQuery(Constants.StringUtil.EMPTY, false);
		}
		searchView.setOnCloseListener(this);
		searchView.setOnQueryTextListener(this);
		searchView.setOnSearchClickListener(this);
		onBackPressedCallback.setEnabled(!iconified);
		callback.queryChanged(query, restored);
	}

	@Override
	public void onPause(@NonNull LifecycleOwner owner) {
		callback = requireCallback(owner);
		searchView.setOnQueryTextListener(null);
		searchView.setOnCloseListener(null);
		searchView.setOnSearchClickListener(null);
		iconified = searchView.isIconified();
		final CharSequence query = searchView.getQuery();
		this.query = query != null ? query.toString() : null;
		searchView.setQuery(null, false);
		searchView.setVisibility(View.GONE);
		searchView = null;
		restored = true;
	}

	@Override
	public void onDestroy(@NonNull LifecycleOwner owner) {
		onBackPressedCallback.remove();
		callback = null;
	}

	private boolean setQuery(String query) {
		if (this.query == null) {
			if (query == null) {
				return false;
			}
		} else if (this.query.equals(query)) {
			return false;
		}
		this.query = query;
		return true;
	}

	@Override
	public boolean onQueryTextSubmit(String query) {
		searchView.removeCallbacks(postQuery);
		newQuery = query;
		searchView.postDelayed(postQuery, Constants.SEARCH_VIEW_DELAY);
		return true;
	}

	@Override
	public boolean onQueryTextChange(String query) {
		searchView.removeCallbacks(postQuery);
		newQuery = query;
		searchView.postDelayed(postQuery, Constants.SEARCH_VIEW_DELAY);
		return true;
	}

	@NonNull
	@Override
	public Bundle saveState() {
		final Bundle outState = new Bundle();
		outState.putString(QUERY, newQuery);
		outState.putBoolean(ICONIFIED, iconified);
		return outState;
	}

	@Override
	public boolean onClose() {
		onBackPressedCallback.setEnabled(false);
		return false;
	}

	@Override
	public void onClick(View view) {
		onBackPressedCallback.setEnabled(true);
	}


	public interface Callback {
		void queryChanged(String query, boolean restored);

		default String getSaveKey() {
			return getClass().getName();
		}

		FragmentActivity requireActivity();

		Lifecycle getLifecycle();

		SavedStateRegistry getSavedStateRegistry();
	}
}