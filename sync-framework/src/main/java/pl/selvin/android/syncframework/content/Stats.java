package pl.selvin.android.syncframework.content;

import android.content.SyncStats;
import android.os.Parcel;
import android.os.Parcelable;

@SuppressWarnings("WeakerAccess")
public class Stats implements Parcelable {
	@SuppressWarnings({"unused", "RedundantSuppression"})
	public static final Creator<Stats> CREATOR = new Creator<Stats>() {
		public Stats createFromParcel(Parcel in) {
			return new Stats(in);
		}

		public Stats[] newArray(int size) {
			return new Stats[size];
		}
	};
	public SyncStats stats = new SyncStats();
	public boolean isInterrupted;

	public Stats() {
		isInterrupted = false;
	}

	public Stats(Parcel in) {
		stats = in.readParcelable(ClassLoader.getSystemClassLoader());
		isInterrupted = in.readByte() == 1;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeParcelable(stats, flags);
		dest.writeByte((byte) (isInterrupted ? 1 : 0));
	}

	public boolean hasErrors() {
		return stats.numIoExceptions + stats.numAuthExceptions + stats.numConflictDetectedExceptions +
				stats.numParseExceptions > 0;
	}
}