package pl.selvin.android.syncframework.content;

import android.content.UriMatcher;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.SparseArray;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import pl.selvin.android.syncframework.ColumnType;
import pl.selvin.android.syncframework.SetupInterface;
import pl.selvin.android.syncframework.annotation.Column;
import pl.selvin.android.syncframework.annotation.Table;
import pl.selvin.android.syncframework.annotation.TableName;

/**
 * Created by selvin on 24.02.14.
 */
public class ContentHelper {
    // why this name... to ensure that your never call your table like this
    public static final String DOSYNC = "pl_selvin_android_syncframework_dosync";
    public static final String DOCLEAR = "pl_selvin_android_syncframework_doclear";
    public static final int uriSyncCode = 0x10000;
    public static final int uriClearCode = 0x20000;
    public static final int uriCode = 0xfff;
    public static final int uriCodeItemFlag = 0x1000;
    public static final int uriCodeItemRowIDFlag = 0x2000 | uriCodeItemFlag;
    public static final int uriCodeViewFlag = 0x4000;
    private static HashMap<Class<? extends SetupInterface>, ContentHelper> instances = new HashMap<Class<? extends SetupInterface>, ContentHelper>();
    public final Uri CONTENT_URI;
    public final String SERVICE_URI;
    public final String AUTHORITY;
    public final String DOWNLOAD_SERVICE_URI;
    public final String UPLOAD_SERVICE_URI;
    public final Class<?> DB_CONTAINER_CLASS;
    public final Uri SYNC_URI;
    public final Uri CLEAR_URI;
    public final String DATABASE_NAME;
    public final int DATABASE_VERSION;
    public static final String SYNCTONETWORK = "syncToNetwork";
    private final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
    private final SparseArray<TableInfo> AllTableInfoCode = new SparseArray<TableInfo>();
    private final HashMap<String, TableInfo> AllTableInfo = new HashMap<String, TableInfo>();

    public TableInfo getTableFromCode(int code) {
        return AllTableInfoCode.get(code);
    }

    protected ContentHelper(Class<? extends SetupInterface> setupClass) {
        SetupInterface setupInterface = null;
        try {
            setupInterface = setupClass.newInstance();
        } catch (Exception ignored) {
        }
        if (setupInterface != null) {

            AUTHORITY = setupInterface.getAuthority();
            CONTENT_URI = Uri.parse("content://" + AUTHORITY);
            SERVICE_URI = setupInterface.getServiceUrl();
            UPLOAD_SERVICE_URI = SERVICE_URI + "%s.svc/%s/UploadChanges%s";
            DOWNLOAD_SERVICE_URI = SERVICE_URI + "%s.svc/%s/DownloadChanges%s";
            DB_CONTAINER_CLASS = setupInterface.getDatabaseClass();
            DATABASE_NAME = setupInterface.getDatabaseName();
            DATABASE_VERSION = setupInterface.getDatabaseVersion();
            SYNC_URI = Uri.withAppendedPath(CONTENT_URI, DOSYNC);
            CLEAR_URI = Uri.withAppendedPath(CONTENT_URI, DOCLEAR);
            matcher.addURI(AUTHORITY, DOSYNC + "/*/*", uriSyncCode);
            matcher.addURI(AUTHORITY, DOCLEAR, uriClearCode);
            final Class<?>[] clazzes = DB_CONTAINER_CLASS.getClasses();
            final ArrayList<ColumnInfo> columns = new ArrayList<ColumnInfo>();
            final ArrayList<ColumnInfo> columnsComputed = new ArrayList<ColumnInfo>();
            final HashMap<String, ColumnInfo> columnsHash = new HashMap<String, ColumnInfo>();
            int code = 0;
            for (final Class<?> clazz : clazzes) {
                final Table table = clazz.getAnnotation(Table.class);
                if (table != null) {
                    code++;
                    columns.clear();
                    columnsComputed.clear();
                    columnsHash.clear();
                    String tname = null;
                    final String tscope = table.scope();
                    final Field[] fields = clazz.getDeclaredFields();
                    for (final Field field : fields) {
                        final Column column = field.getAnnotation(Column.class);
                        if (column != null) {
                            try {
                                final String colname = (String) field.get(null);
                                final ColumnInfo col = new ColumnInfo(tscope,
                                        colname, column);
                                if (!column.computed().equals("")) {
                                    columnsComputed.add(col);
                                    columnsHash.put(colname, col);
                                } else {
                                    columns.add(col);
                                    columnsHash.put(colname, col);
                                }

                            } catch (Exception ignored) {
                            }
                        } else if (field.getAnnotation(TableName.class) != null) {
                            try {
                                tname = (String) field.get(null);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    final TableInfo tab = new TableInfo(tscope, tname, columns,
                            columnsComputed, columnsHash, table.primaryKeys(), table, AUTHORITY);
                    matcher.addURI(AUTHORITY, tname, code);
                    matcher.addURI(AUTHORITY, tname + "/ROWID/#", code
                            | uriCodeItemRowIDFlag);
                    if (tab.primaryKey.length > 0) {
                        String sig = tname;
                        for (int c = 0; c < tab.primaryKey.length; c++) {
                            sig = sig
                                    + "/"
                                    + (tab.primaryKey[c].type == ColumnType.INTEGER ? "#"
                                    : "*");
                        }
                        matcher.addURI(AUTHORITY, sig, code | uriCodeItemFlag);
                    }
                    AllTableInfo.put(tab.scope_name, tab);
                    AllTableInfoCode.put(code, tab);
                }
            }
        } else {
            CONTENT_URI = null;
            SERVICE_URI = null;
            SYNC_URI = null;
            CLEAR_URI = null;
            DOWNLOAD_SERVICE_URI = null;
            UPLOAD_SERVICE_URI = null;
            DB_CONTAINER_CLASS = null;
            AUTHORITY = null;
            DATABASE_NAME = null;
            DATABASE_VERSION = 0;
        }
    }

    public static ContentHelper getInstance(Class<? extends SetupInterface> clazz) {
        if (instances.containsKey(clazz))
            return instances.get(clazz);
        final ContentHelper ret = new ContentHelper(clazz);
        instances.put(clazz, ret);
        return ret;
    }


    public Uri.Builder getDirUriBuilder(String tableName) {
        return CONTENT_URI.buildUpon().appendPath(tableName);
    }

    public Uri.Builder getDirUriBuilder(String tableName,
                                        Boolean syncToNetwork) {
        return getDirUriBuilder(tableName).appendQueryParameter(SYNCTONETWORK,
                Boolean.toString(syncToNetwork));
    }

    public Uri getDirUri(String tableName) {
        return getDirUriBuilder(tableName).build();
    }

    public Uri getDirUri(String tableName, Boolean syncToNetwork) {
        return getDirUriBuilder(tableName, syncToNetwork).build();
    }

    public Uri.Builder getItemUriBuilder(String tableName,
                                         Object... primaryKeys) {
        Uri.Builder builder = CONTENT_URI.buildUpon();
        builder.appendPath(tableName);
        if (primaryKeys == null || primaryKeys.length == 0) {
            throw new IllegalArgumentException(
                    "primary_keys should be empty or null");
        } else {
            for (final Object primaryKey : primaryKeys) {
                builder.appendPath(primaryKey.toString());
            }
        }
        return builder;
    }

    public Uri.Builder getItemUriBuilder(String tableName,
                                         Boolean syncToNetwork, Object... primaryKeys) {
        return getItemUriBuilder(tableName, primaryKeys).appendQueryParameter(
                SYNCTONETWORK, Boolean.toString(syncToNetwork));
    }

    public Uri getItemUri(String tableName, Object... primaryKeys) {
        return getItemUriBuilder(tableName, primaryKeys).build();
    }

    public Uri getItemUri(String tableName, Boolean syncToNetwork,
                          Object... primaryKeys) {
        return getItemUriBuilder(tableName, syncToNetwork, primaryKeys).build();
    }

    public Uri.Builder getItemUriBuilder(String tableName, long _id) {
        return CONTENT_URI.buildUpon().appendPath(tableName)
                .appendPath("ROWID").appendPath(Long.toString(_id));
    }

    public Uri.Builder getItemUriBuilder(String tableName,
                                         Boolean syncToNetwork, long _id) {
        return getItemUriBuilder(tableName, _id).appendQueryParameter(
                SYNCTONETWORK, Boolean.toString(syncToNetwork));
    }

    public Uri getItemUri(String tableName, long _id) {
        return getItemUriBuilder(tableName, _id).build();
    }

    public Uri getItemUri(String tableName, boolean syncToNetwork,
                          long _id) {
        return getItemUriBuilder(tableName, syncToNetwork, _id).build();
    }

    public Uri getSyncUri(String serviceName, String syncScope) {
        return Uri.withAppendedPath(
                Uri.withAppendedPath(SYNC_URI, serviceName), syncScope);
    }

    public Uri getClearUri() {
        return CLEAR_URI;
    }

    public int matchUri(Uri uri) {
        return matcher.match(uri);
    }

    public boolean hasDirtTable(SQLiteDatabase db, String scope) {
        boolean ret = false;
        for (TableInfo tab : AllTableInfo.values()) {
            if (tab.scope.toLowerCase().equals(scope.toLowerCase())) {
                ret = tab.hasDirtData(db);
                if (ret)
                    break;
            }
        }
        return ret;
    }

    public TableInfo getTableFromType(String type) {
        return AllTableInfo.get(type);
    }

    public Collection<? extends TableInfo> getAllTables() {
        return AllTableInfo.values();
    }
}
