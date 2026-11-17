package me.whereareiam.anvil.testkit.physics.harness.block;

import lombok.Value;

import java.util.List;

/**
 * One state property of a block, its values in the order of {@code Property.getPossibleValues}, each as
 * {@code Property.getName} writes it.
 */
@Value
public class VanillaProperty {
	String name;
	List<String> values;
}
