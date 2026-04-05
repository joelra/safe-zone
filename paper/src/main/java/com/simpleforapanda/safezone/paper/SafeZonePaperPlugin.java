package com.simpleforapanda.safezone.paper;

import com.simpleforapanda.safezone.paper.runtime.PaperRuntime;
import org.bukkit.plugin.java.JavaPlugin;

public final class SafeZonePaperPlugin extends JavaPlugin {
	private PaperRuntime runtime;

	@Override
	public void onEnable() {
		try {
			this.runtime = PaperRuntime.create(this);
			this.runtime.start();
		} catch (RuntimeException exception) {
			getLogger().severe("Safe Zone Paper failed to start: " + exception.getMessage());
			getLogger().log(java.util.logging.Level.SEVERE, "Safe Zone Paper startup failure", exception);
			this.runtime = null;
			getServer().getPluginManager().disablePlugin(this);
		}
	}

	@Override
	public void onDisable() {
		if (this.runtime == null) {
			return;
		}
		this.runtime.stop();
		this.runtime = null;
	}
}
