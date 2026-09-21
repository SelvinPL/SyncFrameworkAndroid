/*
 Copyright (c) 2017-2026 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */
package pl.selvin.android.autocontentprovider.test;

import pl.selvin.android.autocontentprovider.annotation.Cascade;
import pl.selvin.android.autocontentprovider.annotation.Column;
import pl.selvin.android.autocontentprovider.annotation.Index;
import pl.selvin.android.autocontentprovider.annotation.IndexColumn;
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

	/**
	 * Head of a cascade chain: Parent -&gt; Child -&gt; GrandChild, plus a second, independent
	 * cascade Parent -&gt; Note.
	 * <p>
	 * The Child cascade matches on two columns, as real schemas do (demo's List -&gt; Item
	 * keys on {ID, USER_ID}), and having two @Cascade entries on one table mirrors
	 * MobileTrader's Zamowienia, which cascades to both ZamowieniaElementy and Tagi.
	 * Note that cascade pk columns need not be the table's primary key.
	 * <p>
	 * Child cascades on INTEGER columns and Note on a GUID one: cascade always binds
	 * Cursor.getString values, so both column affinities are worth covering - demo keys
	 * every cascade it has on GUIDs.
	 */
	@Table(primaryKeys = {Parent.ID},
			delete = {
					@Cascade(table = Child.TABLE_NAME, pk = {Parent.ID, Parent.TENANT},
							fk = {Child.PARENT_ID, Child.TENANT}),
					@Cascade(table = Note.TABLE_NAME, pk = {Parent.CODE}, fk = {Note.PARENT_CODE})})
	public interface Parent {

		@TableName
		String TABLE_NAME = "Parent";

		@Column
		String ID = "ID";

		@Column
		String TENANT = "Tenant";

		@Column(type = ColumnType.GUID)
		String CODE = "Code";

		@Column(type = ColumnType.VARCHAR)
		String NAME = "Name";
	}

	@Table(primaryKeys = {Child.ID},
			delete = {@Cascade(table = GrandChild.TABLE_NAME, pk = {Child.ID}, fk = {GrandChild.CHILD_ID})})
	public interface Child {

		@TableName
		String TABLE_NAME = "Child";

		@Column
		String ID = "ID";

		@Column
		String PARENT_ID = "ParentID";

		@Column
		String TENANT = "Tenant";
	}

	@Table(primaryKeys = {GrandChild.ID})
	public interface GrandChild {

		@TableName
		String TABLE_NAME = "GrandChild";

		@Column
		String ID = "ID";

		@Column
		String CHILD_ID = "ChildID";
	}

	/** Second cascade target of Parent, keyed on a GUID. No cascade of its own. */
	@Table(primaryKeys = {Note.ID})
	public interface Note {

		@TableName
		String TABLE_NAME = "Note";

		@Column
		String ID = "ID";

		@Column(type = ColumnType.GUID)
		String PARENT_CODE = "ParentCode";
	}

	/**
	 * Composite primary key mixing INTEGER and VARCHAR, so the generated UriMatcher
	 * signature is a mix of {@code #} and {@code *} segments.
	 * <p>
	 * rowIdAlias points at a single column of that composite key - the shape real
	 * schemas use, where the remaining key column is a tenant/agent discriminator.
	 * A ROWID item Uri therefore selects on GroupID alone and can match more than one row.
	 */
	@Table(primaryKeys = {Composite.GROUP_ID, Composite.CODE, Composite.SEQ},
			rowIdAlias = Composite.GROUP_ID)
	public interface Composite {

		@TableName
		String TABLE_NAME = "Composite";

		@Column
		String GROUP_ID = "GroupID";

		@Column(type = ColumnType.VARCHAR)
		String CODE = "Code";

		@Column
		String SEQ = "Seq";

		@Column(type = ColumnType.VARCHAR, nullable = true)
		String NOTE = "Note";
	}

	/**
	 * One column of every {@link ColumnType}, plus a computed column, notifyUris
	 * and two indexes. Keeps the default rowIdAlias.
	 */
	@Table(primaryKeys = {AllTypes.ID},
			notifyUris = {AllTypes.NOTIFY_URI},
			indexes = {
					@Index(name = "IX_" + AllTypes.TABLE_NAME + "_" + AllTypes.NAME,
							columns = {@IndexColumn(name = AllTypes.NAME, order = IndexColumn.ASC,
									collate = Column.COLLATE_NO_CASE)}),
					@Index(name = "UX_" + AllTypes.TABLE_NAME + "_" + AllTypes.GUID, isUnique = true,
							ifNotExists = true, where = AllTypes.NUM + " IS NOT NULL",
							columns = {@IndexColumn(name = AllTypes.GUID),
									@IndexColumn(name = AllTypes.NUM, order = IndexColumn.DESC)})})
	public interface AllTypes {

		@TableName
		String TABLE_NAME = "AllTypes";

		String NOTIFY_URI = "content://" + TestProvider.AUTHORITY + "/notify_all_types";

		@Column
		String ID = "ID";

		@Column(type = ColumnType.VARCHAR, extras = Column.COLLATE_NO_CASE)
		String NAME = "Name";

		@Column(type = ColumnType.GUID)
		String GUID = "Guid";

		@Column(type = ColumnType.DATETIME, nullable = true)
		String DATE = "Date";

		@Column(type = ColumnType.NUMERIC, nullable = true)
		String NUM = "Num";

		@Column(type = ColumnType.BOOLEAN)
		String FLAG = "Flag";

		@Column(type = ColumnType.BLOB, nullable = true)
		String DATA = "Data";

		@Column(type = ColumnType.DECIMAL, nullable = true)
		String AMOUNT = "Amount";

		@Column(type = ColumnType.VARCHAR, computed = AllTypes.NAME + " || '-' || " + AllTypes.GUID)
		String LABEL = "Label";
	}
}
