package pl.selvin.android.listsyncsample.provider.implementation;

import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.ArrayMap;

import androidx.annotation.NonNull;

import pl.selvin.android.autocontentprovider.content.ContentHelper;
import pl.selvin.android.autocontentprovider.log.Logger;
import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.provider.Database.Tag;
import pl.selvin.android.listsyncsample.provider.Database.TagItemMapping;
import pl.selvin.android.listsyncsample.provider.ListProvider;
import pl.selvin.android.syncframework.content.SYNC;
import pl.selvin.android.syncframework.content.SyncTableInfo;

public class TagNotUsedProvider extends AbstractQueryProvider {
	protected static final String baseTable = Tag.TABLE_NAME;
	protected static final String Path = "TagNotUsed";
	public static final String Uri = "content://" + Constants.AUTHORITY + "/" + Path;
	private final ArrayMap<String, String> projectionMap;

	public TagNotUsedProvider(@NonNull ListProvider contentProvider, @NonNull ContentHelper<SyncTableInfo> contentHelper, Logger logger, @NonNull UriMatcher uriMatcher, int code) {
		super(contentProvider, contentHelper, logger, uriMatcher, code);
		uriMatcher.addURI(Constants.AUTHORITY, Path, code);
		projectionMap = projectionMapBuilder(Tag.SCOPE, Tag.TABLE_NAME)
				.build();
	}

	public static Uri getDirUri() {
		return ListProvider.getHelper().getDirUri(Path, false);
	}

	@Override
	public String getType(@NonNull Uri uri) {
		return contentProvider.getType(contentHelper.getDirUri(baseTable));
	}

	@Override
	public Cursor query(@NonNull android.net.Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
		builder.setTables(Tag.TABLE_NAME);
		builder.appendWhere("(NOT EXISTS(SELECT 1 FROM " + TagItemMapping.TABLE_NAME + " WHERE " + TagItemMapping.TAG_ID
				+ "=" + Tag.ID + " AND " + TagItemMapping.ITEM_ID + "=? AND [" +
				TagItemMapping.TABLE_NAME + "]." + SYNC.isDeleted + "=0))");
		return returnCursor(projectionMap, uri, builder, projection, selection, selectionArgs,
				null, null, sortOrder, getLimit(uri));
	}
}