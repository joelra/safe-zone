package com.simpleforapanda.safezone.mixin;

import com.simpleforapanda.safezone.manager.ClaimEntityProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockAttachedEntity.class)
public abstract class BlockAttachedEntityMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("DataFlowIssue")
	private void safezone$protectClaimDecorationExplosion(ServerLevel level, DamageSource damageSource, float damage,
		CallbackInfoReturnable<Boolean> cir) {
		if (ClaimEntityProtection.shouldBlockExplosionDamage((BlockAttachedEntity) (Object) this, damageSource)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "ignoreExplosion", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("DataFlowIssue")
	private void safezone$ignoreClaimExplosion(Explosion explosion, CallbackInfoReturnable<Boolean> cir) {
		if (ClaimEntityProtection.shouldIgnoreExplosion((BlockAttachedEntity) (Object) this, explosion)) {
			cir.setReturnValue(true);
		}
	}
}
