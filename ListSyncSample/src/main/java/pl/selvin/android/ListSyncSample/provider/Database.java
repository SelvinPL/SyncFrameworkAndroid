package pl.selvin.android.listsyncsample.provider;

import pl.selvin.android.syncframework.ColumnType;
import pl.selvin.android.syncframework.annotation.Cascade;
import pl.selvin.android.syncframework.annotation.Column;
import pl.selvin.android.syncframework.annotation.Table;
import pl.selvin.android.syncframework.annotation.TableName;

public class Database {

    static final String DS = "DefaultScope";

    @Table(primaryKeys = {Status.ID}, scope = DS, readonly = true)
    public static interface Status {
        @TableName
        public static final String TABLE_NAME = "Status";

        @Column
        public static final String ID = "ID";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE)
        public static final String NAME = "Name";
    }

    @Table(primaryKeys = {Tag.ID}, scope = DS, readonly = true)
    public static interface Tag {
        @TableName
        public static final String TABLE_NAME = "Tag";

        @Column
        public static final String ID = "ID";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE)
        public static final String NAME = "Name";
    }

    @Table(primaryKeys = {Priority.ID}, scope = DS, readonly = true)
    public static interface Priority {
        @TableName
        public static final String TABLE_NAME = "Priority";

        @Column
        public static final String ID = "ID";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE)
        public static final String C_NAME = "Name";
    }

    @Table(primaryKeys = {User.ID}, scope = DS, readonly = true)
    public static interface User {
        @TableName
        public static final String TABLE_NAME = "User";

        @Column(type = ColumnType.GUID)
        public static final String ID = "ID";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE)
        public static final String NAME = "Name";
    }

    @Table(primaryKeys = {List.ID}, scope = DS, delete = {@Cascade(table = Item.TABLE_NAME, pk = {
            List.ID, List.USERID}, fk = {Item.LISTID, Item.USERID})})
    public static interface List {
        @TableName
        public static final String TABLE_NAME = "List";

        @Column(type = ColumnType.GUID)
        public static final String ID = "ID";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE)
        public static final String NAME = "Name";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE, nullable = true)
        public static final String DESCRIPTION = "Description";

        @Column(type = ColumnType.GUID)
        public static final String USERID = "UserID";

        @Column(type = ColumnType.DATETIME)
        public static final String CREATEDATE = "CreatedDate";

        // this is the only sneeky part computed ... but well see @Column doc :)
        @Column(type = ColumnType.VARCHAR, computed = List.NAME + " || ' ' || "
                + List.DESCRIPTION)
        public static final String NAME_DESCRIPTION = "N_D";

        // this is the only sneeky part computed ... but well see @Column doc :)
        @Column(type = ColumnType.VARCHAR, computed = List.NAME + " || ' ' || "
                + List.DESCRIPTION + " || ' ' || " + List.CREATEDATE)
        public static final String NAME_DESCRIPTION_CREATE = "N_D_C";
    }

    @Table(primaryKeys = {Item.ID}, scope = DS, delete = {@Cascade(table = TagItemMapping.TABLE_NAME, pk = {
            Item.ID, Item.USERID}, fk = {TagItemMapping.ITEMID,
            TagItemMapping.USERID})})
    public static interface Item {
        @TableName
        public static final String TABLE_NAME = "Item";

        @Column(type = ColumnType.GUID)
        public static final String ID = "ID";

        @Column(type = ColumnType.GUID)
        public static final String LISTID = "ListID";

        @Column(type = ColumnType.GUID)
        public static final String USERID = "UserID";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE)
        public static final String NAME = "Name";

        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE, nullable = true)
        public static final String DESCRIPTION = "Description";

        @Column(type = ColumnType.INTEGER, nullable = true)
        public static final String PRIORITY = "Priority";

        @Column(type = ColumnType.INTEGER, nullable = true)
        public static final String STATUS = "Status";

        @Column(type = ColumnType.DATETIME, nullable = true)
        public static final String STARTDATE = "StartDate";

        @Column(type = ColumnType.DATETIME, nullable = true)
        public static final String ENDDATE = "EndDate";
    }

    @Table(primaryKeys = {TagItemMapping.TAGID, TagItemMapping.ITEMID,
            TagItemMapping.USERID}, scope = DS)
    public static interface TagItemMapping {
        @TableName
        public static final String TABLE_NAME = "TagItemMapping";

        @Column(type = ColumnType.INTEGER)
        public static final String TAGID = "TagID";

        @Column(type = ColumnType.GUID)
        public static final String ITEMID = "ItemID";

        @Column(type = ColumnType.GUID)
        public static final String USERID = "UserID";
    }

}
