package pl.selvin.android.ListSyncSample.ui;

import pl.selvin.android.ListSyncSample.R;
import pl.selvin.android.ListSyncSample.provider.Database;
import pl.selvin.android.ListSyncSample.provider.Database.TagItemMapping;
import pl.selvin.android.ListSyncSample.provider.ListProvider;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;


public class ItemActivity extends ActionBarActivity {

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.item_activity);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		Intent intent = getIntent();
		if (intent != null) {
			Uri uri = intent.getData();
			if (uri != null) {
				Cursor cursor = managedQuery(uri, new String[] {
						Database.Item.NAME, Database.Item.DESCRIPTION,
						Database.Item.STARTDATE, Database.Item.ENDDATE,
						Database.Item.PRIORITY, Database.Item.STATUS,
						Database.Item.ID, Database.Item.USERID }, null, null,
						null);

				if (cursor == null) {
					finish();
				} else {
					if (cursor.moveToFirst()) {
						((ImageView) findViewById(R.id.iItem)).setPadding(6, 6,
								6, 6);
						((TextView) findViewById(R.id.tName)).setText(cursor
								.getString(0));
						((TextView) findViewById(R.id.tDescription))
								.setText(cursor.getString(1));
						((TextView) findViewById(R.id.tStartDate))
								.setText(cursor.getString(2));
						((TextView) findViewById(R.id.tEndDate)).setText(cursor
								.getString(3));
						((TextView) findViewById(R.id.tPriority))
								.setText(cursor.getString(4));
						((TextView) findViewById(R.id.tStatus)).setText(cursor
								.getString(5));
						Cursor tags = managedQuery(
								ListProvider
										.getHelper().getDirUri(Database.TagItemMapping.TABLE_NAME),
								new String[] { TagItemMapping.TAGID },
								String.format("%s=? AND %s=?",
										TagItemMapping.ITEMID,
										TagItemMapping.USERID),
								new String[] { cursor.getString(6),
										cursor.getString(7) }, null);
						if (tags.moveToFirst()) {
							StringBuilder sb = new StringBuilder();
							do {
								if (sb.length() > 0)
									sb.append(", ");
								sb.append(tags.getString(0));
							} while (tags.moveToNext());
							((TextView) findViewById(R.id.tTags)).setText(sb
									.toString());
						}
					} else {
						finish();
					}

				}
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
		case R.id.ui_edit_cancel:
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}
}
