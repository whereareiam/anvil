package me.whereareiam.anvil.testkit.physics.harness;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * Allocates the client objects whose constructors cannot run headless, such as {@code Minecraft}, whose constructor
 * opens a window, or a renderer, which needs a graphics device. The object gets no constructor run, so every field
 * holds its default value; the harness then gives it the state the movement and the handlers it calls read, through
 * the members its family's {@code accesstransformer.cfg} widens.
 *
 * <p>This is the harness's only reflection: every member of the client it reaches is plain, compiler-checked Java.</p>
 */
public final class Unconstructed {
	private static final Unsafe UNSAFE = unsafe();

	private Unconstructed() {
	}

	/**
	 * Allocates {@code type} without running a constructor.
	 *
	 * @param type the class to allocate, which may be a harness subclass that replaces what draws on the screen
	 * @return the new instance
	 */
	public static <T> T allocate(Class<T> type) {
		try {
			return type.cast(UNSAFE.allocateInstance(type));
		} catch (InstantiationException exception) {
			throw new IllegalStateException("Cannot allocate " + type.getName(), exception);
		}
	}

	private static Unsafe unsafe() {
		try {
			Field field = Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			return (Unsafe) field.get(null);
		} catch (ReflectiveOperationException exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}
}
