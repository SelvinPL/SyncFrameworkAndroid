/*
 * Copyright (c) 2014-2018 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package pl.selvin.android.autocontentprovider.db;

import android.text.TextUtils;

import pl.selvin.android.autocontentprovider.annotation.Index;
import pl.selvin.android.autocontentprovider.annotation.IndexColumn;

public final class IndexInfo {
	public final String name;
	public final String tableName;
	public final boolean ifNotExists;
	public final boolean isUnique;
	public final String where;
	private final IndexColumnInfo[] columns;

	public IndexInfo(String tableName, final Index index) {
		name = index.name();
		this.tableName = tableName;
		ifNotExists = index.ifNotExists();
		isUnique = index.isUnique();
		where = index.where();
		IndexColumn[] columnsAttr = index.columns();
		columns = new IndexColumnInfo[columnsAttr.length];
		for (int i = 0; i < columnsAttr.length; i++) {
			columns[i] = new IndexColumnInfo(columnsAttr[i]);
		}
	}

	public String createStatement() {
		StringBuilder sb = new StringBuilder("CREATE INDEX ");
		if (ifNotExists)
			sb.append("IF NOT EXISTS ");
		sb.append('[');
		sb.append(name);
		sb.append("] ON [");
		sb.append(tableName);
		sb.append("] (");
		boolean first = true;
		for (final IndexColumnInfo column : columns) {
			if (!first)
				sb.append(", ");
			else
				first = false;
			sb.append("[");
			sb.append(column.name);
			sb.append("] ");
			sb.append(column.collate);
			sb.append(' ');
			sb.append(column.order);
		}
		sb.append(')');
		if (where != null && !TextUtils.isEmpty(where)) {
			sb.append(" WHERE ");
			sb.append(where);
		}
		return sb.toString();
	}
}