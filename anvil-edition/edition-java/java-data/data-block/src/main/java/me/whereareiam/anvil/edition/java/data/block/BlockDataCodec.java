package me.whereareiam.anvil.edition.java.data.block;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.edition.java.data.block.model.BlockDataSource;
import me.whereareiam.anvil.edition.java.data.block.model.BlockProperty;
import me.whereareiam.anvil.edition.java.data.block.model.BlockState;
import me.whereareiam.anvil.edition.java.data.block.model.BlockType;
import me.whereareiam.anvil.edition.java.data.block.model.collision.BlockOffset;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionBox;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionCase;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionShape;
import me.whereareiam.anvil.edition.java.data.block.model.movement.BlockMovement;
import me.whereareiam.anvil.edition.java.data.block.model.movement.StuckSpeed;
import me.whereareiam.anvil.edition.java.data.block.type.BlockFace;
import me.whereareiam.anvil.edition.java.data.block.type.BlockKind;
import me.whereareiam.anvil.edition.java.data.block.type.CollisionKind;
import me.whereareiam.anvil.edition.java.data.block.type.EntityBottom;
import me.whereareiam.anvil.edition.java.data.block.type.FluidType;
import me.whereareiam.anvil.edition.java.data.block.type.MovementBehaviour;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The shipped block data's binary form, written by the build's generator and read by {@link JavaBlockData}: one
 * class for both directions, so the two can never disagree. Both ship in this module's JAR, so the data carries no
 * format version. Enums are written by name and checked by {@code valueOf} when read.
 *
 * <p>A registry is its protocol, its {@link BlockDataSource}, the collision shapes its states use, and each block
 * with its states' shapes, fluid and faces; property values follow from {@link BlockType#propertiesAt(int)}. The index
 * lists each supported protocol, with the reason when it has no data.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BlockDataCodec {
	/**
	 * Writes a registry, compressed.
	 */
	static void writeRegistry(@NotNull BlockRegistry registry, @NotNull OutputStream output) throws IOException {
		try (DataOutputStream data = new DataOutputStream(new GZIPOutputStream(output))) {
			data.writeInt(registry.protocol());
			writeSource(data, registry.source());

			Map<CollisionShape, Integer> shapes = new LinkedHashMap<>();
			for (int stateId = 0; stateId < registry.stateCount(); stateId++) {
				BlockState state = registry.state(stateId);
				shapes.putIfAbsent(state.getCollision(), shapes.size());
				for (CollisionShape shape : state.getCollisionCases().values())
					shapes.putIfAbsent(shape, shapes.size());
			}
			data.writeInt(shapes.size());
			for (CollisionShape shape : shapes.keySet())
				writeShape(data, shape);

			data.writeInt(registry.blocks().size());
			for (BlockType block : registry.blocks()) {
				writeBlock(data, block);
				for (BlockState state : registry.states(block))
					writeState(data, state, shapes);
			}
		}
	}

	/**
	 * Reads a registry that {@link #writeRegistry} wrote.
	 */
	static @NotNull BlockRegistry readRegistry(@NotNull InputStream input) throws IOException {
		try (DataInputStream data = new DataInputStream(new GZIPInputStream(input))) {
			int protocol = data.readInt();
			BlockDataSource source = readSource(data);
			List<CollisionShape> shapes = new ArrayList<>();
			for (int count = data.readInt(); shapes.size() < count; )
				shapes.add(readShape(data));

			int blockCount = data.readInt();
			List<BlockType> blocks = new ArrayList<>(blockCount);
			List<BlockState> states = new ArrayList<>();
			for (int id = 0; id < blockCount; id++) {
				BlockType block = readBlock(data, id);
				blocks.add(block);
				for (int stateId = block.getMinStateId(); stateId <= block.getMaxStateId(); stateId++)
					states.add(readState(data, stateId, block, shapes));
			}

			return new BlockRegistry(protocol, source, blocks, states.toArray(BlockState[]::new));
		}
	}

	/**
	 * Writes the index: each supported protocol, and the reason for each that has no data.
	 */
	static void writeIndex(@NotNull JavaBlockData index, @NotNull OutputStream output) throws IOException {
		Map<Integer, String> missing = new TreeMap<>();
		index.missingProtocols().forEach(protocol -> missing.put(protocol, index.missingReason(protocol)));
		try (DataOutputStream data = new DataOutputStream(new GZIPOutputStream(output))) {
			data.writeInt(index.protocols().size());
			for (int protocol : index.protocols())
				data.writeInt(protocol);
			data.writeInt(missing.size());
			for (Map.Entry<Integer, String> entry : missing.entrySet()) {
				data.writeInt(entry.getKey());
				data.writeUTF(entry.getValue());
			}
		}
	}

	/**
	 * Reads an index that {@link #writeIndex} wrote.
	 */
	static @NotNull JavaBlockData readIndex(@NotNull InputStream input) throws IOException {
		try (DataInputStream data = new DataInputStream(new GZIPInputStream(input))) {
			TreeSet<Integer> protocols = new TreeSet<>();
			for (int count = data.readInt(); protocols.size() < count; )
				protocols.add(data.readInt());
			Map<Integer, String> missing = new TreeMap<>();
			for (int count = data.readInt(); missing.size() < count; )
				missing.put(data.readInt(), data.readUTF());

			return new JavaBlockData(protocols, missing);
		}
	}

	private static void writeSource(DataOutputStream data, BlockDataSource source) throws IOException {
		data.writeUTF(source.getMinecraft());
	}

	private static BlockDataSource readSource(DataInputStream data) throws IOException {
		return new BlockDataSource(data.readUTF());
	}

	private static void writeState(DataOutputStream data, BlockState state, Map<CollisionShape, Integer> shapes) throws IOException {
		data.writeInt(shapes.get(state.getCollision()));
		data.writeUTF(state.getFluid().name());
		data.writeByte(state.getFluidAmount());
		data.writeBoolean(state.isFluidSource());
		data.writeBoolean(state.isFluidFalling());
		data.writeBoolean(state.isBlocksFluidFlow());
		int faces = 0;
		for (BlockFace face : state.getSturdyFaces())
			faces |= 1 << face.ordinal();
		data.writeByte(faces);
		data.writeInt(state.getCollisionCases().size());
		for (Map.Entry<CollisionCase, CollisionShape> entry : state.getCollisionCases().entrySet()) {
			CollisionCase context = entry.getKey();
			data.writeUTF(context.getBottom().name());
			data.writeBoolean(context.isDescending());
			data.writeBoolean(context.isFalling());
			data.writeBoolean(context.isLeatherBoots());
			data.writeInt(shapes.get(entry.getValue()));
		}
	}

	private static BlockState readState(DataInputStream data, int stateId, BlockType block, List<CollisionShape> shapes) throws IOException {
		CollisionShape collision = shapes.get(data.readInt());
		FluidType fluid = FluidType.valueOf(data.readUTF());
		int amount = data.readByte();
		boolean source = data.readBoolean();
		boolean falling = data.readBoolean();
		boolean blocksFlow = data.readBoolean();
		int faces = data.readByte();
		Set<BlockFace> sturdy = EnumSet.noneOf(BlockFace.class);
		for (BlockFace face : BlockFace.values())
			if ((faces & 1 << face.ordinal()) != 0) sturdy.add(face);
		Map<CollisionCase, CollisionShape> cases = new LinkedHashMap<>();
		for (int count = data.readInt(), index = 0; index < count; index++)
			cases.put(new CollisionCase(EntityBottom.valueOf(data.readUTF()), data.readBoolean(), data.readBoolean(), data.readBoolean()),
					shapes.get(data.readInt()));

		return new BlockState(stateId, block, block.propertiesAt(stateId), collision, fluid, Collections.unmodifiableMap(cases), amount,
				source, falling, blocksFlow, Collections.unmodifiableSet(sturdy));
	}

	private static void writeShape(DataOutputStream data, CollisionShape shape) throws IOException {
		data.writeInt(shape.getBoxes().size());
		for (CollisionBox box : shape.getBoxes()) {
			data.writeDouble(box.getMinX());
			data.writeDouble(box.getMinY());
			data.writeDouble(box.getMinZ());
			data.writeDouble(box.getMaxX());
			data.writeDouble(box.getMaxY());
			data.writeDouble(box.getMaxZ());
		}
	}

	private static CollisionShape readShape(DataInputStream data) throws IOException {
		List<CollisionBox> boxes = new ArrayList<>();
		for (int count = data.readInt(); boxes.size() < count; )
			boxes.add(new CollisionBox(data.readDouble(), data.readDouble(), data.readDouble(), data.readDouble(),
					data.readDouble(), data.readDouble()));

		return boxes.isEmpty() ? CollisionShape.EMPTY : new CollisionShape(List.copyOf(boxes));
	}

	private static void writeBlock(DataOutputStream data, BlockType block) throws IOException {
		data.writeUTF(block.getName());
		data.writeInt(block.getMinStateId());
		data.writeInt(block.getMaxStateId());
		data.writeInt(block.getDefaultStateId());
		data.writeUTF(block.getKind().name());
		data.writeUTF(block.getCollision().name());
		writeMovement(data, block.getMovement());
		BlockOffset offset = block.getOffset();
		data.writeBoolean(offset != null);
		if (offset != null) {
			data.writeBoolean(offset.isVertical());
			data.writeDouble(offset.getMaxHorizontal());
			data.writeDouble(offset.getMaxVertical());
		}
		data.writeInt(block.getTags().size());
		for (String tag : new TreeSet<>(block.getTags()))
			data.writeUTF(tag);
		data.writeInt(block.getProperties().size());
		for (BlockProperty property : block.getProperties()) {
			data.writeUTF(property.getName());
			data.writeInt(property.getValues().size());
			for (String value : property.getValues())
				data.writeUTF(value);
		}
	}

	private static BlockType readBlock(DataInputStream data, int id) throws IOException {
		BlockType.BlockTypeBuilder block = BlockType.builder()
				.id(id)
				.name(data.readUTF())
				.minStateId(data.readInt())
				.maxStateId(data.readInt())
				.defaultStateId(data.readInt())
				.kind(BlockKind.valueOf(data.readUTF()))
				.collision(CollisionKind.valueOf(data.readUTF()))
				.movement(readMovement(data));
		if (data.readBoolean()) block.offset(new BlockOffset(data.readBoolean(), data.readDouble(), data.readDouble()));
		for (int count = data.readInt(), index = 0; index < count; index++)
			block.tag(data.readUTF());
		for (int count = data.readInt(), index = 0; index < count; index++) {
			String name = data.readUTF();
			List<String> values = new ArrayList<>();
			for (int valueCount = data.readInt(); values.size() < valueCount; )
				values.add(data.readUTF());
			block.property(new BlockProperty(name, List.copyOf(values)));
		}

		return block.build();
	}

	private static void writeMovement(DataOutputStream data, BlockMovement movement) throws IOException {
		data.writeFloat(movement.getFriction());
		data.writeFloat(movement.getSpeedFactor());
		data.writeFloat(movement.getJumpFactor());
		data.writeDouble(movement.getBounceFactor());
		StuckSpeed stuck = movement.getStuckSpeed();
		data.writeBoolean(stuck != null);
		if (stuck != null) {
			data.writeDouble(stuck.getHorizontal());
			data.writeDouble(stuck.getVertical());
		}
		data.writeInt(movement.getBehaviours().size());
		for (MovementBehaviour behaviour : movement.getBehaviours())
			data.writeUTF(behaviour.name());
	}

	private static BlockMovement readMovement(DataInputStream data) throws IOException {
		BlockMovement.BlockMovementBuilder movement = BlockMovement.builder()
				.friction(data.readFloat())
				.speedFactor(data.readFloat())
				.jumpFactor(data.readFloat())
				.bounceFactor(data.readDouble());
		if (data.readBoolean()) movement.stuckSpeed(new StuckSpeed(data.readDouble(), data.readDouble()));
		for (int count = data.readInt(), index = 0; index < count; index++)
			movement.behaviour(MovementBehaviour.valueOf(data.readUTF()));

		return movement.build();
	}
}
