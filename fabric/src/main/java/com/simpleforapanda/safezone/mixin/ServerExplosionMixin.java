package com.simpleforapanda.safezone.mixin;

import com.simpleforapanda.safezone.manager.ClaimEntityProtection;
import com.simpleforapanda.safezone.manager.ClaimManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
	@Inject(method = "interactWithBlocks", at = @At("HEAD"))
	private void safezone$filterClaimBlocks(List<BlockPos> explodedPositions, CallbackInfo ci) {
		if (!ClaimManager.getInstance().isLoaded()) {
			return;
		}

		explodedPositions.removeIf(pos -> ClaimManager.getInstance().getClaimAt(pos).isPresent());
	}

	@Inject(method = "createFire", at = @At("HEAD"))
	private void safezone$filterClaimFire(List<BlockPos> explodedPositions, CallbackInfo ci) {
		if (!ClaimManager.getInstance().isLoaded()) {
			return;
		}

		explodedPositions.removeIf(pos -> ClaimManager.getInstance().getClaimAt(pos).isPresent());
	}

	@Redirect(
		method = "hurtEntities",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
		)
	)
	private boolean safezone$protectClaimExplosionDamage(Entity entity, ServerLevel level, DamageSource damageSource, float damage) {
		if (ClaimEntityProtection.shouldBlockExplosionDamage(entity, damageSource)) {
			return false;
		}

		return entity.hurtServer(level, damageSource, damage);
	}

	@Redirect(
		method = "hurtEntities",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;push(Lnet/minecraft/world/phys/Vec3;)V"
		)
	)
	private void safezone$blockClaimExplosionMovement(Entity entity, Vec3 pushVector) {
		if (ClaimEntityProtection.shouldBlockExplosionMovement(entity)) {
			return;
		}

		entity.push(pushVector);
	}

	@Redirect(
		method = "hurtEntities",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
		)
	)
	private Object safezone$blockClaimPlayerExplosionKnockback(Map<Player, Vec3> hitPlayers, Object player, Object pushVector) {
		if (!(player instanceof Player targetPlayer)) {
			throw new IllegalStateException("Expected Player in explosion knockback redirect but got " + player);
		}
		if (!(pushVector instanceof Vec3 knockbackVector)) {
			throw new IllegalStateException("Expected Vec3 in explosion knockback redirect but got " + pushVector);
		}
		if (ClaimEntityProtection.shouldBlockExplosionMovement(targetPlayer)) {
			return null;
		}

		return hitPlayers.put(targetPlayer, knockbackVector);
	}
}
