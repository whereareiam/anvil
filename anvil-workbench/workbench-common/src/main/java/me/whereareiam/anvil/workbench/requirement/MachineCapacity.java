package me.whereareiam.anvil.workbench.requirement;

import com.sun.management.OperatingSystemMXBean;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.lang.management.ManagementFactory;

/**
 * Reads the capacity of the machine this JVM runs on, which a workbench in this JVM offers its processes and a
 * scenario's processor and memory requirements are checked against.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MachineCapacity {
	private static final long MEBIBYTE = 1024 * 1024;

	/**
	 * Returns the processors available to this JVM.
	 *
	 * @return processor count, at least one
	 */
	public static int processors() {
		return Runtime.getRuntime().availableProcessors();
	}

	/**
	 * Returns the machine's physical memory.
	 *
	 * @return memory in MiB
	 */
	public static int memoryMegabytes() {
		long total = ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();

		return (int) Math.min(Integer.MAX_VALUE, total / MEBIBYTE);
	}
}
