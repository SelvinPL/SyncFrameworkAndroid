package pl.selvin.android.syncframework;

import pl.selvin.android.ListSyncSample.Constants;
import pl.selvin.android.ListSyncSample.provider.Database;

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
