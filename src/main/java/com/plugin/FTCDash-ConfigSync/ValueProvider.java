package com.plugin.config;

/**
 * A generic interface for providing and storing a single value.
 *
 * @param <T> type of the value
 */
public interface ValueProvider<T> {
    /** Returns the value. */
    T get();

    /**
     * Updates the value. {@link #get()} should now return this new value.
     *
     * @param value new value
     */
    void set(T value);
}