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

package pl.selvin.android.syncframework.content;

import android.util.Log;

import java.util.Date;

final class Helpers {

    final static void LogInfo(final Date start) {
        Date end = new Date();
        long diffInMili = (end.getTime() - start.getTime());
        long diffInSeconds = diffInMili / 1000;
        long diff[] = new long[]{0, 0, 0, 0, 0};
        diff[4] = (diffInMili >= 1000 ? diffInMili % 1000 : diffInMili);
        diff[3] = (diffInSeconds >= 60 ? diffInSeconds % 60 : diffInSeconds);
        diff[2] = (diffInSeconds = (diffInSeconds / 60)) >= 60 ? diffInSeconds % 60
                : diffInSeconds;
        diff[1] = (diffInSeconds = (diffInSeconds / 60)) >= 24 ? diffInSeconds % 24
                : diffInSeconds;
        diff[0] = (diffInSeconds = (diffInSeconds / 24));
        Log.d(BaseContentProvider.TAG, String.format(
                "CP-Sync: %d d, %d h, %d m, %d s, %d m", diff[0], diff[1],
                diff[2], diff[3], diff[4]));
    }

    final static String formatAsException(String msg) {
        StackTraceElement st = new Exception().getStackTrace()[1];
        return String.format("at %s (%s:%d)", msg, st.getFileName(), st.getLineNumber());
    }
}
