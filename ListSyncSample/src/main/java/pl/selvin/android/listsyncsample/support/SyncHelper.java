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

package pl.selvin.android.listsyncsample.support;

import android.app.Activity;
import android.os.Build;


public abstract class SyncHelper {
    public static SyncHelper createInstance(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            return new SyncHelperFroyo(activity);
        } else {
            return new SyncHelperBase(activity);
        }
    }

    protected Activity mActivity;

    protected SyncHelper(Activity activity) {
        mActivity = activity;
    }

    public abstract void doSync();

    public abstract String getUserId() throws Exception;
}
