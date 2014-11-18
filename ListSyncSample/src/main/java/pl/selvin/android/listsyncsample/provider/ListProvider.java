package pl.selvin.android.listsyncsample.provider;

import pl.selvin.android.listsyncsample.Setup;
import pl.selvin.android.syncframework.content.BaseContentProvider;
import pl.selvin.android.syncframework.content.ContentHelper;

import android.database.Cursor;
import android.net.Uri;

public class ListProvider extends BaseContentProvider {
    private final static ContentHelper helperInstance = ContentHelper.getInstance(Setup.class);

    public static final synchronized ContentHelper getHelper() {
        return helperInstance;
    }

    public ListProvider() {
        super(getHelper());
    }
    // we don't need implementation ... base class do everything on it's own
	// this is just class which we are pointing in xml files as Provider
	// since one day i'll move BaseContentProvider to library

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		try {
			//delay for testing
			//Thread.sleep(2000);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return super
				.query(uri, projection, selection, selectionArgs, sortOrder);
	}
}
