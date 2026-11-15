package me.whereareiam.anvil.protocol.java.block.generator.source;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * One block of minecraft-data's {@code blocks.json}, with the fields block data needs; the file's other fields, such
 * as hardness and drops, are ignored.
 */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class MinecraftDataBlock {
	/**
	 * The block's numeric ID, its position in the file.
	 */
	int id;
	/**
	 * The block's name without namespace, such as {@code oak_stairs}.
	 */
	@NotNull String name;
	/**
	 * The global ID of the block's first state.
	 */
	int minStateId;
	/**
	 * The global ID of the block's last state.
	 */
	int maxStateId;
	/**
	 * The global ID of the block's default state.
	 */
	int defaultState;
	/**
	 * The block's state properties, in the order that numbers its states.
	 */
	@Singular
	@NotNull List<MinecraftDataProperty> states;
}
