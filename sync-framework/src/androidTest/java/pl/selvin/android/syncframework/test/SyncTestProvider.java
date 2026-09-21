/*
 Copyright (c) 2026 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */
package pl.selvin.android.syncframework.test;

import android.content.ContentProviderClient;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;

import pl.selvin.android.autocontentprovider.log.Logger;
import pl.selvin.android.syncframework.content.BaseContentProvider;
import pl.selvin.android.syncframework.content.SyncContentHelper;
import pl.selvin.android.syncframework.json.moshi.MoshiJsonFactory;

public class SyncTestProvider extends BaseContentProvider {

	public final static String AUTHORITY = "pl.selvin.android.syncframework.test";
	public final static SyncContentHelper CONTENT_HELPER = SyncContentHelper.getInstance(
			SyncDatabase.class, AUTHORITY, "sync_test_db", 1);
	/** Shared with the tests, which enqueue responses on it before calling sync. */
	public final static FakeRequestExecutor EXECUTOR = new FakeRequestExecutor();
	/** Supplied through the JsonFactory constructor parameter, wrapping the bundled codec. */
	public final static RecordingJsonFactory JSON_FACTORY =
			new RecordingJsonFactory(new MoshiJsonFactory(CONTENT_HELPER));

	public SyncTestProvider() {
		super(CONTENT_HELPER, Logger.EmptyLogger.INSTANCE,
				context -> new FrameworkSQLiteOpenHelperFactory(), EXECUTOR, JSON_FACTORY);
	}

	@SuppressWarnings("deprecation")
	public static SyncTestProvider getRunning(@NonNull Context context) {
		final ContentProviderClient client = context.getContentResolver()
				.acquireContentProviderClient(AUTHORITY);
		if (client == null)
			throw new IllegalStateException("SyncTestProvider is not running");
		try {
			return (SyncTestProvider) client.getLocalContentProvider();
		} finally {
			// close() is API 24+, minSdk here is 23
			client.release();
		}
	}
}
