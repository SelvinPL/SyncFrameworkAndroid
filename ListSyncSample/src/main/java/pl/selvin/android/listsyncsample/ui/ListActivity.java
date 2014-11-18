/***
 Copyright (c) 2014 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */

package pl.selvin.android.listsyncsample.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;


public class ListActivity extends SyncActivity {

    String listID = null;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ListFragment details = null;
        Intent intent = getIntent();
        Uri uri = intent.getData();
        Configuration config = getResources().getConfiguration();
        if (uri != null) {
            String sId = uri.getLastPathSegment();
            details = ListFragment.newInstance(Long.parseLong(sId), false);
        } else {
            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // && (config.screenLayout &
                // Configuration.SCREENLAYOUT_SIZE_MASK) ==
                // Configuration.SCREENLAYOUT_SIZE_LARGE) {
                finish();
                return;
            }
            details = new ListFragment();
            details.setArguments(getIntent().getExtras());
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, details).commit();
        /*-
		setContentView(R.layout.list_activity);
		getSupportActionBar().setTitle(R.string.ui_listactivity_title);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		Intent intent = getIntent();
		if (intent != null) {
			Uri uri = intent.getData();
			if (uri != null) {
				Cursor cursor = managedQuery(uri, new String[] {
						BaseColumns._ID, Database.List.NAME,
						Database.List.DESCRIPTION, Database.List.CREATEDATE,
						Database.List.ID }, null, null, null);

				if (cursor == null) {
					finish();
				} else {
					if (cursor.moveToFirst()) {
						listID = cursor.getString(4);
						ListView listView = (ListView) findViewById(R.id.listView);
						registerForContextMenu(listView);
						((TextView) findViewById(R.id.tName)).setText(cursor
								.getString(1));
						((TextView) findViewById(R.id.tDescription))
								.setText(cursor.getString(2));
						((TextView) findViewById(R.id.tCreatedDate))
								.setText(cursor.getString(3));
						listView.setAdapter(new SimpleCursorAdapter(this,
								R.layout.itemrow, managedQuery(ListProvider
										.getDirUri(Database.Item.TABLE_NAME),
										new String[] { BaseColumns._ID,
												Database.Item.ID,
												Database.Item.NAME,
												Database.Item.DESCRIPTION },
										Database.Item.LISTID + "=?",
										new String[] { listID }, null),
								new String[] { Database.Item.NAME,
										Database.Item.DESCRIPTION }, new int[] {
										R.id.tName, R.id.tDescription }));
					} else {
						finish();
					}

				}
			}
		}*/
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
