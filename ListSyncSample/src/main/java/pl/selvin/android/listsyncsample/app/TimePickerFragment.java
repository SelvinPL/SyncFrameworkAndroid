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


import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.widget.TimePicker;

import java.util.Calendar;

public class TimePickerFragment extends DialogFragment implements TimePickerDialog.OnTimeSetListener {

    private static final String CalendarArg = "CalendarArg";
    private static final String UserDataArg = "UserDataArg";
    private OnTimeSetListener listener;

    public static TimePickerFragment newInstance(Calendar date, Bundle userData) {
        TimePickerFragment f = new TimePickerFragment();
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
        return new TimePickerDialog(getActivity(), this,
                calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true);
    }

    public void setOnTimeSetListener(OnTimeSetListener listener) {
        this.listener = listener;
    }


    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        final Calendar calendar = getCalendar();
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (listener != null)
            listener.setTime(calendar, getUserData());
    }

    public static interface OnTimeSetListener {
        void setTime(Calendar calendar, Bundle userData);
    }
}
