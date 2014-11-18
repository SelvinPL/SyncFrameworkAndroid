/***
 Copyright (c) 2014 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */

package pl.selvin.android.syncframework.content;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

import pl.selvin.android.syncframework.BuildConfig;
import pl.selvin.android.syncframework.ColumnType;
import pl.selvin.android.syncframework.annotation.Cascade;
import pl.selvin.android.syncframework.annotation.Table;

public final class TableInfo {

    final static String msdate = "\"/Date(%s)/\"";
    final static SimpleDateFormat sdf = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss");

    static {
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        // comment this line if you're store date in SQL in UTC format
    }

    public final HashMap<String, String> map = new HashMap<String, String>();
    final ColumnInfo[] columns;
    final ColumnInfo[] columnsComputed;
    final CascadeInfo[] cascadeDelete;
    final String name;
    final String scope;
    final String scope_name;
    final ColumnInfo[] primaryKey;
    final String[] primaryKeyStrings;
    final String DirMime;
    final String ItemMime;
    final boolean readonly;
    final ContentValues vals = new ContentValues(2);
    public String[] notifyUris;
    String selection = null;

    TableInfo(String scope, String name, ArrayList<ColumnInfo> columns,
              ArrayList<ColumnInfo> columnsComputed,
              HashMap<String, ColumnInfo> columnsHash,
              String[] primaryKey, Table table, String AUTHORITY) {
        this.name = name;
        final Cascade[] delete = table.delete();
        cascadeDelete = delete.length > 0 ? new CascadeInfo[delete.length] : null;
        for (int i = 0; i < delete.length; i++) {
            cascadeDelete[i] = new CascadeInfo(delete[i]);
        }
        readonly = table.readonly();
        this.columns = columns.toArray(new ColumnInfo[columns.size()]);
        map.put("_id", "[" + name + "].ROWID AS _id");
        for (ColumnInfo ci : columns) {
            map.put(ci.name, ci.name);
        }
        this.columnsComputed = columnsComputed
                .toArray(new ColumnInfo[columnsComputed.size()]);
        for (ColumnInfo ci : columnsComputed) {
            map.put(ci.name, String.format("%s AS %s", ci.computed, ci.name));
        }
        this.scope = scope;
        this.primaryKey = new ColumnInfo[primaryKey.length];
        for (int c = 0; c < primaryKey.length; c++) {
            this.primaryKey[c] = columnsHash.get(primaryKey[c]);
        }
        this.primaryKeyStrings = primaryKey;
        scope_name = String.format("%s.%s", scope, name);
        DirMime = String.format("%s/%s.%s",
                ContentResolver.CURSOR_DIR_BASE_TYPE,
                AUTHORITY, scope_name);
        ItemMime = String.format("%s/%s.%s",
                ContentResolver.CURSOR_ITEM_BASE_TYPE,
                AUTHORITY, scope_name);
        notifyUris = table.notifyUris();

    }

    final String getSelection() {
        if (selection == null) {
            String newSelection = "";
            for (String primaryKeyString : primaryKeyStrings) {
                newSelection = String.format("%s AND %s=?", newSelection,
                        primaryKeyString);
            }
            selection = newSelection;
        }
        return selection;
    }

    public boolean hasDirtData(SQLiteDatabase db) {
        Cursor c = db.query(name, null, _.isDirtyP, new String[]{"1"}, null,
                null, null);
        if (c.moveToFirst()) {
            c.close();
            return true;
        }
        c.close();
        return false;
    }

    static class OperationHolder {
        public final static int DELETE = 0;
        public final static int UPDATE = 1;
        public final static int INSERT = 2;


        public OperationHolder(String table, int operation, ContentValues values, String uri) {
            this.table = table;
            this.operation = operation;
            this.values = values;
            this.uri = uri;
        }

        public long execute(SQLiteDatabase db) {
            switch (operation) {
                case DELETE:
                    return db.delete(table, _.uriP, new String[]{uri});
                case UPDATE:
                    return db.update(table, values, _.uriP, new String[]{uri});
                case INSERT:
                    return db.insert(table, null, values);
                default:
                    return 0;
            }
        }

        final String table;
        final int operation;
        final ContentValues values;
        final String uri;
    }

    public void GetChanges(SQLiteDatabase db, JsonGenerator gen,
                           ArrayList<TableInfo> notifyTableInfo)
            throws IOException {
        String[] cols = new String[columns.length + 3];
        int i = 0;
        for (; i < columns.length; i++)
            cols[i] = columns[i].name;
        cols[i] = _.uri;
        cols[i + 1] = _.tempId;
        cols[i + 2] = _.isDeleted;
        Cursor c = db.query(name, cols, _.isDirtyP, new String[]{"1"}, null, null, null);
        //to fix startPos  > actual rows for large cursors db operations should be done after cursor is closed ...
        final ArrayList<OperationHolder> operations = new ArrayList<OperationHolder>();
        if (c.moveToFirst()) {
            if (!notifyTableInfo.contains(this))
                notifyTableInfo.add(this);
            do {


                gen.writeStartObject();
                gen.writeObjectFieldStart(_.__metadata);
                gen.writeBooleanField(_.isDirty, true);
                gen.writeStringField(_.type, scope_name);
                //Log.d("before", scope_name + ":" + c.getLong(i + 3));
                String uri = c.getString(i);
                //Log.d("after", scope_name + ":" + c.getLong(i + 3));
                if (uri == null) {
                    gen.writeStringField(_.tempId, c.getString(i + 1));
                } else {
                    gen.writeStringField(_.uri, uri);
                    final ContentValues update = new ContentValues(1);
                    update.put(_.isDirty, 0);
                    operations.add(new OperationHolder(name, OperationHolder.UPDATE, update, uri));
                }
                boolean isDeleted = c.getInt(i + 2) == 1;
                if (isDeleted) {
                    gen.writeBooleanField(_.isDeleted, true);
                    gen.writeEndObject();// meta
                    operations.add(new OperationHolder(name, OperationHolder.DELETE, null, uri));
                } else {
                    gen.writeEndObject();// meta
                    for (i = 0; i < columns.length; i++) {
                        if (columns[i].nullable && c.isNull(i)) {
                            gen.writeNullField(columns[i].name);
                        } else {
                            switch (columns[i].type) {
                                case ColumnType.BLOB:
                                    gen.writeBinaryField(columns[i].name,
                                            c.getBlob(i));
                                    break;
                                case ColumnType.BOOLEAN:
                                    gen.writeBooleanField(columns[i].name,
                                            c.getLong(i) == 1);
                                    break;
                                case ColumnType.INTEGER:
                                    gen.writeNumberField(columns[i].name,
                                            c.getLong(i));
                                    break;
                                case ColumnType.DATETIME:
                                    try {
                                        gen.writeStringField(columns[i].name,
                                                String.format(msdate,
                                                        sdf.parse(c.getString(i))
                                                                .getTime()));
                                    } catch (Exception e) {
                                        if (BuildConfig.DEBUG) {
                                            Log.e("ListSync",
                                                    e.getLocalizedMessage());
                                        }
                                    }
                                    break;
                                case ColumnType.NUMERIC:
                                    gen.writeNumberField(columns[i].name,
                                            c.getDouble(i));
                                    break;
                                default:
                                    gen.writeStringField(columns[i].name,
                                            c.getString(i));
                                    break;
                            }
                        }
                    }
                }
                gen.writeEndObject(); // end of row
            } while (c.moveToNext());
        }
        c.close();
        for (OperationHolder operation : operations)
            operation.execute(db);
    }

    final public void DeleteWithUri(String uri, SQLiteDatabase db) {
        db.delete(name, _.uriP, new String[]{uri});
    }

    @SuppressLint("NewApi")
    final public void SyncJSON(final HashMap<String, Object> hval,
                               final Metadata meta, final SQLiteDatabase db) {
        int i = 0;
        vals.clear();
        for (; i < columns.length; i++) {
            String column = columns[i].name;
            switch (columns[i].type) {
                case ColumnType.BLOB:
                    final String str = (String) hval.get(column);
                    if (str != null)
                        vals.put(column, Base64.decode(str, Base64.DEFAULT));
                    break;
                case ColumnType.BOOLEAN:
                case ColumnType.INTEGER:
                    vals.put(column, (Long) hval.get(column));
                    break;
                case ColumnType.DATETIME:
                    String date = (String) hval.get(column);
                    if (date != null) {
                        date = sdf.format(new Date(Long.parseLong(date.substring(6,
                                date.length() - 2))));
                    }
                    vals.put(column, date);
                    break;
                case ColumnType.NUMERIC:
                    Object obj = hval.get(column);
                    if (obj instanceof Double)
                        vals.put(column, (Double) obj);
                    else
                        vals.put(column, (Long) obj);
                    break;
                default:
                    vals.put(column, (String) hval.get(column));
                    break;
            }
        }
        vals.put(_.uri, meta.uri);
        vals.put(_.tempId, (String) null);
        vals.put(_.isDirty, 0);
        if (meta.tempId != null) {
            db.update(name, vals, _.tempIdP, new String[]{meta.tempId});
        } else {
            db.replace(name, null, vals);
        }
    }

    final public String DropStatement() {
        return "DROP TABLE IF EXISTS " + name;
    }

    final public String CreateStatement() {
        StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        sb.append(name);
        sb.append(" ([");
        for (final ColumnInfo column : columns) {
            sb.append(column.name);
            sb.append("] ");
            sb.append(ColumnType.getName(column.type));
            sb.append(' ');
            sb.append(column.extras);
            if (!column.nullable)
                sb.append(" NOT NULL ");
            sb.append(", [");
        }
        sb.append(_.uri + "] varchar, [" + _.tempId + "] GUID, [" + _.isDeleted
                + "] INTEGER NOT NULL DEFAULT (0)" + " , [" + _.isDirty
                + "] INTEGER NOT NULL DEFAULT (0), PRIMARY KEY (");
        sb.append(TextUtils.join(", ", primaryKeyStrings));
        sb.append("));");
        String ret = sb.toString();
        if (BuildConfig.DEBUG) {
            Log.d(BaseContentProvider.TAG, "DB-C: " + ret);
        }
        return ret;
    }
}
