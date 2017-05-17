/*
 * Copyright (c) 2014-2017 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package pl.selvin.android.syncframework.content;

import android.content.Context;

import pl.selvin.android.syncframework.database.ISQLiteDatabase;

public interface IBaseContentProvider {
    Context getContext();

    void onCreateDataBase(ISQLiteDatabase db);

    void onUpgradeDatabase(ISQLiteDatabase db, int oldVersion, int newVersion);

    void onDowngradeDatabase(ISQLiteDatabase db, int oldVersion, int newVersion);

    String getDatabasePassword();
}
