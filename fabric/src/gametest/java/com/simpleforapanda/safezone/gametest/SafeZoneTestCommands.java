package com.simpleforapanda.safezone.gametest;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Test-mod-only conveniences grafted onto vanilla's {@code /test} command:
 *
 * <ul>
 *   <li>{@code /test runall} — runs the whole Safe Zone suite (shorthand for
 *       {@code /test run safe-zone-test:*}).</li>
 *   <li>{@code /test clearmockplayers} — removes lingering {@code test-mock-player}
 *       entities; vanilla's {@code /test clearall} intentionally skips player
 *       entities, so mock players from failed or interrupted tests stay behind.</li>
 * </ul>
 *
 * Lives in the gametest source set — never shipped in release jars.
 */
public final class SafeZoneTestCommands implements ModInitializer {
	/** The profile name vanilla's GameTestHelper gives every mock player. */
	static final String MOCK_PLAYER_NAME = "test-mock-player";

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var testNode = dispatcher.getRoot().getChild("test");
		if (testNode == null) {
			return;
		}

		testNode.addChild(Commands.literal("runall")
			.executes(context -> {
				CommandSourceStack source = context.getSource();
				source.getServer().getCommands().performPrefixedCommand(source, "test run safe-zone-test:*");
				return 1;
			})
			.build());

		testNode.addChild(Commands.literal("clearmockplayers")
			.executes(context -> {
				CommandSourceStack source = context.getSource();
				List<ServerPlayer> mocks = source.getServer().getPlayerList().getPlayers().stream()
					.filter(player -> MOCK_PLAYER_NAME.equals(player.getName().getString()))
					.toList();
				for (ServerPlayer mock : mocks) {
					source.getServer().getPlayerList().remove(mock);
				}
				source.sendSuccess(() -> Component.literal("Removed " + mocks.size() + " mock player(s)"), false);
				return mocks.size();
			})
			.build());
	}
}
