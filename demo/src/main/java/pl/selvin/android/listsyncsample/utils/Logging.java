package pl.selvin.android.listsyncsample.utils;

public class Logging {
	public static void log(Throwable t) {
		if(t != null)
			t.printStackTrace();
	}
}
