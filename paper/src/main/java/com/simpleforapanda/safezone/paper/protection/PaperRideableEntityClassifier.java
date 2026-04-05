package com.simpleforapanda.safezone.paper.protection;

import com.simpleforapanda.safezone.protection.RideableEntityClassifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Steerable;
import org.bukkit.entity.Vehicle;

public final class PaperRideableEntityClassifier implements RideableEntityClassifier<Entity> {
	@Override
	public boolean isRideable(Entity entity) {
		return entity instanceof Vehicle || entity instanceof Steerable;
	}
}
