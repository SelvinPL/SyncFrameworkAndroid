package pl.selvin.android.listsyncsample.provider.implementation;

import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteQuery;

import java.util.HashMap;
import java.util.Map;

import pl.selvin.android.autocontentprovider.content.ContentHelper;
import pl.selvin.android.autocontentprovider.db.TableInfo;
import pl.selvin.android.autocontentprovider.log.Logger;
import pl.selvin.android.listsyncsample.provider.ListProvider;
import pl.selvin.apt.constructorsconstraints.annotations.ConstructorConstraint;

/**
 * @noinspection unused, SameParameterValue
 */
@ConstructorConstraint(arguments = {ListProvider.class, ContentHelper.class, Logger.class, UriMatcher.class, int.class})
public abstract class AbstractQueryProvider implements AbstractQueryProviderInterface {
	protected final ListProvider contentProvider;
	protected final ContentHelper contentHelper;
	protected final Logger logger;
	protected final UriMatcher uriMatcher;
	protected final int code;

	public AbstractQueryProvider(@NonNull ListProvider contentProvider, @NonNull ContentHelper contentHelper, Logger logger,
								 @NonNull UriMatcher uriMatcher, int code) {
		this.contentProvider = contentProvider;
		this.contentHelper = contentHelper;
		this.logger = logger;
		this.uriMatcher = uriMatcher;
		this.code = code;
	}

	protected ProjectionMapBuilder projectionMapBuilder(final String scope, final String table) {
		return new ProjectionMapBuilder(contentHelper, scope, table);
	}

	@NonNull
	protected Context requireContext() {
		return contentProvider.requireContextEx();
	}

	protected SupportSQLiteDatabase getReadableDatabase() {
		return contentProvider.getReadableDatabase();
	}

	protected SupportSQLiteDatabase getWritableDatabase() {
		return contentProvider.getWritableDatabase();
	}

	protected String getLimit(Uri uri) {
		return uri.getQueryParameter(ContentHelper.PARAMETER_LIMIT);
	}

	protected Cursor returnCursor(final Map<String, String> projectionMap, final Uri uri,
								  final SQLiteQueryBuilder builder, final String[] projection, final String selection,
								  final String[] selectionArgs, final String groupBy, final String having, final String sortOrder,
								  final String limit) {
		return returnCursor(projectionMap, uri, builder, projection, selection, selectionArgs, groupBy, having, sortOrder, limit, true);
	}

	protected Cursor returnCursor(final Map<String, String> projectionMap, final Uri uri,
								  final SQLiteQueryBuilder builder, final String[] projection, final String selection,
								  final String[] selectionArgs, final String groupBy, final String having, final String sortOrder,
								  final String limit, boolean register) {
		builder.setProjectionMap(projectionMap);
		logger.LogQuery(getClass(), uri, builder, projection, selection, selectionArgs, groupBy, having, sortOrder, limit);
		final SupportSQLiteQuery query = new SimpleSQLiteQuery(builder.buildQuery(projection, selection, groupBy, having, sortOrder, limit), selectionArgs);
		final Cursor cursor = contentProvider.getReadableDatabase().query(query);
		if (register)
			registerCursor(cursor, uri);
		return cursor;
	}

	protected void registerCursor(Cursor cursor, Uri uri) {
		cursor.setNotificationUri(contentProvider.requireContextEx().getContentResolver(), uri);
	}

	public static class ProjectionMapBuilder {
		private final HashMap<String, String> projectionMap = new HashMap<>();
		private final ContentHelper contentHelper;

		private ProjectionMapBuilder(final ContentHelper contentHelper, final String scope, final String table) {
			this.contentHelper = contentHelper;
			projectionMap.putAll(contentHelper.getTableFromType(String.format("%s.%s", scope, table)).map);
		}

		public ProjectionMapBuilder addTable(final String scope, final String table) {
			final TableInfo tableInfo = contentHelper.getTableFromType(String.format("%s.%s", scope, table));
			for (final String key : tableInfo.map.keySet()) {
				if (!key.equals(BaseColumns._ID)) {
					projectionMap.put(key, tableInfo.map.get(key));
				}
			}
			return this;
		}

		public HashMap<String, String> build() {
			return projectionMap;
		}
	}
}