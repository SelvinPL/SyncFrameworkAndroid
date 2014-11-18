package pl.selvin.android.syncframework;

public class ColumnType {
    public final static int INTEGER = 0;
    public final static int VARCHAR = 1;
    public final static int GUID = 2;
    public final static int DATETIME = 3;
    public final static int NUMERIC = 4;
    public final static int BOOLEAN = 5;
    public final static int BLOB = 6;
    static String[] names = new String[]{"INTEGER", "VARCHAR", "GUID", "DATETIME", "NUMERIC", "BOOLEAN", "BLOB"};

    public static String getName(int type) {
        return names[type];
    }
}