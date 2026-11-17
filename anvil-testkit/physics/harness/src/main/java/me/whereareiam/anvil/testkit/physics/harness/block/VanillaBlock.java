package me.whereareiam.anvil.testkit.physics.harness.block;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Set;

/**
 * One block as the client states it, with its states in global ID order.
 */
@Value
@Builder
public class VanillaBlock {
	String name;
	int defaultState;
	List<VanillaProperty> properties;
	BlockKind kind;
	CollisionKind collision;

	/**
	 * How the block's shapes move with their position, or {@code null} when they do not.
	 */
	BlockOffset offset;

	float friction;
	float speedFactor;
	float jumpFactor;
	double bounce;

	/**
	 * The stuck speed factors, horizontal then vertical, or {@code null} when the block makes nothing stuck.
	 */
	double[] stuck;

	Set<MovementBehaviour> behaviours;

	/**
	 * The {@link MovementTags} the block is in, in the order {@code MovementTags.NAMES} lists them.
	 */
	List<String> tags;
	List<VanillaState> states;
}
