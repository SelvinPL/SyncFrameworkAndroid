package pl.selvin.android.listsyncsample.utils;

import java.util.Objects;

public final class SuppliedThreadLocalCompat<T> extends ThreadLocal<T> {

    private final Supplier<? extends T> supplier;

    public SuppliedThreadLocalCompat(Supplier<? extends T> supplier) {
        this.supplier = Objects.requireNonNull(supplier);
    }

    @Override
    protected T initialValue() {
        return supplier.get();
    }

    public interface Supplier<T> {
        T get();
    }
}