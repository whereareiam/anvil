package me.whereareiam.anvil.edition.java.protocol.model.overlay;

import lombok.Value;

/**
 * Removes a boss bar. It carries nothing but the bar's ID in its packet, so every removal equals every other.
 */
@Value
public class BossEventRemove implements BossEventOperation {
}
