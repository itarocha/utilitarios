package br.com.itarocha.utilitarios.qr;

import java.util.function.Consumer;
import java.util.function.Function;

public final class Unchecked {

    private Unchecked() {
    }

    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingFunction<T, R> {
        R apply(T t) throws Exception;
    }

    public static <T> Consumer<T> unchecked(ThrowingConsumer<T> consumer) {
        return t -> {
            try {
                consumer.accept(t);
            } catch (Exception e) {
                sneakyThrow(e);
            }
        };
    }

    public static <T, R> Function<T, R> unchecked(ThrowingFunction<T, R> function) {
        return t -> {
            try {
                return function.apply(t);
            } catch (Exception e) {
                return sneakyThrow(e);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable, R> R sneakyThrow(Throwable throwable) throws E {
        throw (E) throwable;
    }
}
