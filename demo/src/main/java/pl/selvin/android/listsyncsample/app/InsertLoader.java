package pl.selvin.android.listsyncsample.app;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.loader.content.AsyncTaskLoader;

public class InsertLoader extends AsyncTaskLoader<Uri> {
	private Bundle args;
	private ICreator creator;
	private Uri mUri = null;
	private boolean wasStarted = false;

	public InsertLoader(Context context, Bundle args, ICreator creator) {
		super(context);
		this.args = args;
		this.creator = creator;
	}

	@Override
	public Uri loadInBackground() {
		if (creator != null) {
			mUri = creator.createNewElement(args);
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

	public void cleanUp() {
		creator = null;
		args = null;
	}

	public interface ICreator {
		Uri createNewElement(Bundle args);
	}
}