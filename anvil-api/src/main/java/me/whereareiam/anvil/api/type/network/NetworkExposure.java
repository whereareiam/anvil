package me.whereareiam.anvil.api.type.network;

/**
 * Controls whether a process listener is exposed to the execution host.
 */
public enum NetworkExposure {
    HOST,
    LOOPBACK,
    PRIVATE
}
