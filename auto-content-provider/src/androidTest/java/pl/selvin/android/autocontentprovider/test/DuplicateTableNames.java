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

/**
 * Two tables sharing one @TableName. Table names are the identity a table is looked up by,
 * so a ContentHelper built over this class has to fail rather than silently keep whichever
 * one it saw last.
 */
public class DuplicateTableNames {

	static final String SHARED_NAME = "Dup";

	@Table(primaryKeys = {First.ID})
	public interface First {

		@TableName
		String TABLE_NAME = SHARED_NAME;

		@Column
		String ID = "ID";
	}

	@Table(primaryKeys = {Second.ID})
	public interface Second {

		@TableName
		String TABLE_NAME = SHARED_NAME;

		@Column
		String ID = "ID";
	}
}
