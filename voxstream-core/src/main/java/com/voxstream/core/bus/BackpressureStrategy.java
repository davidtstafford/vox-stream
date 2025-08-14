package com.voxstream.core.bus;

/**
 * Strategies for handling event queue overflow.
 */
public enum BackpressureStrategy {
    DROP_OLDEST,
    DROP_NEW,
    BLOCK
}
