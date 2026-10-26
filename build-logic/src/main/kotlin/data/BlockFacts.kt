package me.whereareiam.anvil.buildlogic.data

/**
 * The hand-maintained facts of `block-facts.toml` that minecraft-data does not carry, merged per block.
 *
 * @property blocks the facts of each block, by namespaced name such as `minecraft:ice`
 */
data class BlockFacts(val blocks: Map<String, Fact>) {
	/**
	 * The facts of [name], or the vanilla defaults when the file names no fact of it.
	 */
	fun of(name: String): Fact = blocks[name] ?: Fact()

	/**
	 * What vanilla says about one block's movement. Absent values keep vanilla's defaults.
	 *
	 * @property friction slipperiness, `Block.getFriction()`; vanilla's default is `0.6F`
	 * @property speedFactor `Block.getSpeedFactor()`; vanilla's default is `1.0F`
	 * @property jumpFactor `Block.getJumpFactor()`; vanilla's default is `1.0F`
	 * @property bounce the factor `Block.updateEntityAfterFallOn` multiplies the reversed fall speed with
	 * @property stuckHorizontal the horizontal factor of `Entity.makeStuckInBlock`
	 * @property stuckVertical the vertical factor of `Entity.makeStuckInBlock`
	 * @property fluid the fluid every state of the block holds, `WATER` or `LAVA`
	 * @property traits the block's movement traits, names of the Java `BlockTrait` constants
	 * @property propertyStarts the lowest value of each integer property that does not start at `0`, as vanilla's
	 * `IntegerProperty.create(name, min, max)` declares it; minecraft-data leaves out the values of integer
	 * properties in some releases
	 */
	data class Fact(
		val friction: Float? = null,
		val speedFactor: Float? = null,
		val jumpFactor: Float? = null,
		val bounce: Double? = null,
		val stuckHorizontal: Double? = null,
		val stuckVertical: Double? = null,
		val fluid: String? = null,
		val traits: Set<String> = emptySet(),
		val propertyStarts: Map<String, Int> = emptyMap(),
	)
}
