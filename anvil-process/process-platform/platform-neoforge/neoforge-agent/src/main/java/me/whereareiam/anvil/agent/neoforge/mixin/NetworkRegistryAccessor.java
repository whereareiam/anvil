package me.whereareiam.anvil.agent.neoforge.mixin;

import net.minecraft.network.ConnectionProtocol;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Reads the payload channels NeoForge registered, which its network keeps to itself: NeoForge offers no way to list
 * them, and a client must claim each one at its version to join. Every supported release keeps them in this field; its
 * keys are resource locations until Minecraft 1.21.11 and identifiers from then on, so the accessor leaves the key's
 * type open, as the bytecode does.
 */
@Mixin(value = NetworkRegistry.class, remap = false)
public interface NetworkRegistryAccessor {
	/**
	 * Returns the registered channels by connection phase and id.
	 *
	 * @return NeoForge's own map of the registrations
	 */
	@Accessor("PAYLOAD_REGISTRATIONS")
	static Map<ConnectionProtocol, Map<?, PayloadRegistration<?>>> anvil$registrations() {
		throw new AssertionError("Replaced by the accessor");
	}
}
