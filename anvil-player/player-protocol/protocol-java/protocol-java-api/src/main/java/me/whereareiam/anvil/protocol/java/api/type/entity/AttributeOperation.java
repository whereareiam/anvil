package me.whereareiam.anvil.protocol.java.api.type.entity;

/**
 * How an attribute modifier changes an attribute's value. The game applies every modifier of one operation before the
 * next operation, in this order.
 */
public enum AttributeOperation {
	/**
	 * The amount is added to the base value.
	 */
	ADD_VALUE,
	/**
	 * The base value times the amount is added to the value.
	 */
	ADD_MULTIPLIED_BASE,
	/**
	 * The value is multiplied by one plus the amount.
	 */
	ADD_MULTIPLIED_TOTAL
}
