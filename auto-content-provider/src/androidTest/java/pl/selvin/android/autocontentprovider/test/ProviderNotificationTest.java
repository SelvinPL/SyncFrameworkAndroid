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
package pl.selvin.android.autocontentprovider.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Change notifications: the provider notifies the operated Uri on insert/update/delete, plus
 * every entry in the table's notifyUris, and stays quiet when nothing actually changed.
 */
@RunWith(AndroidJUnit4.class)
public class ProviderNotificationTest {

	/** Notifications round trip through the system server, so they are not instant. */
	private static final long NOTIFY_TIMEOUT_SECONDS = 5;
	/** Window used when asserting that nothing arrives. */
	private static final long QUIET_MILLIS = 750;

	private ContentResolver resolver;
	private final ArrayList<ContentObserver> registered = new ArrayList<>();

	@Before
	public void setUp() {
		final Context appContext = ApplicationProvider.getApplicationContext();
		resolver = appContext.getContentResolver();
		resolver.delete(dirUri(DatabaseTest.Status.TABLE_NAME), null, null);
		resolver.delete(dirUri(DatabaseTest.AllTypes.TABLE_NAME), null, null);
	}

	@After
	public void tearDown() {
		for (final ContentObserver observer : registered)
			resolver.unregisterContentObserver(observer);
		registered.clear();
	}

	@Test
	public void insertNotifiesDirUri() throws Exception {
		final CountingObserver observer = observe(dirUri(DatabaseTest.Status.TABLE_NAME));

		insertStatus(1, "one");

		observer.awaitChange();
	}

	@Test
	public void updateNotifiesDirUri() throws Exception {
		insertStatus(1, "one");
		final CountingObserver observer = observe(dirUri(DatabaseTest.Status.TABLE_NAME));

		final ContentValues values = new ContentValues();
		values.put(DatabaseTest.Status.NAME, "changed");
		assertEquals(1, resolver.update(dirUri(DatabaseTest.Status.TABLE_NAME), values,
				DatabaseTest.Status.ID + "=?", new String[]{"1"}));

		observer.awaitChange();
	}

	@Test
	public void deleteNotifiesDirUri() throws Exception {
		insertStatus(1, "one");
		final CountingObserver observer = observe(dirUri(DatabaseTest.Status.TABLE_NAME));

		assertEquals(1, resolver.delete(dirUri(DatabaseTest.Status.TABLE_NAME),
				DatabaseTest.Status.ID + "=?", new String[]{"1"}));

		observer.awaitChange();
	}

	/**
	 * AllTypes declares a notifyUris entry - an unrelated Uri that has to be notified
	 * alongside the one being written, so dependent cursors refresh.
	 */
	@Test
	public void insertNotifiesDeclaredNotifyUris() throws Exception {
		final CountingObserver observer = observe(Uri.parse(DatabaseTest.AllTypes.NOTIFY_URI));

		insertAllTypes(1);

		observer.awaitChange();
	}

	@Test
	public void deleteNotifiesDeclaredNotifyUris() throws Exception {
		insertAllTypes(1);
		final CountingObserver observer = observe(Uri.parse(DatabaseTest.AllTypes.NOTIFY_URI));

		assertEquals(1, resolver.delete(dirUri(DatabaseTest.AllTypes.TABLE_NAME),
				DatabaseTest.AllTypes.ID + "=?", new String[]{"1"}));

		observer.awaitChange();
	}

	/**
	 * update and delete only notify when they actually changed rows. The no-op runs first
	 * and a real update follows: if the no-op had notified, the observed count would be 2.
	 */
	@Test
	public void updateMatchingNothingDoesNotNotify() throws Exception {
		insertStatus(1, "one");
		final CountingObserver observer = observe(dirUri(DatabaseTest.Status.TABLE_NAME));

		final ContentValues values = new ContentValues();
		values.put(DatabaseTest.Status.NAME, "changed");
		assertEquals(0, resolver.update(dirUri(DatabaseTest.Status.TABLE_NAME), values,
				DatabaseTest.Status.ID + "=?", new String[]{"404"}));
		assertEquals(1, resolver.update(dirUri(DatabaseTest.Status.TABLE_NAME), values,
				DatabaseTest.Status.ID + "=?", new String[]{"1"}));

		observer.awaitChange();
		Thread.sleep(QUIET_MILLIS);
		assertEquals("only the update that changed a row should notify", 1, observer.count());
	}

	// ---- helpers ------------------------------------------------------------------------

	private static Uri dirUri(String tableName) {
		return TestProvider.CONTENT_HELPER.getDirUri(tableName);
	}

	private CountingObserver observe(Uri uri) {
		final CountingObserver observer = new CountingObserver();
		resolver.registerContentObserver(uri, false, observer);
		registered.add(observer);
		return observer;
	}

	private void insertStatus(int id, String name) {
		final ContentValues values = new ContentValues();
		values.put(DatabaseTest.Status.ID, id);
		values.put(DatabaseTest.Status.NAME, name);
		assertNotNull(resolver.insert(dirUri(DatabaseTest.Status.TABLE_NAME), values));
	}

	private void insertAllTypes(int id) {
		final ContentValues values = new ContentValues();
		values.put(DatabaseTest.AllTypes.ID, id);
		values.put(DatabaseTest.AllTypes.NAME, "row " + id);
		values.put(DatabaseTest.AllTypes.GUID, UUID.randomUUID().toString());
		values.put(DatabaseTest.AllTypes.FLAG, 1);
		assertNotNull(resolver.insert(dirUri(DatabaseTest.AllTypes.TABLE_NAME), values));
	}

	/** null handler, so changes are delivered straight to the calling thread. */
	private static class CountingObserver extends ContentObserver {

		private final CountDownLatch first = new CountDownLatch(1);
		private final AtomicInteger changes = new AtomicInteger();

		CountingObserver() {
			super(null);
		}

		@Override
		public void onChange(boolean selfChange) {
			changes.incrementAndGet();
			first.countDown();
		}

		void awaitChange() throws InterruptedException {
			assertTrue("no change notification within " + NOTIFY_TIMEOUT_SECONDS + "s",
					first.await(NOTIFY_TIMEOUT_SECONDS, TimeUnit.SECONDS));
		}

		int count() {
			return changes.get();
		}
	}
}
