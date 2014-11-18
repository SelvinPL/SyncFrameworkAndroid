package pl.selvin.android.ListSyncSample.ui;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.UUID;

import pl.selvin.android.ListSyncSample.R;
import pl.selvin.android.ListSyncSample.provider.Database;
import pl.selvin.android.ListSyncSample.provider.Database.Item;
import pl.selvin.android.ListSyncSample.provider.ListProvider;
import pl.selvin.android.ListSyncSample.support.SyncHelper;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.v7.app.ActionBarActivity;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;


public class EditItemActivity extends ActionBarActivity implements
		OnClickListener {
	EditText edName, edDesc;
	TextView tStartDate, tStartTime, tEndDate, tEndTime;
	Spinner sPriority, sStatus;
	Uri id = null;
	TextView lastDateView = null;

	final static SimpleDateFormat sdf = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss");
	final static SimpleDateFormat sdfdate = new SimpleDateFormat("yyyy-MM-dd");
	final static SimpleDateFormat sdftime = new SimpleDateFormat("HH:mm");

	String listID = null;
	static final int TIME_DIALOG_ID = 0;
	static final int DATE_DIALOG_ID = 1;
	static final int TAG_DIALOG_ID = 2;

	String UserID;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			UserID = SyncHelper.createInstance(this).getUserId();
		} catch (Exception ex) {
			Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
			finish();
		}
		setContentView(R.layout.edit_item_activity);
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
		SimpleCursorAdapter adPriority = new SimpleCursorAdapter(this,
				android.R.layout.simple_spinner_item, managedQuery(
						ListProvider.getHelper().getDirUri(Database.Priority.TABLE_NAME),
						new String[] { BaseColumns._ID,
								Database.Priority.C_NAME }, null, null, null),
				new String[] { Database.Priority.C_NAME },
				new int[] { android.R.id.text1 });
		adPriority
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		sPriority.setAdapter(adPriority);
		SimpleCursorAdapter adStatus = new SimpleCursorAdapter(this,
				android.R.layout.simple_spinner_item, managedQuery(
						ListProvider.getHelper().getDirUri(Database.Status.TABLE_NAME),
						new String[] { BaseColumns._ID, Database.Status.NAME },
						null, null, null),
				new String[] { Database.Status.NAME },
				new int[] { android.R.id.text1 });
		adStatus.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		sStatus.setAdapter(adStatus);
		Intent intent = getIntent();
		if (!Intent.ACTION_INSERT.endsWith(intent.getAction())) {
			id = intent.getData();
			Cursor cursor = managedQuery(id, new String[] { Database.Item.NAME,
					Database.Item.DESCRIPTION, Database.Item.PRIORITY,
					Database.Item.STATUS, Database.Item.STARTDATE,
					Database.Item.ENDDATE }, null, null, null);
			if (cursor.moveToFirst()) {
				setTitle(R.string.ui_edit_item);
				edName.setText(cursor.getString(0));
				edDesc.setText(cursor.getString(1));
				int sid = cursor.getInt(2);
				for (int i = 0; i < sPriority.getCount(); i++) {
					Cursor value = (Cursor) sPriority.getItemAtPosition(i);
					int id = value
							.getInt(value.getColumnIndex(BaseColumns._ID));
					if (id == sid) {
						sPriority.setSelection(i);
						break;
					}
				}
				sid = cursor.getInt(3);
				for (int i = 0; i < sStatus.getCount(); i++) {
					Cursor value = (Cursor) sStatus.getItemAtPosition(i);
					int id = value
							.getInt(value.getColumnIndex(BaseColumns._ID));
					if (id == sid) {
						sStatus.setSelection(i);
						break;
					}
				}
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
		} else {
			listID = intent.getStringExtra(Item.LISTID);
			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.SECOND, 0);
			tStartDate.setTag(cal);
			tStartDate.setText(sdfdate.format(cal.getTime()));
			tStartTime.setTag(cal);
			tStartTime.setText(sdftime.format(cal.getTime()));
			cal = Calendar.getInstance();
			cal.set(Calendar.SECOND, 0);
			cal.add(Calendar.DAY_OF_MONTH, 1);
			tEndDate.setTag(cal);
			tEndDate.setText(sdfdate.format(cal.getTime()));
			tEndTime.setTag(cal);
			tEndTime.setText(sdftime.format(cal.getTime()));
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.save_cancel, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case TIME_DIALOG_ID:
			Calendar cal = (Calendar) lastDateView.getTag();
			return new TimePickerDialog(this, mTimeSetListener,
					cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE),
					DateFormat.is24HourFormat(this));
		case DATE_DIALOG_ID:
			cal = (Calendar) lastDateView.getTag();
			return new DatePickerDialog(this, mDateSetListener,
					cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
					cal.get(Calendar.DAY_OF_MONTH));
		case TAG_DIALOG_ID:
			return new AlertDialog.Builder(this).setTitle("Tags").create();// .setMultiChoiceItems(arg0,
																			// arg1,
																			// arg2,
																			// arg3)
		}
		return null;
	}

	private DatePickerDialog.OnDateSetListener mDateSetListener = new DatePickerDialog.OnDateSetListener() {

		public void onDateSet(DatePicker view, int year, int monthOfYear,
				int dayOfMonth) {
			if (lastDateView != null) {
				Calendar cal = (Calendar) lastDateView.getTag();
				cal.set(Calendar.YEAR, year);
				cal.set(Calendar.MONTH, monthOfYear);
				cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
				lastDateView.setText(sdfdate.format(cal.getTime()));
				lastDateView = null;
			}
		}
	};

	private TimePickerDialog.OnTimeSetListener mTimeSetListener = new TimePickerDialog.OnTimeSetListener() {

		public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
			if (lastDateView != null) {
				Calendar cal = (Calendar) lastDateView.getTag();
				cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
				cal.set(Calendar.MINUTE, minute);
				lastDateView.setText(sdftime.format(cal.getTime()));
				lastDateView = null;
			}
		}
	};

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
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.tStartDate:
		case R.id.tEndDate:
			lastDateView = (TextView) v;
			showDialog(DATE_DIALOG_ID);
			break;
		case R.id.tStartTime:
		case R.id.tEndTime:
			lastDateView = (TextView) v;
			showDialog(TIME_DIALOG_ID);
			break;
		}
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		switch (id) {
		case TIME_DIALOG_ID:
			Calendar cal = (Calendar) lastDateView.getTag();
			((TimePickerDialog) dialog).updateTime(
					cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
			break;
		case DATE_DIALOG_ID:
			cal = (Calendar) lastDateView.getTag();
			((DatePickerDialog) dialog).updateDate(cal.get(Calendar.YEAR),
					cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
			break;
		}
	}
}
