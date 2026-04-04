package com.simpleforapanda.safezone.mixin;

import com.simpleforapanda.safezone.manager.ClaimEntityProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VehicleEntity.class)
public abstract class VehicleEntityMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("DataFlowIssue")
	private void safezone$protectClaimVehicleExplosion(ServerLevel level, DamageSource damageSource, float damage,
		CallbackInfoReturnable<Boolean> cir) {
		if (ClaimEntityProtection.shouldBlockExplosionDamage((VehicleEntity) (Object) this, damageSource)) {
			cir.setReturnValue(false);
		}
	}
}
