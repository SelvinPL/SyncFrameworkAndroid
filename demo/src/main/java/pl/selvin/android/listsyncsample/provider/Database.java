/***
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

import pl.selvin.android.listsyncsample.Constants;
import pl.selvin.android.syncframework.ColumnType;
import pl.selvin.android.syncframework.annotation.Cascade;
import pl.selvin.android.syncframework.annotation.Column;
import pl.selvin.android.syncframework.annotation.Table;
import pl.selvin.android.syncframework.annotation.TableName;

@SuppressWarnings("unused")
public class Database {

    static final String DS = "DefaultScope";

    @Table(primaryKeys = {Status.ID}, scope = Status.SCOPE, readonly = true)
    public interface Status {
        String SCOPE = DS;

        @TableName
        String TABLE_NAME = "Status";

        @Column
        String ID = "ID";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE)
        String NAME = "Name";
    }

    @Table(primaryKeys = {Tag.ID}, scope = Tag.SCOPE, readonly = true, notifyUris = {TagItemMapping.TagItemMappingWithNamesUri})
    public interface Tag {
        String SCOPE = DS;

        @TableName
        String TABLE_NAME = "Tag";

        @Column
        String ID = "ID";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE)
        String NAME = "Name";

        String TagNotUsed = "TagNotUsed";

        String TagNotUsedUri = "content://" + Constants.AUTHORITY + "/" + TagNotUsed;
    }

    @Table(primaryKeys = {Priority.ID}, scope = Priority.SCOPE, readonly = true)
    public interface Priority {
        String SCOPE = DS;

        @TableName
        String TABLE_NAME = "Priority";

        @Column
        String ID = "ID";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE)
        String NAME = "Name";
    }

    @Table(primaryKeys = {User.ID}, scope = User.SCOPE, readonly = true)
    public interface User {
        String SCOPE = DS;

        @TableName
        String TABLE_NAME = "User";

        @Column(type = ColumnType.GUID)
        String ID = "ID";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE)
        String NAME = "Name";
    }

    @Table(primaryKeys = {List.ID}, scope = List.SCOPE, delete = {@Cascade(table = Item.TABLE_NAME, pk = {
            List.ID, List.USERID}, fk = {Item.LISTID, Item.USERID})})
    public interface List {
        String SCOPE = DS;

        @TableName
        String TABLE_NAME = "List";

        @Column(type = ColumnType.GUID)
        String ID = "ID";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE)
        String NAME = "Name";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE, nullable = true)
        String DESCRIPTION = "Description";

        @Column(type = ColumnType.GUID)
        String USERID = "UserID";

        @Column(type = ColumnType.DATETIME)
        String CREATEDATE = "CreatedDate";

        // this is the only sneeky part computed ... but well see @Column doc :)
        @Column(type = ColumnType.VARCHAR, computed = List.NAME + " || ' ' || "
                + List.DESCRIPTION)
        String NAME_DESCRIPTION = "N_D";

        // this is the only sneeky part computed ... but well see @Column doc :)
        @Column(type = ColumnType.VARCHAR, computed = List.NAME + " || ' ' || "
                + List.DESCRIPTION + " || ' ' || " + List.CREATEDATE)
        String NAME_DESCRIPTION_CREATE = "N_D_C";
    }

    @Table(primaryKeys = {Item.ID}, scope = Item.SCOPE, delete = {@Cascade(table = TagItemMapping.TABLE_NAME, pk = {
            Item.ID, Item.USERID}, fk = {TagItemMapping.ITEMID,
            TagItemMapping.USERID})})
    public interface Item {
        String SCOPE = DS;

        @TableName
        String TABLE_NAME = "Item";

        @Column(type = ColumnType.GUID)
        String ID = "ID";

        @Column(type = ColumnType.GUID)
        String LISTID = "ListID";

        @Column(type = ColumnType.GUID)
        String USERID = "UserID";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE)
        String NAME = "Name";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE, nullable = true)
        String DESCRIPTION = "Description";

        @Column(type = ColumnType.INTEGER, nullable = true)
        String PRIORITY = "Priority";

        @Column(type = ColumnType.INTEGER, nullable = true)
        String STATUS = "Status";

        @Column(type = ColumnType.DATETIME, nullable = true)
        String STARTDATE = "StartDate";

        @Column(type = ColumnType.DATETIME, nullable = true)
        String ENDDATE = "EndDate";
    }

    @Table(primaryKeys = {TagItemMapping.TAGID, TagItemMapping.ITEMID,
            TagItemMapping.USERID}, scope = TagItemMapping.SCOPE, notifyUris = {TagItemMapping.TagItemMappingWithNamesUri,
            Tag.TagNotUsedUri})
    public interface TagItemMapping {
        String SCOPE = DS;

        @TableName
        String TABLE_NAME = "TagItemMapping";

        @Column(type = ColumnType.INTEGER)
        String TAGID = "TagID";

        @Column(type = ColumnType.GUID)
        String ITEMID = "ItemID";

        @Column(type = ColumnType.GUID)
        String USERID = "UserID";

        String TagItemMappingWithNames = "TagItemMappingWithNames";

        String TagItemMappingWithNamesUri = "content://" + Constants.AUTHORITY + "/" + TagItemMappingWithNames;
    }

}
