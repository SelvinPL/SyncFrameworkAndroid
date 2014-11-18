package pl.selvin.android.syncframework;


public interface SetupInterface {
    String getServiceUrl();

    String getAuthority();

    Class<?> getDatabaseClass();

    String getDatabaseName();

    int getDatabaseVersion();
}
