package pl.selvin.android.listsyncsample.provider.implementation;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;

/**
 * @noinspection unused
 */
public interface AbstractQueryProviderInterface {
	default String getType(@NonNull Uri uri) {
		throw new UnsupportedOperationException("getType is not supported!");
	}

	default int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException("update is not supported!");
	}

	default int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException("delete is not supported!");
	}

	default Uri insert(@NonNull Uri uri, ContentValues values) {
		throw new UnsupportedOperationException("insert is not supported!");
	}

	default Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		throw new UnsupportedOperationException("query is not supported!");
	}
}