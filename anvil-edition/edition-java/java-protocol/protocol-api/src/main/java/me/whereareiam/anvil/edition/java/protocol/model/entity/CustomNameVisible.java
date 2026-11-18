package me.whereareiam.anvil.edition.java.protocol.model.entity;

import lombok.Value;

/**
 * Whether the client shows the entity's custom name above it.
 */
@Value
public class CustomNameVisible implements EntityDataValue {
	/**
	 * Whether the name is shown.
	 */
	boolean visible;
}
