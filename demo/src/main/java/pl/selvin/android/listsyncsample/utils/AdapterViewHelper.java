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

import android.database.Cursor;
import androidx.cursoradapter.widget.CursorAdapter;
import android.widget.AdapterView;

public class AdapterViewHelper {
    public static boolean setSelectionById(final AdapterView spinner, final boolean loaded, long id, final String columnName) {
        return  setSelectionById(spinner, loaded, id, columnName, false);
    }

    public static boolean setSelectionById(final AdapterView spinner, final boolean loaded, long id, final String columnName, boolean setLastIfDidntFind) {
        if (loaded && id != AdapterView.INVALID_ROW_ID) {
            Cursor c = ((CursorAdapter) spinner.getAdapter()).getCursor();
            if (c != null) {
                int pos = 0;
                if (c.moveToFirst()) {
                    final int index = c.getColumnIndex(columnName);
                    do {
                        if (c.getLong(index) == id) {
                            spinner.setSelection(pos);
                            return true;
                        }
                        pos++;
                    } while (c.moveToNext());
                    if(setLastIfDidntFind) {
                        spinner.setSelection(--pos);
                    }
                }
            }
        }
        return false;
    }

    public static boolean setSelectionByValue(final AdapterView spinner, final boolean loaded, String value, final String columnName) {
        return  setSelectionByValue(spinner, loaded, value, columnName, false);
    }
    public static boolean setSelectionByValue(final AdapterView spinner, final boolean loaded, String value, final String columnName, boolean setLastIfDidntFind) {
        if (loaded && value != null) {
            Cursor c = ((CursorAdapter) spinner.getAdapter()).getCursor();
            if (c != null) {
                int pos = 0;
                if (c.moveToFirst()) {
                    value = value.toLowerCase();
                    final int index = c.getColumnIndex(columnName);
                    do {
                        if (c.getString(index).toLowerCase().equals(value)) {
                            spinner.setSelection(pos);
                            return true;
                        }
                        pos++;
                    } while (c.moveToNext());
                    if(setLastIfDidntFind) {
                        spinner.setSelection(--pos);
                    }
                }
            }
        }
        return false;
    }
}
