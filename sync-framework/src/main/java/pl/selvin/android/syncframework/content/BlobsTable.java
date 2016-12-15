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

package pl.selvin.android.syncframework.content;

final public class BlobsTable {
    //why such name? ... to make sure that Your table will not have the same name as blob table
    public static final String NAME = "pl_selvin_android_syncframework_blobs";
    public static final String C_NAME = "name";
    public static final String C_VALUE = "value";
    public static final String C_DATE = "date";
    public static final String C_STATE = "state";
}
