package com.simpleforapanda.safezone.data;

public class SafeZoneConfig {
	public GameplayConfig gameplay = new GameplayConfig();
	public OpsSettings ops = new OpsSettings();

	public void ensureDefaults() {
		if (this.gameplay == null) {
			this.gameplay = new GameplayConfig();
		} else {
			this.gameplay.ensureDefaults();
		}
		if (this.ops == null) {
			this.ops = new OpsSettings();
		} else {
			this.ops.ensureDefaults();
		}
	}

	public SafeZoneConfig copy() {
		SafeZoneConfig copy = new SafeZoneConfig();
		copy.gameplay = this.gameplay.copy();
		copy.ops = this.ops.copy();
		return copy;
	}
}
