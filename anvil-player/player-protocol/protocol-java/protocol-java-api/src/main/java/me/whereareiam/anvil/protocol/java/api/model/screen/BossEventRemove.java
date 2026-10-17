package me.whereareiam.anvil.protocol.java.api.model.screen;

import lombok.Value;

/**
 * Removes a boss bar. It carries nothing but the bar's ID in its packet, so every removal equals every other.
 */
@Value
public class BossEventRemove implements BossEventOperation {
}
