package com.simpleforapanda.safezone.paper.runtime;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Logger;

record PaperPluginContext(JavaPlugin plugin, Logger logger, PaperPathLayout paths) {
	static PaperPluginContext create(JavaPlugin plugin) {
		Objects.requireNonNull(plugin, "plugin");
		return new PaperPluginContext(plugin, plugin.getLogger(), PaperPathLayout.fromPlugin(plugin));
	}
}
