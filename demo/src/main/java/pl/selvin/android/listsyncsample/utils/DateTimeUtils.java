/*
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import pl.selvin.android.listsyncsample.compat.ThreadLocalExt;

public class DateTimeUtils {
	private final static ThreadLocal<SimpleDateFormat> SIMPLE_DATE_FORMAT =
			ThreadLocalExt.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()));
	private final static ThreadLocal<SimpleDateFormat> SIMPLE_DATE_FORMAT_DATE_ONLY =
			ThreadLocalExt.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()));
	private final static ThreadLocal<SimpleDateFormat> SIMPLE_TIME_ONLY =
			ThreadLocalExt.withInitial(() -> new SimpleDateFormat("HH:mm", Locale.getDefault()));

	public static Calendar zeroTime(Calendar calendar) {
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		return calendar;
	}

	public static Calendar addYears(@NonNull final Calendar start, int years) {
		final Calendar ret = (Calendar) start.clone();
		ret.add(Calendar.YEAR, years);
		return ret;
	}

	public static Calendar today() {
		return zeroTime(Calendar.getInstance());
	}

	private static String toString(Calendar date, SimpleDateFormat format) {
		if (date != null) {
			return Objects.requireNonNull(format).format(date.getTime());
		}
		return null;
	}

	public static String toLong(Calendar date) {
		return toString(date, SIMPLE_DATE_FORMAT.get());
	}

	public static String getNowLong() {
		return toLong(Calendar.getInstance());
	}

	public static String toShort(Calendar date) {
		return toString(date, SIMPLE_DATE_FORMAT_DATE_ONLY.get());
	}

	public static String toTime(Calendar date) {
		return toString(date, SIMPLE_TIME_ONLY.get());
	}

	private static Calendar fromString(String string, @Nullable SimpleDateFormat format) throws ParseException {
		if (string != null) {
			final Calendar ret = Calendar.getInstance();
			final Date date = Objects.requireNonNull(format).parse(string);
			if (date != null)
				ret.setTime(date);
			return ret;
		}
		return null;
	}

	public static Calendar fromLong(String string) throws ParseException {
		return fromString(string, SIMPLE_DATE_FORMAT.get());
	}
}
