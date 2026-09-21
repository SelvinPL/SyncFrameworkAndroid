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
package pl.selvin.android.autocontentprovider.test;

import pl.selvin.android.autocontentprovider.annotation.Column;
import pl.selvin.android.autocontentprovider.annotation.Table;
import pl.selvin.android.autocontentprovider.annotation.TableName;
import pl.selvin.android.autocontentprovider.db.ColumnType;

/**
 * Malformed table definitions used to pin the validation performed by
 * {@link pl.selvin.android.autocontentprovider.db.TableInfo}'s constructor.
 * <p>
 * Deliberately NOT nested in {@link DatabaseTest}: these would abort construction of
 * {@link TestProvider}'s ContentHelper and take every other test down with it. Tests
 * build a TableInfo from them directly instead.
 */
public class InvalidTables {

	@Table(primaryKeys = {NoColumns.ID})
	public interface NoColumns {

		@TableName
		String TABLE_NAME = "NoColumns";

		/** Not annotated with @Column, so the table ends up with no columns at all. */
		String ID = "ID";
	}

	@Table(primaryKeys = {NoTableName.ID})
	public interface NoTableName {

		@Column
		String ID = "ID";
	}

	@Table(primaryKeys = {TwoTableNames.ID})
	public interface TwoTableNames {

		@TableName
		String TABLE_NAME = "TwoTableNames";

		@TableName
		String OTHER_TABLE_NAME = "TwoTableNamesAgain";

		@Column
		String ID = "ID";
	}

	/**
	 * primaryKeys names a column that does not exist on the table - a plain typo in a
	 * schema. See TableInfoTest#primaryKeyNamingUnknownColumn.
	 */
	@Table(primaryKeys = {UnknownPrimaryKey.MISSPELLED_ID})
	public interface UnknownPrimaryKey {

		@TableName
		String TABLE_NAME = "UnknownPrimaryKey";

		String MISSPELLED_ID = "Idd";

		@Column
		String ID = "Id";

		@Column(type = ColumnType.VARCHAR)
		String NAME = "Name";
	}
}
