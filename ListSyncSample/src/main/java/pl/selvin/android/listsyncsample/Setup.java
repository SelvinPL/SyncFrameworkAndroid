package pl.selvin.android.listsyncsample;

import pl.selvin.android.listsyncsample.provider.Database;
import pl.selvin.android.syncframework.SetupInterface;

public class Setup implements SetupInterface {

    @Override
    public String getServiceUrl() {
        return Constants.SERVICE_URI;
    }

    @Override
    public String getAuthority() {
        return Constants.AUTHORITY;
    }

    @Override
    public Class<?> getDatabaseClass() {
        return Database.class;
    }

    @Override
    public String getDatabaseName() {
        return "listdb";
    }

    @Override
    public int getDatabaseVersion() {
        return 17;
    }
}
