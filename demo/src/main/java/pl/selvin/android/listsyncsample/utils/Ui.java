/*
 * Copyright (C) 2011 The Android Open Source Project
 * Modifications copyright (C) 2014-2016 Selvin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pl.selvin.android.listsyncsample.utils;

import android.app.Activity;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.View;

@SuppressWarnings("unused")
public class Ui {

    @Nullable
    public static <T extends View> T getViewOrNull(Activity parent, @IdRes int viewId) {
        return parent.findViewById(viewId);
    }

    @Nullable
    public static <T extends View> T getViewOrNull(View parent, @IdRes int viewId) {
        return parent.findViewById(viewId);
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public static <T extends View> T getView(Activity parent, @IdRes int viewId) {
        return (T) checkView(parent.findViewById(viewId));
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public static <T extends View> T getView(View parent, @IdRes int viewId) {
        return (T) checkView(parent.findViewById(viewId));
    }

    private static View checkView(View v) {
        if (v == null) {
            throw new IllegalArgumentException("View doesn't exist");
        }
        return v;
    }

    public static boolean isViewExists(Activity parent, @IdRes int viewId) {
        return parent.findViewById(viewId) != null;
    }

    public static boolean isViewExists(View parent, int viewId) {
        return parent.findViewById(viewId) != null;
    }

    public static void setVisibilitySafe(View v, int visibility) {
        if (v != null) {
            v.setVisibility(visibility);
        }
    }

    public static void setVisibilitySafe(Activity parent, @IdRes int viewId, int visibility) {
        setVisibilitySafe(parent.findViewById(viewId), visibility);
    }

    public static void setVisibilitySafe(View parent, @IdRes int viewId, int visibility) {
        setVisibilitySafe(parent.findViewById(viewId), visibility);
    }
}
