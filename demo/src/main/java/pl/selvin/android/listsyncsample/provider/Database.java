/*
 Copyright (c) 2014-2018 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */

package pl.selvin.android.listsyncsample.provider;

import pl.selvin.android.autocontentprovider.annotation.Cascade;
import pl.selvin.android.autocontentprovider.annotation.Column;
import pl.selvin.android.autocontentprovider.annotation.Index;
import pl.selvin.android.autocontentprovider.annotation.IndexColumn;
import pl.selvin.android.autocontentprovider.annotation.Table;
import pl.selvin.android.autocontentprovider.annotation.TableName;
import pl.selvin.android.autocontentprovider.db.ColumnType;
import pl.selvin.android.listsyncsample.provider.implementation.TagItemMappingWithNamesProvider;
import pl.selvin.android.listsyncsample.provider.implementation.TagNotUsedProvider;
import pl.selvin.android.syncframework.annotation.SyncScope;

@SuppressWarnings("unused")
public interface Database {

	String DS = "DefaultScope";

	@SyncScope(DS)
	@Table(primaryKeys = {Status.ID}, readonly = true)
	interface Status {
		String SCOPE = DS;

		@TableName
		String TABLE_NAME = "Status";

		@Column
		String ID = "ID";

		@Column(type = ColumnType.VARCHAR, extras = Column.COLLATE_NO_CASE)
		String NAME = "Name";
	}

	@SyncScope(DS)
	@Table(primaryKeys = {Tag.ID}, readonly = true, notifyUris = {TagNotUsedProvider.Uri})
	interface Tag {
		String SCOPE = DS;

		@TableName
		String TABLE_NAME = "Tag";

		@Column
		String ID = "ID";

		@Column(type = ColumnType.VARCHAR, extras = Column.COLLATE_NO_CASE)
		String NAME = "Name";
	}

	@SyncScope(DS)
	@Table(primaryKeys = {Priority.ID}, readonly = true)
	interface Priority {
		String SCOPE = DS;

		@TableName
		String TABLE_NAME = "Priority";

		@Column
		String ID = "ID";

		@Column(type = ColumnType.VARCHAR, extras = Column.COLLATE_NO_CASE)
		String NAME = "Name";
	}

	@SyncScope(DS)
	@Table(primaryKeys = {User.ID}, readonly = true)
	interface User {
		String SCOPE = DS;

		@TableName
		String TABLE_NAME = "User";

		@Column(type = ColumnType.GUID)
		String ID = "ID";

		@Column(type = ColumnType.VARCHAR, extras = Column.COLLATE_NO_CASE)
		String NAME = "Name";
	}

	@SyncScope(DS)
	@Table(primaryKeys = {List.ID}, delete = {@Cascade(table = Item.TABLE_NAME, pk = {
			List.ID, List.USER_ID}, fk = {Item.LIST_ID, Item.USER_ID})}, indexes = {
			@Index(name = "IX_" + List.TABLE_NAME + "_" + List.NAME, columns = {@IndexColumn(name = List.NAME, order = IndexColumn.ASC)})})
	interface List {
		String SCOPE = DS;

		@TableName
		String TABLE_NAME = "List";

		@Column(type = ColumnType.GUID)
		String ID = "ID";

		@Column(type = ColumnType.VARCHAR, extras = Column.COLLATE_NO_CASE)
		String NAME = "Name";

		@Column(type = ColumnType.VARCHAR, extras = Column.COLLATE_NO_CASE, nullable = true)
		String DESCRIPTION = "Description";

		@Column(type = ColumnType.GUID)
		String USER_ID = "UserID";

		@Column(type = ColumnType.DATETIME)
		String CREATED_DATE = "CreatedDate";

		// this is the only sneaky part computed ... but well see @Column doc :)
		@Column(type = ColumnType.VARCHAR, computed = List.NAME + " || ' ' || " + List.DESCRIPTION)
		String NAME_DESCRIPTION = "N_D";

		// this is the only sneaky part computed ... but well see @Column doc :)
		@Column(type = ColumnType.VARCHAR, computed = List.NAME + " || ' ' || " + List.DESCRIPTION + " || ' ' || " + List.CREATED_DATE)
		String NAME_DESCRIPTION_CREATE = "N_D_C";
	}

	@SyncScope(DS)
	@Table(primaryKeys = {Item.ID}, delete = {@Cascade(table = TagItemMapping.TABLE_NAME, pk = {
			Item.ID, Item.USER_ID}, fk = {TagItemMapping.ITEM_ID,
			TagItemMapping.USER_ID})})
	interface Item {
		String SCOPE = DS;

		@TableName
		String TABLE_NAME = "Item";

		@Column(type = ColumnType.GUID)
		String ID = "ID";

		@Column(type = ColumnType.GUID)
		String LIST_ID = "ListID";

		@Column(type = ColumnType.GUID)
		String USER_ID = "UserID";

		@Column(type = ColumnType.VARCHAR, extras = Column.COLLATE_NO_CASE)
		String NAME = "Name";

		@Column(type = ColumnType.VARCHAR, extras = Column.COLLATE_NO_CASE, nullable = true)
		String DESCRIPTION = "Description";

		@Column(nullable = true)
		String PRIORITY = "Priority";

		@Column(nullable = true)
		String STATUS = "Status";

		@Column(type = ColumnType.DATETIME, nullable = true)
		String START_DATE = "StartDate";

		@Column(type = ColumnType.DATETIME, nullable = true)
		String END_DATE = "EndDate";
	}

	@SyncScope(DS)
	@Table(primaryKeys = {TagItemMapping.TAG_ID, TagItemMapping.ITEM_ID,
			TagItemMapping.USER_ID}, notifyUris = {TagItemMappingWithNamesProvider.Uri,
			TagNotUsedProvider.Uri})
	interface TagItemMapping {
		String SCOPE = DS;

		@TableName
		String TABLE_NAME = "TagItemMapping";

		@Column
		String TAG_ID = "TagID";

		@Column(type = ColumnType.GUID)
		String ITEM_ID = "ItemID";

		@Column(type = ColumnType.GUID)
		String USER_ID = "UserID";
	}
}