package pl.selvin.android.listsyncsample.ui;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.provider.Database;
import pl.selvin.android.listsyncsample.provider.ListProvider;
import pl.selvin.android.listsyncsample.support.SyncHelper;

public class EditListActivity extends ActionBarActivity {
    EditText edName, edDesc;
    Uri id = null;
    String UserID;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            UserID = SyncHelper.createInstance(this).getUserId();
        } catch (Exception ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
        setContentView(R.layout.edit_list_activity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        edName = (EditText) findViewById(R.id.edName);
        edDesc = (EditText) findViewById(R.id.edDesc);
        Intent intent = getIntent();
        if (!Intent.ACTION_INSERT.endsWith(intent.getAction())) {
            id = intent.getData();
            Cursor cursor = getContentResolver().query(
                    id,
                    new String[]{Database.List.NAME,
                            Database.List.DESCRIPTION}, null, null, null);
            if (cursor.moveToFirst()) {
                edName.setText(cursor.getString(0));
                edDesc.setText(cursor.getString(1));
                setTitle(R.string.ui_edit_list);
                cursor.close();
            } else {
                cursor.close();
                finish();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.save_cancel, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
            case R.id.ui_edit_cancel:
                finish();
                return true;
            case R.id.ui_edit_save:
                String name = edName.getText().toString(),
                        desc = edDesc.getText().toString();
                if (name.length() != 0 && desc.length() != 0) {
                    ContentValues values = new ContentValues();
                    values.put(Database.List.NAME, name);
                    values.put(Database.List.DESCRIPTION, desc);
                    if (id == null) {
                        values.put(Database.List.ID, UUID.randomUUID().toString());
                        values.put(Database.List.USERID, UserID);
                        values.put(Database.List.CREATEDATE, new SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss").format(new Date()));
                        getContentResolver().insert(
                                ListProvider.getHelper().getDirUri(Database.List.TABLE_NAME), // content://pl.selvin.android.listsyncsample/Lists
                                values);
                    } else {
                        getContentResolver().update(id, values, null, null);
                    }
                    finish();
                } else {
                    Toast.makeText(
                            this,
                            getResources().getString(
                                    R.string.ui_name_description_empty),
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
