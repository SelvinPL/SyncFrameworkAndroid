/*
 Copyright (c) 2017 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */
package pl.selvin.android.autocontentprovider.test;

import android.content.ContentProviderClient;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;

import pl.selvin.android.autocontentprovider.content.AutoContentProvider;
import pl.selvin.android.autocontentprovider.content.ContentHelper;
import pl.selvin.android.autocontentprovider.db.TableInfo;
import pl.selvin.android.autocontentprovider.impl.DefaultDatabaseInfoFactory;
import pl.selvin.android.autocontentprovider.log.Logger;

public class TestProvider extends AutoContentProvider<TableInfo> {

	public final static String AUTHORITY = "pl.selvin.android.autocontentprovider.test";
	public final static ContentHelper<TableInfo> CONTENT_HELPER = new ContentHelper<>(DatabaseTest.class, AUTHORITY, new DefaultDatabaseInfoFactory(), "test_db", 4);

	private SupportSQLiteOpenHelper.Callback helperCallback;

	public TestProvider() {
		super(CONTENT_HELPER, Logger.EmptyLogger.INSTANCE,
				context -> new FrameworkSQLiteOpenHelperFactory());
	}

	/**
	 * The provider keeps its callback private, but it has to hand it to the helper through
	 * the configuration - so capture it on the way past. Lets lifecycle tests
	 * (downgrade/corruption) drive the callback directly instead of staging a real database
	 * version change.
	 */
	@NonNull
	@Override
	protected SupportSQLiteOpenHelper.Configuration getHelperConfiguration() {
		final SupportSQLiteOpenHelper.Configuration configuration = super.getHelperConfiguration();
		helperCallback = configuration.callback;
		return configuration;
	}

	public SupportSQLiteOpenHelper.Callback getCapturedCallback() {
		return helperCallback;
	}

	/**
	 * The running in-process instance, so tests can reach provider internals that are not
	 * exposed through ContentResolver.
	 */
	@SuppressWarnings("deprecation")
	public static TestProvider getRunning(@NonNull Context context) {
		final ContentProviderClient client = context.getContentResolver()
				.acquireContentProviderClient(AUTHORITY);
		if (client == null)
			throw new IllegalStateException("TestProvider is not running");
		try {
			return (TestProvider) client.getLocalContentProvider();
		} finally {
			// close() is API 24+, minSdk here is 23
			client.release();
		}
	}
}