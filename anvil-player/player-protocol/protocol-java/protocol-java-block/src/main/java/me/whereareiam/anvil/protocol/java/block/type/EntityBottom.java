package me.whereareiam.anvil.protocol.java.block.type;

/**
 * Where the bottom of an entity's box is relative to a block, as vanilla's {@code CollisionContext.isAbove} asks it of
 * the context-dependent blocks: they compare it with the full block's top ({@code Shapes.block()}) or with the block
 * below it, each less {@code 1.0E-5F}.
 */
public enum EntityBottom {
	/**
	 * Above the block's top less {@code 1.0E-5F}, as an entity standing on the block is: above the block and above the
	 * block below it.
	 */
	ABOVE,
	/**
	 * Inside the block, above its bottom less {@code 1.0E-5F}: above the block below it only.
	 */
	INSIDE,
	/**
	 * Not above the block's bottom less {@code 1.0E-5F}: above neither.
	 */
	BELOW
}
