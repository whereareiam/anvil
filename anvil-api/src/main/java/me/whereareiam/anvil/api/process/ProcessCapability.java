package me.whereareiam.anvil.api.process;

import me.whereareiam.anvil.api.capability.Capability;

/**
 * Typed behavior owned by a scenario's logical server or proxy process, independent of simulated players.
 * A capability instance remains associated with that process across restarts and is finalized with
 * the scenario. Its implementation may require an installed agent or other provider-specific services;
 * the public capability contract does not prescribe how those services are supplied.
 */
public interface ProcessCapability extends Capability { }
