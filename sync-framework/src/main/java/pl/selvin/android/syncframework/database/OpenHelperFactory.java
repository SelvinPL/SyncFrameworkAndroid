/***
 * Copyright (c) 2014-2017 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package pl.selvin.android.syncframework.database;

import pl.selvin.android.syncframework.content.BaseContentProvider;
import pl.selvin.android.syncframework.content.ContentHelper;

public class OpenHelperFactory {
    public static ISQLiteOpenHelper getSQLiteOpenHelper(BaseContentProvider provider, ContentHelper contentHelper) {
        try {
            //noinspection ConstantConditions
            if (Class.forName("net.sqlcipher.SQLException", false, provider.getContext().getClassLoader()) != null)
                return new pl.selvin.android.syncframework.database.sqlcipher.OpenHelper(provider, contentHelper);
        } catch (Exception ignore) {
        }
        return new pl.selvin.android.syncframework.database.android.OpenHelper(provider, contentHelper);
    }
}
