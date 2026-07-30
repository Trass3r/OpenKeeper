/*
 * Copyright (C) 2014-2026 OpenKeeper contributors
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package toniarts.openkeeper.utils;

import java.util.function.Supplier;

/**
 * Logging facade that provides the small Logger surface used by
 * OpenKeeper while remaining available on Android.
 */
public final class Logger {

    public enum Level {
        ALL,
        TRACE,
        DEBUG,
        INFO,
        WARNING,
        ERROR,
        OFF
    }

    private final java.util.logging.Logger delegate;

    private Logger(String name) {
        delegate = java.util.logging.Logger.getLogger(name);
    }

    public static Logger getLogger(String name) {
        return new Logger(name);
    }

    public String getName() {
        return delegate.getName();
    }

    public boolean isLoggable(Level level) {
        return delegate.isLoggable(toJavaLevel(level));
    }

    public void log(Level level, String message) {
        delegate.log(toJavaLevel(level), message);
    }

    public void log(Level level, Object object) {
        delegate.log(toJavaLevel(level), String.valueOf(object));
    }

    public void log(Level level, String message, Throwable thrown) {
        delegate.log(toJavaLevel(level), message, thrown);
    }

    public void log(Level level, String format, Object... parameters) {
        delegate.log(toJavaLevel(level), format, parameters);
    }

    public void log(Level level, Supplier<String> messageSupplier) {
        if (isLoggable(level)) {
            delegate.log(toJavaLevel(level), messageSupplier.get());
        }
    }

    public void log(Level level, Supplier<String> messageSupplier, Throwable thrown) {
        if (isLoggable(level)) {
            delegate.log(toJavaLevel(level), messageSupplier.get(), thrown);
        }
    }

    private static java.util.logging.Level toJavaLevel(Level level) {
        return switch (level) {
            case ALL -> java.util.logging.Level.ALL;
            case TRACE -> java.util.logging.Level.FINER;
            case DEBUG -> java.util.logging.Level.FINE;
            case INFO -> java.util.logging.Level.INFO;
            case WARNING -> java.util.logging.Level.WARNING;
            case ERROR -> java.util.logging.Level.SEVERE;
            case OFF -> java.util.logging.Level.OFF;
        };
    }
}
