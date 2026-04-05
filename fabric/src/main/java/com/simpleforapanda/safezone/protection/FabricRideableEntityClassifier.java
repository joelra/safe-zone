package com.simpleforapanda.safezone.protection;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemSteerable;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.vehicle.VehicleEntity;

public final class FabricRideableEntityClassifier implements RideableEntityClassifier<Entity> {
	@Override
	public boolean isRideable(Entity entity) {
		return entity instanceof VehicleEntity
			|| entity instanceof AbstractHorse
			|| entity instanceof ItemSteerable;
	}
}
