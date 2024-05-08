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

package pl.selvin.android.listsyncsample.provider;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;

import pl.selvin.android.autocontentprovider.log.Logger;
import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.listsyncsample.HelperFactoryProvider;
import pl.selvin.android.listsyncsample.provider.implementation.AbstractQueryProvider;
import pl.selvin.android.listsyncsample.provider.implementation.TagItemMappingWithNamesProvider;
import pl.selvin.android.listsyncsample.provider.implementation.TagNotUsedProvider;
import pl.selvin.android.syncframework.content.BaseContentProvider;
import pl.selvin.android.syncframework.content.SyncContentHelper;

public class ListProvider extends BaseContentProvider {
	private static final String DATABASE_NAME = "list_db";
	private static final int DATABASE_VERSION = 19;

	private static SyncContentHelper helperInstance;
	private final QueryProviders queryProviders = new QueryProviders(this, contentHelper, logger,
			TagItemMappingWithNamesProvider.class,
			TagNotUsedProvider.class);

	public ListProvider() {
		super(getHelper(), new Logger("SYNC"), new HelperFactoryProvider(), new RequestExecutor());
	}

	public static synchronized SyncContentHelper getHelper() {
		if (helperInstance == null)
			helperInstance = SyncContentHelper.getInstance(Database.class, Constants.AUTHORITY, DATABASE_NAME, DATABASE_VERSION, Constants.SERVICE_URI);
		return helperInstance;
	}

	@Override
	public String getType(@NonNull Uri uri) {

		final AbstractQueryProvider provider = queryProviders.get(uri);
		if (provider != null)
			return provider.getType(uri);
		return super.getType(uri);
	}

	@Override
	public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		final AbstractQueryProvider provider = queryProviders.get(uri);
		if (provider != null)
			return provider.update(uri, values, selection, selectionArgs);
		return super.update(uri, values, selection, selectionArgs);
	}

	@Override
	public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
		final AbstractQueryProvider provider = queryProviders.get(uri);
		if (provider != null)
			return provider.delete(uri, selection, selectionArgs);
		return super.delete(uri, selection, selectionArgs);
	}

	@Override
	public Uri insert(@NonNull Uri uri, ContentValues values) {
		final AbstractQueryProvider provider = queryProviders.get(uri);
		if (provider != null)
			return provider.insert(uri, values);
		return super.insert(uri, values);
	}

	@Override
	public Cursor query(@NonNull Uri uri, String[] projection, String selection,
						String[] selectionArgs, String sortOrder) {
		final AbstractQueryProvider provider = queryProviders.get(uri);
		if (provider != null)
			return provider.query(uri, projection, selection, selectionArgs, sortOrder);
		return super.query(uri, projection, selection, selectionArgs, sortOrder);
	}
}