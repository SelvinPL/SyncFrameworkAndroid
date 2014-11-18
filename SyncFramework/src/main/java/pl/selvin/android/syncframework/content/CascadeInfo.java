package pl.selvin.android.syncframework.content;

import pl.selvin.android.syncframework.annotation.Cascade;

final class CascadeInfo {
    final String table;
    final String[] pk;
    final String[] fk;

    public CascadeInfo(final Cascade cascade) {
        table = cascade.table();
        pk = cascade.pk();
        fk = cascade.fk();
    }
}