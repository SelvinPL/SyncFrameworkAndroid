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
import android.annotation.TargetApi;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import java.util.Calendar;

import pl.selvin.android.listsyncsample.R;
import pl.selvin.android.listsyncsample.utils.DateTimeUtils;


public interface GenericDialogFragment {
    String MESSAGE = "MESSAGE";
    String TITLE = "TITLE";
    String URI = "URI";
    String ID = "ID";
    String ARGS = "ARGS";
    String VIEW = "VIEW";
    String DATE = "DATE";
    String MAX_DATE = "MAX_DATE";
    String MIN_DATE = "MIN_DATE";
    String DIALOG_FRAGMENT_TAG = "DIALOG";

    abstract class DialogFragment extends androidx.fragment.app.DialogFragment {
        @NonNull
        protected final Bundle requireArguments() {
            final Bundle args = getArguments();
            if (args == null) {
                throw new IllegalStateException("Fragment " + this + " has no arguments.");
            }
            return args;
        }
    }

    @SuppressWarnings("unused")
    @SuppressLint("ValidFragment")
    class Info extends DialogFragment implements GenericDialogFragment {

        public static Info newInstance(@StringRes int title, @LayoutRes int view) {
            Info frag = new Info();
            Bundle args = new Bundle();
            args.putInt(TITLE, title);
            args.putInt(VIEW, view);
            frag.setArguments(args);
            return frag;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final int title = requireArguments().getInt(TITLE);
            final int view = requireArguments().getInt(VIEW);
            return new AlertDialog.Builder(requireActivity())
                    .setTitle(title).setView(view)
                    .setPositiveButton(android.R.string.ok, null).create();
        }
    }

    @SuppressWarnings("unused")
    @SuppressLint("ValidFragment")
    class OKCancel extends DialogFragment implements GenericDialogFragment {

        public static OKCancel newInstance(int id, @StringRes int title, @StringRes int message, Parcelable argsIn) {
            OKCancel frag = new OKCancel();
            Bundle args = new Bundle();
            args.putInt(ID, id);
            args.putInt(TITLE, title);
            args.putInt(MESSAGE, message);
            args.putParcelable(ARGS, argsIn);
            frag.setArguments(args);
            return frag;
        }

        public void onCancel(DialogInterface dialog) {
            runCallback(requireArguments().getInt(ID), true, requireArguments().getParcelable(ARGS));
            super.onCancel(dialog);
        }

        private void runCallback(final int ID, boolean canceled, final Parcelable args) {
            boolean handled = false;
            if (getParentFragment() != null && getParentFragment() instanceof Callback)
                handled = ((Callback) getParentFragment()).onAction(ID, canceled, args);
            if (!handled && getActivity() instanceof Callback)
                ((Callback) getActivity()).onAction(ID, canceled, args);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final int title = requireArguments().getInt(TITLE);
            final int message = requireArguments().getInt(MESSAGE);
            return new AlertDialog.Builder(requireActivity())
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int whichButton) {
                                    runCallback(requireArguments().getInt(ID), false, requireArguments().getParcelable(ARGS));
                                }
                            })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int whichButton) {
                                    dialog.cancel();
                                }
                            }).create();
        }

        public interface Callback {
            boolean onAction(final int ID, boolean canceled, final Parcelable args);
        }
    }

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

        public interface Callback {
            boolean onAction(int ID, boolean canceled);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final int title = requireArguments().getInt(TITLE);
            final int message = requireArguments().getInt(MESSAGE);
            final Uri uri = requireArguments().getParcelable(URI);
            return new AlertDialog.Builder(requireActivity())
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int whichButton) {
                                    if (uri != null) {
                                        requireActivity().getContentResolver().delete(uri, null, null);
                                        runCallback(requireArguments().getInt(ID), false);
                                    }
                                }
                            })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int whichButton) {
                                    runCallback(requireArguments().getInt(ID), true);
                                }
                            }).create();
        }
    }

    @SuppressLint("ValidFragment")
    class DatePicker extends DialogFragment implements DatePickerDialog.OnDateSetListener, GenericDialogFragment {

        public static DatePicker newInstance(int id, Calendar date) {
            final Calendar MAX_DATE = DateTimeUtils.getToday();
            MAX_DATE.add(Calendar.YEAR, 100);
            final Calendar MIN_DATE = DateTimeUtils.getToday();
            MIN_DATE.add(Calendar.YEAR, -100);
            return newInstance(id, date, MIN_DATE, MAX_DATE);
        }

        static DatePicker newInstance(int id, Calendar date, Calendar minDate, Calendar maxDate) {
            DatePicker frag = new DatePicker();
            Bundle args = new Bundle();
            args.putLong(DATE, date.getTimeInMillis());
            args.putLong(MIN_DATE, minDate.getTimeInMillis());
            args.putLong(MAX_DATE, maxDate.getTimeInMillis());
            args.putInt(ID, id);
            frag.setArguments(args);
            return frag;
        }

        @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(requireArguments().getLong(DATE));
            final DatePickerDialog dialog = new DatePickerDialog(requireActivity(), R.style.AppTheme_Light_Dialog, this, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
            DatePickerDialogCompatHelper.setMaxDate(dialog, requireArguments().getLong(MAX_DATE));
            DatePickerDialogCompatHelper.setMinDate(dialog, requireArguments().getLong(MIN_DATE));
            return dialog;
        }

        @Override
        public void onDateSet(android.widget.DatePicker view, int year, int monthOfYear, int dayOfMonth) {
            final Calendar minDate = Calendar.getInstance();
            final Calendar maxDate = Calendar.getInstance();
            minDate.setTimeInMillis(requireArguments().getLong(MIN_DATE));
            maxDate.setTimeInMillis(requireArguments().getLong(MAX_DATE));
            final Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(requireArguments().getLong(DATE));
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, monthOfYear);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            if (minDate.getTimeInMillis() > calendar.getTimeInMillis())
                runCallback(requireArguments().getInt(ID), false, minDate);
            else if (maxDate.getTimeInMillis() < calendar.getTimeInMillis())
                runCallback(requireArguments().getInt(ID), false, maxDate);
            else
                runCallback(requireArguments().getInt(ID), false, calendar);
        }

        private void runCallback(final int ID, boolean canceled, Calendar date) {
            boolean handled = false;
            if (getParentFragment() != null && getParentFragment() instanceof Callback)
                handled = ((Callback) getParentFragment()).onAction(ID, canceled, date);
            if (!handled && getActivity() instanceof Callback)
                ((Callback) getActivity()).onAction(ID, canceled, date);
        }

        public void onCancel(DialogInterface dialog) {
            final Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(requireArguments().getLong(DATE));
            runCallback(requireArguments().getInt(ID), true, calendar);
            super.onCancel(dialog);
        }

        public interface Callback {
            boolean onAction(final int ID, boolean canceled, Calendar date);
        }


        private static class DatePickerDialogCompatHelper {
            final static DatePickerDialogCompatImplBase IMPL;

            static {
                final int version = Build.VERSION.SDK_INT;
                if (version >= Build.VERSION_CODES.HONEYCOMB) {
                    IMPL = new DatePickerDialogCompatImplHC();
                } else {
                    IMPL = new DatePickerDialogCompatImplBase();
                }
            }

            static void setMaxDate(DatePickerDialog dialog, long maxDate) {
                IMPL.setMaxDate(dialog, maxDate);
            }

            static void setMinDate(DatePickerDialog dialog, long minDate) {
                IMPL.setMinDate(dialog, minDate);
            }

            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            static class DatePickerDialogCompatImplHC extends DatePickerDialogCompatImplBase {
                @Override
                public void setMaxDate(DatePickerDialog dialog, long maxDate) {
                    dialog.getDatePicker().setCalendarViewShown(false);
                    dialog.getDatePicker().setMaxDate(maxDate);
                }

                @Override
                public void setMinDate(DatePickerDialog dialog, long minDate) {
                    dialog.getDatePicker().setCalendarViewShown(false);
                    dialog.getDatePicker().setMinDate(minDate);
                }
            }

            static class DatePickerDialogCompatImplBase {
                public void setMaxDate(DatePickerDialog dialog, long maxDate) {
                }

                public void setMinDate(DatePickerDialog dialog, long minDate) {
                }
            }
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

        public void onCancel(DialogInterface dialog) {
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
