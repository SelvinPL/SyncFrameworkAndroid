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

package pl.selvin.android.listsyncsample.app;


import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.widget.DatePicker;

import java.util.Calendar;

public class DatePickerFragment extends DialogFragment implements DatePickerDialog.OnDateSetListener {

    private static final String CalendarArg = "CalendarArg";
    private static final String UserDataArg = "UserDataArg";
    private OnDateSetListener listener;

    public static DatePickerFragment newInstance(Calendar date, Bundle userData) {
        DatePickerFragment f = new DatePickerFragment();
        Bundle args = new Bundle();
        args.putBundle(UserDataArg, userData);
        args.putSerializable(CalendarArg, date);
        f.setArguments(args);
        return f;
    }

    public Calendar getCalendar() {
        return (Calendar) getArguments().getSerializable(CalendarArg);
    }

    public Bundle getUserData() {
        return getArguments().getBundle(UserDataArg);
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Calendar calendar = getCalendar();
        return new DatePickerDialog(getActivity(), this,
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
    }

    public void setOnDateSetListener(OnDateSetListener listener) {
        this.listener = listener;
    }


    @Override
    public void onDateSet(DatePicker view, int year, int monthOfYear,
                          int dayOfMonth) {
        final Calendar calendar = getCalendar();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, monthOfYear);
        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        if (listener != null)
            listener.setDate(calendar, getUserData());
    }

    public static interface OnDateSetListener {
        void setDate(Calendar calendar, Bundle userData);
    }
}
