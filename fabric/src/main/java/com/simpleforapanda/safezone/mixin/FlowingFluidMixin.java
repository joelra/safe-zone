package com.simpleforapanda.safezone.mixin;

import com.simpleforapanda.safezone.data.ClaimData;
import com.simpleforapanda.safezone.manager.ClaimManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
	@Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
	private void safezone$blockCrossClaimFluidSpread(LevelAccessor level, BlockPos targetPos, BlockState targetState,
		Direction direction, FluidState fluidState, CallbackInfo ci) {
		ClaimManager claimManager = ClaimManager.getInstance();
		if (!claimManager.isLoaded()) {
			return;
		}

		Optional<ClaimData> targetClaim = claimManager.getClaimAt(targetPos);
		if (targetClaim.isEmpty()) {
			return;
		}

		BlockPos sourcePos = targetPos.relative(direction.getOpposite());
		Optional<ClaimData> sourceClaim = claimManager.getClaimAt(sourcePos);
		if (sourceClaim.isPresent() && sourceClaim.get().claimId.equals(targetClaim.get().claimId)) {
			return;
		}

		ci.cancel();
	}
}
