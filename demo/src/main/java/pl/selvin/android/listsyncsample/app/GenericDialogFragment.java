/*
 * Copyright (c) 2014-2016 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package pl.selvin.android.listsyncsample.app;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.DialogFragment;

import java.util.Calendar;

import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.utils.DateTimeUtils;

public interface GenericDialogFragment {
	String MESSAGE = "MESSAGE";
	String TITLE = "TITLE";
	String URI = "URI";
	String ID = "ID";
	String DATE = "DATE";
	String MAX_DATE = "MAX_DATE";
	String MIN_DATE = "MIN_DATE";
	String DIALOG_FRAGMENT_TAG = "DIALOG";

	@SuppressLint("ValidFragment")
	class ConfirmDelete extends DialogFragment implements GenericDialogFragment {

		@SuppressWarnings("SameParameterValue")
		static ConfirmDelete newInstance(int id, @StringRes int title, @StringRes int message, @NonNull Uri uri) {
			ConfirmDelete frag = new ConfirmDelete();
			Bundle args = new Bundle();
			args.putParcelable(URI, uri);
			args.putInt(TITLE, title);
			args.putInt(MESSAGE, message);
			args.putInt(ID, id);
			frag.setArguments(args);
			return frag;
		}

		private void runCallback(int ID, boolean canceled) {
			boolean handled = false;
			if (getParentFragment() != null && getParentFragment() instanceof Callback)
				handled = ((Callback) getParentFragment()).onAction(ID, canceled);
			if (!handled && getActivity() instanceof Callback)
				((Callback) getActivity()).onAction(ID, canceled);
		}

		@NonNull
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			final Bundle arguments = requireArguments();
			final int title = arguments.getInt(TITLE);
			final int message = arguments.getInt(MESSAGE);
			final Uri uri = BundleCompat.getParcelable(arguments, URI, Uri.class);
			return new AlertDialog.Builder(requireActivity())
					.setTitle(title)
					.setMessage(message)
					.setPositiveButton(android.R.string.ok,
							(dialog, whichButton) -> {
								if (uri != null) {
									requireActivity().getContentResolver().delete(uri, null, null);
									runCallback(arguments.getInt(ID), false);
								}
							})
					.setNegativeButton(android.R.string.cancel,
							(dialog, whichButton) -> runCallback(arguments.getInt(ID), true)).create();
		}

		public interface Callback {
			boolean onAction(int ID, boolean canceled);
		}
	}

	class DatePicker extends DialogFragment implements DatePickerDialog.OnDateSetListener, GenericDialogFragment {

		static final Calendar MAX_DATE_VALUE = DateTimeUtils.addYears(DateTimeUtils.today(), 2);

		public static DatePicker newInstance(int id, Calendar date) {
			return newInstance(id, date, DateTimeUtils.today(), MAX_DATE_VALUE);
		}

		public static DatePicker newInstance(int id, Calendar date, Calendar minDate, Calendar maxDate) {
			DatePicker frag = new DatePicker();
			Bundle args = new Bundle();
			args.putSerializable(DATE, date);
			args.putSerializable(MIN_DATE, minDate);
			args.putSerializable(MAX_DATE, maxDate == null ? MAX_DATE_VALUE : maxDate);
			args.putInt(ID, id);
			frag.setArguments(args);
			return frag;
		}

		@NonNull
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			final Bundle arguments = requireArguments();
			final Calendar calendar = DateTimeUtils.getCalendarFromBundle(arguments, DATE);
			if (calendar != null) {
				final DatePickerDialog dialog = new DatePickerDialog(this.requireActivity(), R.style.AppTheme_Light_Dialog, this,
						calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
				setMaxDate(dialog, DateTimeUtils.getCalendarFromBundle(arguments, MAX_DATE));
				setMinDate(dialog, DateTimeUtils.getCalendarFromBundle(arguments, MIN_DATE));
				return dialog;
			}
			throw new RuntimeException("No DATE argument");
		}

		@Override
		public void onDateSet(android.widget.DatePicker view, int year, int monthOfYear, int dayOfMonth) {
			final Bundle arguments = requireArguments();
			final Calendar minDate = DateTimeUtils.getCalendarFromBundle(arguments, MIN_DATE);
			final Calendar maxDate = DateTimeUtils.getCalendarFromBundle(arguments, MAX_DATE);
			final Calendar calendar = DateTimeUtils.today();
			calendar.set(Calendar.YEAR, year);
			calendar.set(Calendar.MONTH, monthOfYear);
			calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
			if (minDate != null && calendar.compareTo(minDate) < 0)
				runCallback(arguments.getInt(ID), false, minDate);
			else if (maxDate != null && calendar.compareTo(maxDate) > 0)
				runCallback(arguments.getInt(ID), false, maxDate);
			else
				runCallback(arguments.getInt(ID), false, calendar);
		}

		private void runCallback(final int ID, boolean canceled, Calendar date) {
			boolean handled = false;
			if (getParentFragment() != null && getParentFragment() instanceof Callback)
				handled = ((Callback) getParentFragment()).onAction(ID, canceled, date);
			if (!handled && getActivity() instanceof Callback)
				((Callback) getActivity()).onAction(ID, canceled, date);
		}

		public void onCancel(@NonNull DialogInterface dialog) {
			final Bundle arguments = requireArguments();
			final Calendar calendar = DateTimeUtils.getCalendarFromBundle(arguments, DATE);
			runCallback(arguments.getInt(ID), true, calendar);
			super.onCancel(dialog);
		}

		private void setMaxDate(DatePickerDialog dialog, Calendar maxDate) {
			dialog.getDatePicker().setMaxDate(maxDate.getTimeInMillis());
		}

		private void setMinDate(DatePickerDialog dialog, Calendar minDate) {
			dialog.getDatePicker().setMinDate(minDate.getTimeInMillis());
		}

		public interface Callback {
			boolean onAction(final int ID, boolean canceled, Calendar date);
		}
	}

	@SuppressLint("ValidFragment")
	class TimePicker extends DialogFragment implements TimePickerDialog.OnTimeSetListener, GenericDialogFragment {


		public static TimePicker newInstance(int id, Calendar date) {
			TimePicker frag = new TimePicker();
			Bundle args = new Bundle();
			args.putLong(DATE, date.getTimeInMillis());
			args.putInt(ID, id);
			frag.setArguments(args);
			return frag;
		}

		@NonNull
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			final Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis(requireArguments().getLong(DATE));
			return new TimePickerDialog(getActivity(), R.style.AppTheme_Light_Dialog, this, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true);
		}

		private void runCallback(final int ID, boolean canceled, Calendar date) {
			boolean handled = false;
			if (getParentFragment() != null && getParentFragment() instanceof Callback)
				handled = ((Callback) getParentFragment()).onAction(ID, canceled, date);
			if (!handled && getActivity() instanceof Callback)
				((Callback) getActivity()).onAction(ID, canceled, date);
		}

		public void onCancel(@NonNull DialogInterface dialog) {
			final Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis(requireArguments().getLong(DATE));
			runCallback(requireArguments().getInt(ID), true, calendar);
			super.onCancel(dialog);
		}

		@Override
		public void onTimeSet(android.widget.TimePicker timePicker, int hourOfDay, int minute) {
			final Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis(requireArguments().getLong(DATE));
			calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
			calendar.set(Calendar.MINUTE, minute);
			calendar.set(Calendar.SECOND, 0);
			calendar.set(Calendar.MILLISECOND, 0);
			runCallback(requireArguments().getInt(ID), false, calendar);
		}

		public interface Callback {
			boolean onAction(final int ID, boolean canceled, Calendar date);
		}
	}
}