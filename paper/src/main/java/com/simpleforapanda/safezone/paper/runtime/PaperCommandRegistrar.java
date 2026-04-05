package com.simpleforapanda.safezone.paper.runtime;

import com.simpleforapanda.safezone.paper.command.ClaimCommand;
import com.simpleforapanda.safezone.paper.command.SafeZoneAdminCommand;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

final class PaperCommandRegistrar {
	private final JavaPlugin plugin;

	PaperCommandRegistrar(JavaPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
	}

	void register(PaperRuntime runtime) {
		ClaimCommand claimCommand = new ClaimCommand(runtime);
		SafeZoneAdminCommand adminCommand = new SafeZoneAdminCommand(runtime);
		register("claim", claimCommand, claimCommand);
		register("sz", adminCommand, adminCommand);
	}

	private void register(String commandName, CommandExecutor executor, TabCompleter tabCompleter) {
		PluginCommand command = this.plugin.getCommand(commandName);
		if (command == null) {
			throw new IllegalStateException("Missing command definition in plugin.yml: " + commandName);
		}
		command.setExecutor(executor);
		command.setTabCompleter(tabCompleter);
	}
}
