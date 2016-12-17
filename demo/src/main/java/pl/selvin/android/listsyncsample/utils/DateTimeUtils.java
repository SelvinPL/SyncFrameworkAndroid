/***
 Copyright (c) 2014-2016 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */

package pl.selvin.android.listsyncsample.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class DateTimeUtils {
    public static Calendar zeroTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private final static SimpleDateFormat longFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private final static SimpleDateFormat shortFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final static SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());


    public static int getDateAsMonths(Calendar date) {
        return date.get(Calendar.YEAR) * 12 + date.get(Calendar.MONTH);
    }

    public static Calendar getToday() {
        return zeroTime(Calendar.getInstance());
    }

    public static String toLong(Calendar calendar) {
        synchronized (longFormat) {
            return longFormat.format(calendar.getTime());
        }
    }

    public static String getNowLong() {
        return toLong(Calendar.getInstance());
    }

    public static String getTodayLong() {
        return toLong(getToday());
    }


    public static String toShort(Calendar calendar) {
        synchronized (shortFormat) {
            return shortFormat.format(calendar.getTime());
        }
    }

    public static String toTime(Calendar calendar) {
        synchronized (timeFormat) {
            return timeFormat.format(calendar.getTime());
        }
    }


    public static Calendar fromLong(String date) throws ParseException {
        if(date == null)
            return Calendar.getInstance();
        synchronized (longFormat) {
            final Calendar cal = Calendar.getInstance();
            cal.setTime(longFormat.parse(date));
            return cal;
        }
    }

    public static Calendar fromShort(String date) throws ParseException {
        if(date == null)
            return Calendar.getInstance();
        synchronized (shortFormat) {
            final Calendar cal = Calendar.getInstance();
            cal.setTime(shortFormat.parse(date));
            return cal;
        }
    }
}
