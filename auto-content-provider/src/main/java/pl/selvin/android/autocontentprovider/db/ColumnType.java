/*
 Copyright (c) 2014 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */

package pl.selvin.android.autocontentprovider.db;

public class ColumnType {
    public final static int INTEGER = 0;
    public final static int VARCHAR = 1;
    public final static int GUID = 2;
    public final static int DATETIME = 3;
    public final static int NUMERIC = 4;
    public final static int BOOLEAN = 5;
    public final static int BLOB = 6;
    public final static int DECIMAL = 7;
    private final static String[] names = new String[]{"INTEGER", "VARCHAR", "GUID", "DATETIME", "NUMERIC", "BOOLEAN", "BLOB", "DECIMAL"};

    public static String getName(int type) {
        return names[type];
    }
}