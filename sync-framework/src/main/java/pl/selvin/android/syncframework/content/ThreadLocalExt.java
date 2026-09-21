package pl.selvin.android.syncframework.content;

import androidx.annotation.NonNull;

final class ThreadLocalExt {

	public static <S> ThreadLocal<S> withInitial(@NonNull final Supplier<? extends S> supplier) {
		return new ThreadLocal<S>() {
			@Override
			protected S initialValue() {
				return supplier.get();
			}
		};
	}

	public interface Supplier<T> {
		T get();
	}
}