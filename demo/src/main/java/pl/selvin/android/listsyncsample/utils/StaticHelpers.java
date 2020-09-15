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

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.core.content.res.ResourcesCompat;

import pl.selvin.android.listsyncsample.R;


public class StaticHelpers {
    public static Bundle cursorRowToBundle(final Cursor cursor) {
        if (cursor != null) {
            final Bundle ret = new Bundle();
            final int c = cursor.getColumnCount();
            final String[] columns = cursor.getColumnNames();
            for (int i = 0; i < c; i++) {
                ret.putString(columns[i], cursor.getString(i));
            }
            return ret;
        }
        return null;
    }

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences("OLSS_PREFS", 0);
    }

    public static void brandGlowEffect(Context context) {
        int brandColor = ResourcesCompat.getColor(context.getResources(), R.color.colorPrimary, context.getTheme());

        int glowDrawableId = context.getResources().getIdentifier("overscroll_glow", "drawable", "android");
        final Drawable androidGlow = ResourcesCompat.getDrawable(context.getResources(), glowDrawableId, context.getTheme());
        if (androidGlow != null)
            androidGlow.setColorFilter(brandColor, PorterDuff.Mode.SRC_ATOP);

        int edgeDrawableId = context.getResources().getIdentifier("overscroll_edge", "drawable", "android");
        final Drawable androidEdge = ResourcesCompat.getDrawable(context.getResources(), edgeDrawableId, context.getTheme());
        if (androidEdge != null)
            androidEdge.setColorFilter(brandColor, PorterDuff.Mode.SRC_ATOP);
    }
}
