package pl.selvin.android.ListSyncSample.ui;

import pl.selvin.android.ListSyncSample.R;
import android.os.Bundle;

public class ListsListActivity extends SyncActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.listslist);
		getSupportActionBar().setTitle(R.string.ui_listslistactivity_title);
	}
}
