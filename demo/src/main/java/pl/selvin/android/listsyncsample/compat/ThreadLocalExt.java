package pl.selvin.android.listsyncsample.compat;

import androidx.annotation.NonNull;

public final class ThreadLocalExt {

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