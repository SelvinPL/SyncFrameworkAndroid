package pl.selvin.android.syncframework.content;

import pl.selvin.android.syncframework.annotation.Column;

final class ColumnInfo {
    public final String name;
    public final int type;
    public boolean nullable;
    public final String extras;
    public final String computed;

    public ColumnInfo(final String scope, final String name, final Column column) {
        this.name = name;
        type = column.type();
        nullable = column.nullable();
        extras = column.extras();
        computed = column.computed();
    }
}