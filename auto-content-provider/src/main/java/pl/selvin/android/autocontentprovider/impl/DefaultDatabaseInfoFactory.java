/*
 Copyright (c) 2017 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */
package pl.selvin.android.autocontentprovider.impl;

import android.content.UriMatcher;

import java.lang.reflect.Field;

import pl.selvin.android.autocontentprovider.annotation.Column;
import pl.selvin.android.autocontentprovider.annotation.Table;
import pl.selvin.android.autocontentprovider.db.ColumnInfo;
import pl.selvin.android.autocontentprovider.db.ColumnInfoFactory;
import pl.selvin.android.autocontentprovider.db.DatabaseInfo;
import pl.selvin.android.autocontentprovider.db.DatabaseInfoFactory;
import pl.selvin.android.autocontentprovider.db.TableInfo;
import pl.selvin.android.autocontentprovider.db.TableInfoFactory;

public class DefaultDatabaseInfoFactory implements DatabaseInfoFactory {
    private final ColumnInfoFactory columnInfoFactory = new ColumnInfoFactory() {
        @Override
        public ColumnInfo createColumnInfo(Column column, Field field) throws Exception {
            return new ColumnInfo((String) field.get(null), column);
        }
    };
    private final TableInfoFactory tableInfoFactory = new TableInfoFactory() {
        @Override
        public TableInfo createTableInfo(Table table, Class<?> tableClass, String authority) throws Exception {
            return new TableInfo(table, tableClass, authority, "%s", columnInfoFactory);
        }
    };

    @Override
    public DatabaseInfo createDatabaseInfo(Class<?> dbClass, String authority, UriMatcher matcher) throws Exception {
        return new DatabaseInfo(dbClass, authority, matcher, tableInfoFactory);
    }
}
