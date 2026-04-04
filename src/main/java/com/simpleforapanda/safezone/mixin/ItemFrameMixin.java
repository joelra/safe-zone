package com.simpleforapanda.safezone.mixin;

import com.simpleforapanda.safezone.manager.ClaimEntityProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemFrame.class)
public abstract class ItemFrameMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("DataFlowIssue")
	private void safezone$protectClaimItemFrameExplosion(ServerLevel level, DamageSource damageSource, float damage,
		CallbackInfoReturnable<Boolean> cir) {
		if (ClaimEntityProtection.shouldBlockExplosionDamage((ItemFrame) (Object) this, damageSource)) {
			cir.setReturnValue(false);
		}
	}
}
