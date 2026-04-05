package com.simpleforapanda.safezone.mixin;

import com.simpleforapanda.safezone.manager.ClaimManager;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
	@Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
	private void safezone$preventClaimBurn(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
		if (!level.isClientSide() && ClaimManager.getInstance().isLoaded() && ClaimManager.getInstance().getClaimAt(pos).isPresent()) {
			ci.cancel();
		}
	}
}
