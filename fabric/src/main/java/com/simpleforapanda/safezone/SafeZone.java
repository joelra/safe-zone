package com.simpleforapanda.safezone;

import com.simpleforapanda.safezone.command.AdminCommand;
import com.simpleforapanda.safezone.command.PlayerCommand;
import com.simpleforapanda.safezone.listener.ProtectionListener;
import com.simpleforapanda.safezone.runtime.FabricRuntime;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SafeZone implements ModInitializer {
	public static final String MOD_ID = "safe-zone";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private final FabricRuntime runtime = FabricRuntime.create();

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Safe Zone");
		new ProtectionListener(this.runtime).register();
		new AdminCommand(this.runtime).register();
		new PlayerCommand(this.runtime).register();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> this.runtime.onPlayerJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> this.runtime.onPlayerLeave(handler.player));
		ServerTickEvents.END_SERVER_TICK.register(this.runtime::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(this.runtime::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> this.runtime.save());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> this.runtime.stop());
	}
}
