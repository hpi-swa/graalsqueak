/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.util;

import java.util.logging.Level;

import com.oracle.truffle.api.TruffleLogger;

import de.hpi.swa.trufflesqueak.shared.SqueakLanguageConfig;

/**
 * Logging infrastructure for TruffleSqueak. All loggers are consistently defined here, so that it
 * is clear which loggers are available.
 */
public final class LogUtils {
    public static final TruffleLogger GC = TruffleLogger.getLogger(SqueakLanguageConfig.ID, "gc");
    public static final TruffleLogger INTEROP = TruffleLogger.getLogger(SqueakLanguageConfig.ID, "interop");
    public static final TruffleLogger INTERRUPTS = TruffleLogger.getLogger(SqueakLanguageConfig.ID, "interrupts");
    public static final TruffleLogger ITERATE_FRAMES = TruffleLogger.getLogger(SqueakLanguageConfig.ID, "iterate-frames");
    public static final TruffleLogger PRIMITIVES = TruffleLogger.getLogger(SqueakLanguageConfig.ID, "primitives");
    public static final TruffleLogger SCHEDULING = TruffleLogger.getLogger(SqueakLanguageConfig.ID, "scheduling");

    public static final boolean GC_IS_LOGGABLE_FINE = GC.isLoggable(Level.FINE);
}
