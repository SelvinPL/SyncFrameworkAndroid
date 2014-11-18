package pl.selvin.android.listsyncsample.ui;

import pl.selvin.android.listsyncsample.R;
import android.os.Bundle;

public class ListsListActivity extends SyncActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.listslist);
		getSupportActionBar().setTitle(R.string.ui_listslistactivity_title);
	}
}
