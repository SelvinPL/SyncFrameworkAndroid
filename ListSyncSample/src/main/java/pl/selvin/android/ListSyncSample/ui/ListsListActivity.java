package pl.selvin.android.listsyncsample.ui;

import android.os.Bundle;

import pl.selvin.android.listsyncsample.R;

public class ListsListActivity extends SyncActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.listslist);
        getSupportActionBar().setTitle(R.string.ui_listslistactivity_title);
    }
}
