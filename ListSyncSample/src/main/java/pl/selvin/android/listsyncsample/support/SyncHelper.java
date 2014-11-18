package pl.selvin.android.listsyncsample.support;

import android.app.Activity;
import android.os.Build;


public abstract class SyncHelper {
    public static SyncHelper createInstance(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            return new SyncHelperFroyo(activity);
        } else {
            return new SyncHelperBase(activity);
        }
    }

	protected Activity mActivity;
    
    protected SyncHelper(Activity activity) {
        mActivity = activity;
    }
    
    public abstract void doSync();
    
    public abstract String getUserId() throws Exception;
}
