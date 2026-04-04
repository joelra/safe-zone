package com.simpleforapanda.safezone.manager;

import com.simpleforapanda.safezone.data.PermissionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.Explosion;

public final class ClaimEntityProtection {
	private ClaimEntityProtection() {
	}

	public static boolean shouldBlockExplosionDamage(Entity entity, DamageSource damageSource) {
		if (!damageSource.is(DamageTypeTags.IS_EXPLOSION)) {
			return false;
		}

		if (entity instanceof ServerPlayer player) {
			return hasTrustedClaimAccess(player);
		}

		return isProtectedByClaim(entity);
	}

	public static boolean shouldIgnoreExplosion(Entity entity, Explosion explosion) {
		return isProtectedByClaim(entity);
	}

	public static boolean shouldBlockExplosionMovement(Entity entity) {
		if (entity instanceof ServerPlayer player) {
			return hasTrustedClaimAccess(player);
		}

		return isProtectedByClaim(entity)
			&& (entity instanceof BlockAttachedEntity || entity instanceof VehicleEntity || entity instanceof ArmorStand);
	}

	private static BlockPos getProtectionPos(Entity entity) {
		if (entity instanceof BlockAttachedEntity attachedEntity) {
			return attachedEntity.getPos();
		}

		return entity.blockPosition();
	}

	private static boolean isProtectedByClaim(Entity entity) {
		ClaimManager claimManager = ClaimManager.getInstance();
		return claimManager.isLoaded() && claimManager.getClaimAt(getProtectionPos(entity)).isPresent();
	}

	private static boolean hasTrustedClaimAccess(ServerPlayer player) {
		ClaimManager claimManager = ClaimManager.getInstance();
		if (!claimManager.isLoaded()) {
			return false;
		}

		return claimManager.canBuild(player, player.blockPosition()) != PermissionResult.DENIED;
	}
}
