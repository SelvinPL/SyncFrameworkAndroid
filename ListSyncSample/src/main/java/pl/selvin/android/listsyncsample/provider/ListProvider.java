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

package pl.selvin.android.listsyncsample.provider;

import android.database.Cursor;
import android.net.Uri;

import pl.selvin.android.listsyncsample.Setup;
import pl.selvin.android.syncframework.content.BaseContentProvider;
import pl.selvin.android.syncframework.content.ContentHelper;

public class ListProvider extends BaseContentProvider {
    private final static ContentHelper helperInstance = ContentHelper.getInstance(Setup.class);

    public static final synchronized ContentHelper getHelper() {
        return helperInstance;
    }

    public ListProvider() {
        super(getHelper());
    }
    // we don't need implementation ... base class do everything on it's own
    // this is just class which we are pointing in xml files as Provider
    // since one day i'll move BaseContentProvider to library

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        try {
            //delay for testing
            //Thread.sleep(2000);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return super
                .query(uri, projection, selection, selectionArgs, sortOrder);
    }
}
