package pl.selvin.android.ListSyncSample;

public class Constants {

	public static final String ACCOUNT_TYPE = "pl.selvin.android.ListSyncSample.sync";
	public static final String AUTHORITY = "pl.selvin.android.ListSyncSample";
	public static final String AUTHTOKEN_TYPE = "pl.selvin.android.ListSyncSample.sync";
	public static final String SERVICE_URI = "http://www.agronom.pl/listservice/";
	public static final String SYNCACTION_START = "SELVIN_SYNC_ACTION_START";
	public static final String SYNCACTION_STOP = "SELVIN_SYNC_ACTION_STOP";

	public static interface Loaders {
		public static interface Lists {
			public static final int List = 0x1;
			public static final int Info = 0x2;
		}

		public static interface Items {
			public static final int List = 0x10;
			public static final int Info = 0x20;
		}
	}

}