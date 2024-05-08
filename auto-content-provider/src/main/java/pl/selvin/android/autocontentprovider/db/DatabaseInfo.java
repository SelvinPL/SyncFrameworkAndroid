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

package pl.selvin.android.autocontentprovider.db;

import android.content.UriMatcher;
import android.util.SparseArray;

import java.util.HashMap;

import pl.selvin.android.autocontentprovider.annotation.Table;
import pl.selvin.android.autocontentprovider.content.ContentHelper;

public class DatabaseInfo {
	public final HashMap<String, TableInfo> allTablesInfo = new HashMap<>();
	public final SparseArray<TableInfo> allTablesInfoCode = new SparseArray<>();

	public DatabaseInfo(Class<?> dbClass, String authority, UriMatcher matcher, TableInfoFactory tableInfoFactory) throws Exception {
		final Class<?>[] tableClasses = dbClass.getClasses();
		int code = 0;

		for (final Class<?> tableClass : tableClasses) {
			final Table table = tableClass.getAnnotation(Table.class);
			if (table != null) {
				code++;
				TableInfo tableToAdd = tableInfoFactory.createTableInfo(table, tableClass, authority);
				matcher.addURI(authority, tableToAdd.name, code);
				matcher.addURI(authority, tableToAdd.name + "/ROWID/#", code | ContentHelper.uriCodeItemRowIDFlag);
				if (!tableToAdd.primaryKeys.isEmpty()) {
					final StringBuilder sig = new StringBuilder(tableToAdd.name);
					for (ColumnInfo pkInfo : tableToAdd.primaryKeys) {
						sig.append('/');
						sig.append(pkInfo.type == ColumnType.INTEGER ? '#' : '*');
					}
					matcher.addURI(authority, sig.toString(), code | ContentHelper.uriCodeItemFlag);
				}
				allTablesInfo.put(tableToAdd.nameForMime, tableToAdd);
				allTablesInfoCode.put(code, tableToAdd);
			}
		}
	}
}