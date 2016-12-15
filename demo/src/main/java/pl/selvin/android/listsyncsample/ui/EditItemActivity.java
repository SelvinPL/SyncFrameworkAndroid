/***
 * Copyright (c) 2014 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package pl.selvin.android.listsyncsample.ui;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.app.DatePickerFragment;
import pl.selvin.android.listsyncsample.app.TimePickerFragment;
import pl.selvin.android.listsyncsample.provider.Database;
import pl.selvin.android.listsyncsample.provider.Database.Item;
import pl.selvin.android.listsyncsample.provider.ListProvider;
import pl.selvin.android.listsyncsample.support.SyncHelper;


public class EditItemActivity extends AppCompatActivity implements
        OnClickListener, LoaderManager.LoaderCallbacks<Cursor> {
    final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    final static SimpleDateFormat sdfdate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    final static SimpleDateFormat sdftime = new SimpleDateFormat("HH:mm", Locale.getDefault());
    final static String UserDataView = "view";
    final static String TimePickerFragmentTag = "TimePickerFragment";
    final static String DatePickerFragmentTag = "DatePickerFragment";
    private final static int PriorityLoaderId = 1;
    private final static int StatusLoaderId = 2;
    private final static int ItemLoaderId = 3;
    DatePickerFragment.OnDateSetListener dpdfListener = new DatePickerFragment.OnDateSetListener() {

        @Override
        public void setDate(Calendar calendar, Bundle userData) {
            final TextView tv = (TextView) findViewById(userData.getInt(UserDataView));
            tv.setTag(calendar);
            tv.setText(sdfdate.format(calendar.getTime()));
        }
    };
    TimePickerFragment.OnTimeSetListener tpdfListener = new TimePickerFragment.OnTimeSetListener() {

        @Override
        public void setTime(Calendar calendar, Bundle userData) {
            final TextView tv = (TextView) findViewById(userData.getInt(UserDataView));
            tv.setTag(calendar);
            tv.setText(sdftime.format(calendar.getTime()));
        }
    };
    EditText edName, edDesc;
    TextView tStartDate, tStartTime, tEndDate, tEndTime;
    Spinner sPriority, sStatus;
    SimpleCursorAdapter adPriority, adStatus;
    Uri id = null;
    String listID = null;
    String UserID;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            UserID = SyncHelper.getUserId(this);
        } catch (Exception ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
        setContentView(R.layout.edit_item_activity);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        edName = (EditText) findViewById(R.id.edName);
        edDesc = (EditText) findViewById(R.id.edDesc);
        sPriority = (Spinner) findViewById(R.id.sPriority);
        sStatus = (Spinner) findViewById(R.id.sStatus);
        tStartDate = (TextView) findViewById(R.id.tStartDate);
        tStartTime = (TextView) findViewById(R.id.tStartTime);
        tEndDate = (TextView) findViewById(R.id.tEndDate);
        tEndTime = (TextView) findViewById(R.id.tEndTime);
        tStartDate.setOnClickListener(this);
        tStartTime.setOnClickListener(this);
        tEndDate.setOnClickListener(this);
        tEndTime.setOnClickListener(this);
        adPriority = new SimpleCursorAdapter(this,
                R.layout.support_simple_spinner_dropdown_item, null,
                new String[]{Database.Priority.C_NAME},
                new int[]{android.R.id.text1}, 0);
        adPriority.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sPriority.setAdapter(adPriority);
        adStatus = new SimpleCursorAdapter(this,
                R.layout.support_simple_spinner_dropdown_item, null,
                new String[]{Database.Status.NAME},
                new int[]{android.R.id.text1}, 0);
        adStatus.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sStatus.setAdapter(adStatus);
        Intent intent = getIntent();
        if (!Intent.ACTION_INSERT.endsWith(intent.getAction())) {
            id = intent.getData();
        } else {
            listID = intent.getStringExtra(Item.LISTID);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            tStartDate.setTag(cal);
            tStartDate.setText(sdfdate.format(cal.getTime()));
            tStartTime.setTag(cal);
            tStartTime.setText(sdftime.format(cal.getTime()));
            cal = Calendar.getInstance();
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.add(Calendar.DAY_OF_MONTH, 1);
            tEndDate.setTag(cal);
            tEndDate.setText(sdfdate.format(cal.getTime()));
            tEndTime.setTag(cal);
            tEndTime.setText(sdftime.format(cal.getTime()));
        }

        final DatePickerFragment dateFragment = (DatePickerFragment) getSupportFragmentManager().findFragmentByTag(DatePickerFragmentTag);
        if (dateFragment != null) {
            dateFragment.setOnDateSetListener(dpdfListener);
        }
        final TimePickerFragment timeFragment = (TimePickerFragment) getSupportFragmentManager().findFragmentByTag(TimePickerFragmentTag);
        if (timeFragment != null) {
            timeFragment.setOnTimeSetListener(tpdfListener);
        }
        getSupportLoaderManager().initLoader(PriorityLoaderId, null, this);
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
                    values.put(Database.Item.NAME, name);
                    values.put(Database.Item.DESCRIPTION, desc);
                    Cursor c = (Cursor) sPriority.getSelectedItem();
                    values.put(Database.Item.PRIORITY,
                            c.getInt(c.getColumnIndex(BaseColumns._ID)));
                    c = (Cursor) sStatus.getSelectedItem();
                    values.put(Database.Item.STATUS,
                            c.getInt(c.getColumnIndex(BaseColumns._ID)));
                    Calendar cal = (Calendar) tStartDate.getTag();
                    values.put(Database.Item.STARTDATE, sdf.format(cal.getTime()));
                    cal = (Calendar) tEndDate.getTag();
                    values.put(Database.Item.ENDDATE, sdf.format(cal.getTime()));
                    if (id == null) {
                        values.put(Database.Item.ID, UUID.randomUUID().toString());
                        values.put(Database.Item.LISTID, listID);
                        values.put(Database.Item.USERID, UserID);
                        getContentResolver().insert(
                                ListProvider.getHelper().getDirUri(Database.Item.TABLE_NAME),
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


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Calendar cal = (Calendar) tStartDate.getTag();
        outState.putSerializable("StartDate", cal);
        cal = (Calendar) tEndDate.getTag();
        outState.putSerializable("EndDate", cal);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Calendar cal = (Calendar) savedInstanceState.getSerializable("StartDate");
        if (cal != null) {
            tStartDate.setTag(cal);
            tStartDate.setText(sdfdate.format(cal.getTime()));
            tStartTime.setTag(cal);
            tStartTime.setText(sdftime.format(cal.getTime()));
        }
        cal = (Calendar) savedInstanceState.getSerializable("EndDate");
        if (cal != null) {
            tEndDate.setTag(cal);
            tEndDate.setText(sdfdate.format(cal.getTime()));
            tEndTime.setTag(cal);
            tEndTime.setText(sdftime.format(cal.getTime()));
        }
    }


    @Override
    public void onClick(final View v) {
        final int vId = v.getId();
        final Bundle userData = new Bundle();
        userData.putInt(UserDataView, vId);
        switch (vId) {
            case R.id.tStartDate:
            case R.id.tEndDate:
                final DatePickerFragment dateFragment = DatePickerFragment.newInstance((Calendar) v.getTag(), userData);
                dateFragment.setOnDateSetListener(dpdfListener);
                dateFragment.show(getSupportFragmentManager(), DatePickerFragmentTag);
                break;
            case R.id.tStartTime:
            case R.id.tEndTime:
                final TimePickerFragment timeFragment = TimePickerFragment.newInstance((Calendar) v.getTag(), userData);
                timeFragment.setOnTimeSetListener(tpdfListener);
                timeFragment.show(getSupportFragmentManager(), TimePickerFragmentTag);
                break;
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int lid, Bundle args) {
        switch (lid) {
            case PriorityLoaderId:
                return new CursorLoader(this,
                        ListProvider.getHelper().getDirUri(Database.Priority.TABLE_NAME),
                        new String[]{BaseColumns._ID,
                                Database.Priority.C_NAME}, null, null, null);
            case StatusLoaderId:
                return new CursorLoader(this,
                        ListProvider.getHelper().getDirUri(Database.Status.TABLE_NAME),
                        new String[]{BaseColumns._ID, Database.Status.NAME},
                        null, null, null);
            case ItemLoaderId:
                return new CursorLoader(this, id, new String[]{Database.Item.NAME,
                        Database.Item.DESCRIPTION, Database.Item.PRIORITY,
                        Database.Item.STATUS, Database.Item.STARTDATE,
                        Database.Item.ENDDATE}, null, null, null);
            default:
                return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        final int lid = cursorLoader.getId();
        switch (lid) {
            case PriorityLoaderId:
                adPriority.swapCursor(cursor);
                getSupportLoaderManager().initLoader(StatusLoaderId, null, this);
                return;
            case StatusLoaderId:
                adStatus.swapCursor(cursor);
                if (id != null) {
                    getSupportLoaderManager().initLoader(ItemLoaderId, null, this);
                }
                return;
            case ItemLoaderId:
                if (cursor.moveToFirst()) {
                    setTitle(R.string.ui_edit_item);
                    edName.setText(cursor.getString(0));
                    edDesc.setText(cursor.getString(1));
                    SetupSpinner(cursor.getInt(2), sPriority);
                    SetupSpinner(cursor.getInt(3), sStatus);
                    String time = cursor.getString(4);
                    Calendar cal = Calendar.getInstance();
                    try {
                        cal.setTime(sdf.parse(time));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    tStartDate.setTag(cal);
                    tStartDate.setText(sdfdate.format(cal.getTime()));
                    tStartTime.setTag(cal);
                    tStartTime.setText(sdftime.format(cal.getTime()));
                    time = cursor.getString(5);
                    cal = Calendar.getInstance();
                    try {
                        cal.setTime(sdf.parse(time));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    tEndDate.setTag(cal);
                    tEndDate.setText(sdfdate.format(cal.getTime()));
                    tEndTime.setTag(cal);
                    tEndTime.setText(sdftime.format(cal.getTime()));
                } else {
                    finish();
                }
                return;
            default:
        }
    }

    void SetupSpinner(final int sid, final Spinner spinner) {
        for (int i = 0; i < spinner.getCount(); i++) {
            Cursor value = (Cursor) spinner.getItemAtPosition(i);
            int id = value
                    .getInt(value.getColumnIndex(BaseColumns._ID));
            if (id == sid) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        final int id = cursorLoader.getId();
        switch (id) {
            case PriorityLoaderId:
                adPriority.swapCursor(null);
                return;
            case StatusLoaderId:
                adStatus.swapCursor(null);
                return;
            default:

        }
    }
}
