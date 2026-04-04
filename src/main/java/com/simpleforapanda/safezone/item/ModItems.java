package com.simpleforapanda.safezone.item;

import com.simpleforapanda.safezone.manager.ClaimManager;
import net.minecraft.world.item.ItemStack;

public final class ModItems {
	private ModItems() {
	}

	public static void initialize() {
	}

	public static boolean isClaimWand(ItemStack stack) {
		return stack.is(ClaimManager.getInstance().getGameplayConfig().claimWandItem());
	}

	public static ItemStack createClaimWandStack() {
		return ClaimManager.getInstance().getGameplayConfig().createClaimWandStack();
	}

	public static String claimWandName() {
		return ClaimManager.getInstance().getGameplayConfig().claimWandName();
	}
}
