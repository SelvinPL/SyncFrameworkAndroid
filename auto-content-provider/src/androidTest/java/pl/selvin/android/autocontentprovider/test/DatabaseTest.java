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
package pl.selvin.android.autocontentprovider.test;

import pl.selvin.android.autocontentprovider.annotation.Column;
import pl.selvin.android.autocontentprovider.annotation.Table;
import pl.selvin.android.autocontentprovider.annotation.TableName;
import pl.selvin.android.autocontentprovider.db.ColumnType;

public class DatabaseTest {
	@Table(primaryKeys = {Status.ID})
	public interface Status {

		@TableName
		String TABLE_NAME = "Status";

		@Column
		String ID = "ID";

		@Column(type = ColumnType.VARCHAR, extras = Column.COLLATE_NO_CASE)
		String NAME = "Name";
	}

	@Table(primaryKeys = {StatusReadonly.ID}, readonly = true)
	public interface StatusReadonly {

		@TableName
		String TABLE_NAME = "StatusReadonly";

		@Column
		String ID = "ID";

		@Column(type = ColumnType.VARCHAR, extras = Column.COLLATE_NO_CASE)
		String NAME = "Name";
	}
}
