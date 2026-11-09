// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.token;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

/** A runtime type and value constraint for an immutable token value. */
public final class TokenType<T> {
    public static final TokenType<Float> FLOAT = of(Float.class, Float::isFinite,
            (a, b, p) -> (float) (a * (1 - p) + b * p));
    public static final TokenType<Double> DOUBLE = of(Double.class, Double::isFinite,
            (a, b, p) -> a * (1 - p) + b * p);
    public static final TokenType<Float> NON_NEGATIVE = of(Float.class, v -> Float.isFinite(v) && v >= 0,
            (a, b, p) -> FLOAT.interpolate(a, b, p, p));
    public static final TokenType<Float> OPACITY = of(Float.class, v -> v >= 0 && v <= 1,
            (a, b, p) -> FLOAT.interpolate(a, b, p, p));
    public static final TokenType<String> STRING = discrete(String.class, s -> !s.isBlank() && s.length() <= 256);
    public static final TokenType<Integer> INTEGER = discrete(Integer.class, i -> true);
    public static final TokenType<Boolean> BOOLEAN = discrete(Boolean.class, v -> true);
    public static final TokenType<Duration> DURATION = of(Duration.class,
            d -> !d.isNegative() && d.compareTo(Duration.ofDays(1)) <= 0,
            (a, b, p) -> Duration.ofNanos(Math.round(a.toNanos() * (1 - p) + b.toNanos() * p)));
    public static final TokenType<ColorValue> COLOR = of(ColorValue.class, v -> true, ColorValue::interpolateOklab);

    private final Class<T> valueClass;
    private final Predicate<T> validator;
    private final Interpolator<T> interpolator;

    private TokenType(Class<T> valueClass, Predicate<T> validator, Interpolator<T> interpolator) {
        this.valueClass = Objects.requireNonNull(valueClass);
        this.validator = Objects.requireNonNull(validator);
        this.interpolator = interpolator;
    }

    /** A continuous interpolator must return a valid value for progress in [0, 1]. */
    @FunctionalInterface
    public interface Interpolator<T> {
        T interpolate(T from, T to, double progress);
    }

    /** Declares a continuous immutable value type. Keep one shared instance per semantic type. */
    public static <T> TokenType<T> of(Class<T> valueClass, Predicate<T> validator, Interpolator<T> interpolator) {
        return new TokenType<>(valueClass, validator, Objects.requireNonNull(interpolator));
    }

    /** Declares a value type that switches at the configured discrete cutover. */
    public static <T> TokenType<T> discrete(Class<T> valueClass, Predicate<T> validator) {
        return new TokenType<>(valueClass, validator, null);
    }

    /** Java representation, useful when registering a resource codec. */
    public Class<T> valueClass() { return valueClass; }

    /** True when a continuous interpolator is registered. */
    public boolean continuous() { return interpolator != null; }

    /** Checks both the Java type and this token type's value constraint. */
    public T cast(Object value) {
        if (!valueClass.isInstance(value)) {
            throw new IllegalArgumentException("Expected " + valueClass.getSimpleName() + ", got " + value);
        }
        T checked = valueClass.cast(value);
        if (!validator.test(checked)) throw new IllegalArgumentException("Invalid " + valueClass.getSimpleName() + ": " + value);
        return checked;
    }

    /** Interpolates validated endpoints, preserving their exact values at 0 and 1. */
    public T interpolate(Object from, Object to, double progress, double discreteProgress) {
        if (!Double.isFinite(progress) || !Double.isFinite(discreteProgress)) {
            throw new IllegalArgumentException("Non-finite interpolation progress");
        }
        T start = cast(from);
        T end = cast(to);
        if (progress <= 0 || start.equals(end)) return start;
        if (progress >= 1) return end;
        if (interpolator == null) return discreteProgress >= 0.5 ? end : start;
        return cast(interpolator.interpolate(start, end, progress));
    }
}
