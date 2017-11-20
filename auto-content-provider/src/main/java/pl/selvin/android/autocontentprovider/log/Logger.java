/**
 * Copyright (c) 2014 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package pl.selvin.android.autocontentprovider.log;

import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

import java.util.Arrays;

@SuppressWarnings("unused")
public class Logger {

    private final String TAG;

    public Logger(String tag) {
        TAG = tag;
    }


    private String formatTag(Class<?> clazz) {
        return TAG + clazz.getSimpleName();
    }

    public void LogE(Class<?> clazz, String msg) {
        Log.e(formatTag(clazz), msg);
    }

    public void LogE(Class<?> clazz, String msg, Throwable tr) {
        Log.e(formatTag(clazz), msg, tr);
    }

    public void LogE(Class<?> clazz, Throwable tr) {
        Log.e(formatTag(clazz), tr.getMessage(), tr);
    }

    public void LogD(Class<?> clazz, String msg) {
        Log.d(formatTag(clazz), msg);
    }

    public void LogD(Class<?> clazz, String msg, Throwable tr) {
        Log.d(formatTag(clazz), msg, tr);
    }


    public void LogTimeD(Class<?> clazz, String msg, long start) {
        long diffInMillis = System.currentTimeMillis() - start;
        long diffInSeconds = diffInMillis / 1000;
        long diff[] = new long[]{0, 0, 0, 0, 0};
        diff[4] = (diffInMillis >= 1000 ? diffInMillis % 1000 : diffInMillis);
        diff[3] = (diffInSeconds >= 60 ? diffInSeconds % 60 : diffInSeconds);
        diff[2] = (diffInSeconds = (diffInSeconds / 60)) >= 60 ? diffInSeconds % 60
                : diffInSeconds;
        diff[1] = (diffInSeconds = (diffInSeconds / 60)) >= 24 ? diffInSeconds % 24
                : diffInSeconds;
        diff[0] = diffInSeconds / 24;
        Log.d(formatTag(clazz), String.format(
                "%s: %d d, %d h, %d m, %d s, %d m", msg, diff[0], diff[1],
                diff[2], diff[3], diff[4]));
    }

    public void LogQuery(Class<?> clazz, Uri uri, SQLiteQueryBuilder builder, String[] projection,
                         String selection, String[] selectionArgs, String groupBy, String having,
                         String sortOrder, String limit) {
        LogD(clazz, uri + "");
        //noinspection deprecation
        LogD(clazz, builder.buildQuery(projection, selection, null, groupBy, having, sortOrder, limit));
        LogD(clazz, Arrays.toString(selectionArgs));
    }


    public static final class EmptyLogger extends Logger {

        public final static Logger INSTANCE = new EmptyLogger();

        EmptyLogger() {
            super(null);
        }
        public void LogE(Class<?> clazz, String msg) {}
        public void LogE(Class<?> clazz, String msg, Throwable tr) {}
        public void LogE(Class<?> clazz, Throwable tr) {}
        public void LogD(Class<?> clazz, String msg) {}
        public void LogD(Class<?> clazz, String msg, Throwable tr) {}
        public void LogTimeD(Class<?> clazz, String msg, long start) {}
        public void LogQuery(Class<?> clazz, Uri uri, SQLiteQueryBuilder builder, String[] projection,
                             String selection, String[] selectionArgs, String groupBy, String having,
                             String sortOrder, String limit) {}
    }
}
