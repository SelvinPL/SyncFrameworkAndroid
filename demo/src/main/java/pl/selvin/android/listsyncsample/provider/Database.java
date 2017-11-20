/*
 * Copyright (c) 2014-2016 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package pl.selvin.android.listsyncsample.provider;

import pl.selvin.android.autocontentprovider.annotation.Cascade;
import pl.selvin.android.autocontentprovider.annotation.Column;
import pl.selvin.android.autocontentprovider.annotation.Table;
import pl.selvin.android.autocontentprovider.annotation.TableName;
import pl.selvin.android.autocontentprovider.db.ColumnType;
import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.syncframework.annotation.SyncScope;

@SuppressWarnings("unused")
public class Database {

    public static final String DS = "DefaultScope";

    @SyncScope(DS)
    @Table(primaryKeys = {Status.ID}, readonly = true)
    public interface Status {
        String SCOPE = DS;

        @TableName
        String TABLE_NAME = "Status";

        @Column
        String ID = "ID";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE_NO_CASE)
        String NAME = "Name";
    }

    @SyncScope(DS)
    @Table(primaryKeys = {Tag.ID}, readonly = true, notifyUris = {TagItemMapping.TagItemMappingWithNamesUri})
    public interface Tag {
        String SCOPE = DS;

        @TableName
        String TABLE_NAME = "Tag";

        @Column
        String ID = "ID";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE_NO_CASE)
        String NAME = "Name";

        String TagNotUsed = "TagNotUsed";

        String TagNotUsedUri = "content://" + Constants.AUTHORITY + "/" + TagNotUsed;
    }

    @SyncScope(DS)
    @Table(primaryKeys = {Priority.ID}, readonly = true)
    public interface Priority {
        String SCOPE = DS;

        @TableName
        String TABLE_NAME = "Priority";

        @Column
        String ID = "ID";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE_NO_CASE)
        String NAME = "Name";
    }

    @SuppressWarnings("WeakerAccess")
    @SyncScope(DS)
    @Table(primaryKeys = {User.ID}, readonly = true)
    public interface User {
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
            List.ID, List.USER_ID}, fk = {Item.LIST_ID, Item.USER_ID})})
    public interface List {
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
        @Column(type = ColumnType.VARCHAR, computed = List.NAME + " || ' ' || "
                + List.DESCRIPTION)
        String NAME_DESCRIPTION = "N_D";

        // this is the only sneaky part computed ... but well see @Column doc :)
        @Column(type = ColumnType.VARCHAR, computed = List.NAME + " || ' ' || "
                + List.DESCRIPTION + " || ' ' || " + List.CREATED_DATE)
        String NAME_DESCRIPTION_CREATE = "N_D_C";
    }

    @SyncScope(DS)
    @Table(primaryKeys = {Item.ID}, delete = {@Cascade(table = TagItemMapping.TABLE_NAME, pk = {
            Item.ID, Item.USER_ID}, fk = {TagItemMapping.ITEM_ID,
            TagItemMapping.USER_ID})})
    public interface Item {
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
            TagItemMapping.USER_ID}, notifyUris = {TagItemMapping.TagItemMappingWithNamesUri,
            Tag.TagNotUsedUri})
    public interface TagItemMapping {
        String SCOPE = DS;

        @TableName
        String TABLE_NAME = "TagItemMapping";

        @Column
        String TAG_ID = "TagID";

        @Column(type = ColumnType.GUID)
        String ITEM_ID = "ItemID";

        @Column(type = ColumnType.GUID)
        String USER_ID = "UserID";

        String TagItemMappingWithNames = "TagItemMappingWithNames";

        String TagItemMappingWithNamesUri = "content://" + Constants.AUTHORITY + "/" + TagItemMappingWithNames;
    }

}
