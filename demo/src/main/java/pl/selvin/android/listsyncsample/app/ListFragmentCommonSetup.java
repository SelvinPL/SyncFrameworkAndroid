package pl.selvin.android.listsyncsample.app;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

public class ListFragmentCommonSetup {
	public final int loaderID;
	public final String tableName;
	public final int emptyText;
	public final Class<? extends Fragment> detailsClass;
	public final boolean deferredLoading;
	@StringRes
	public final int pickTitle;
	@StringRes
	public final int deletionTitle;
	@StringRes
	public final int deletionMessage;
	public final boolean supportDetails;
	public final int insertLoaderID;

	private ListFragmentCommonSetup(int loaderID, String tableName, int emptyText, Class<? extends Fragment> detailsClass, boolean deferredLoading, int pickTitle, int deletionTitle, int deletionMessage) {
		this.loaderID = loaderID;
		this.tableName = tableName;
		this.emptyText = emptyText;
		this.detailsClass = detailsClass;
		this.deferredLoading = deferredLoading;
		this.pickTitle = pickTitle;
		this.deletionTitle = deletionTitle;
		this.deletionMessage = deletionMessage;
		this.supportDetails = detailsClass != null;
		this.insertLoaderID = loaderID | 0xf0000000;
	}

	public static Builder createBuilder(int loaderID, String tableName, int emptyText) {
		return new SetupBuilderImp(loaderID, tableName, emptyText);
	}

	public interface Builder {
		Builder deletionTitle(@StringRes int deletionTitle);

		Builder deferredLoading();

		Builder deletionMessage(@StringRes int deletionMessage);

		Builder detailsClass(Class<? extends Fragment> detailsClass);

		/** @noinspection unused*/
		Builder pickTitle(@StringRes int pickTitle);

		ListFragmentCommonSetup create();
	}

	private static class SetupBuilderImp implements Builder {

		private final int loaderID;
		private final String tableName;
		private final int emptyText;
		private Class<? extends Fragment> detailsClass;
		private boolean deferredLoading;
		@StringRes
		private int pickTitle;
		@StringRes
		private int deletionTitle;
		@StringRes
		private int deletionMessage;

		private SetupBuilderImp(int loaderID, String tableName, @StringRes int emptyText) {
			this.loaderID = loaderID;
			this.tableName = tableName;
			this.emptyText = emptyText;
			this.detailsClass = null;
			this.deferredLoading = false;
			this.pickTitle = -1;
			this.deletionTitle = -1;
			this.deletionMessage = -1;
		}

		@Override
		public Builder deletionTitle(int deletionTitle) {
			this.deletionTitle = deletionTitle;
			return this;
		}

		@Override
		public Builder deferredLoading() {
			this.deferredLoading = true;
			return this;
		}

		@Override
		public Builder deletionMessage(int deletionMessage) {
			this.deletionMessage = deletionMessage;
			return this;
		}

		@Override
		public Builder detailsClass(Class<? extends Fragment> detailsClass) {
			this.detailsClass = detailsClass;
			return this;
		}

		@Override
		public Builder pickTitle(int pickTitle) {
			this.pickTitle = pickTitle;
			return this;
		}

		@Override
		public ListFragmentCommonSetup create() {
			return new ListFragmentCommonSetup(loaderID, tableName, emptyText, detailsClass, deferredLoading, pickTitle, deletionTitle, deletionMessage);
		}
	}
}