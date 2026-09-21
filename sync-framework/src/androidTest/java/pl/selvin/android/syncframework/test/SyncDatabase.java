/*
 Copyright (c) 2026 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */
package pl.selvin.android.syncframework.test;

import pl.selvin.android.autocontentprovider.annotation.Column;
import pl.selvin.android.autocontentprovider.annotation.Table;
import pl.selvin.android.autocontentprovider.annotation.TableName;
import pl.selvin.android.autocontentprovider.db.ColumnType;
import pl.selvin.android.syncframework.annotation.SyncScope;

/**
 * Test schema for the sync protocol. Item carries one column of each type that SyncJSON
 * converts; Other exists only so scope handling has a second scope to get wrong.
 */
public class SyncDatabase {

	public static final String SCOPE = "TestScope";
	public static final String OTHER_SCOPE = "OtherScope";

	@SyncScope(SCOPE)
	@Table(primaryKeys = {Item.ID})
	public interface Item {

		/** As it appears in __metadata.type: "<scope>.<table>". */
		String TYPE = SCOPE + "." + Item.TABLE_NAME;

		@TableName
		String TABLE_NAME = "Item";

		@Column(type = ColumnType.GUID)
		String ID = "ID";

		@Column(type = ColumnType.VARCHAR, nullable = true)
		String NAME = "Name";

		@Column(nullable = true)
		String QUANTITY = "Quantity";

		@Column(type = ColumnType.DATETIME, nullable = true)
		String CREATED = "Created";

		@Column(type = ColumnType.BOOLEAN, nullable = true)
		String DONE = "Done";

		@Column(type = ColumnType.DECIMAL, nullable = true)
		String PRICE = "Price";
	}

	@SyncScope(OTHER_SCOPE)
	@Table(primaryKeys = {Other.ID})
	public interface Other {

		String TYPE = OTHER_SCOPE + "." + Other.TABLE_NAME;

		@TableName
		String TABLE_NAME = "Other";

		@Column(type = ColumnType.GUID)
		String ID = "ID";

		@Column(type = ColumnType.VARCHAR, nullable = true)
		String NAME = "Name";
	}
}
