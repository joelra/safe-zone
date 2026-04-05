package com.simpleforapanda.safezone.item;

import com.simpleforapanda.safezone.data.GameplayConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ModItems {
	private ModItems() {
	}

	public static void normalizeGameplayConfig(GameplayConfig gameplayConfig) {
		gameplayConfig.claimWandItemId = normalizeClaimWandItemId(gameplayConfig.claimWandItemId);
	}

	public static boolean isClaimWand(ItemStack stack, GameplayConfig gameplayConfig) {
		return stack.is(resolveClaimWandItem(gameplayConfig.claimWandItemId));
	}

	public static ItemStack createClaimWandStack(GameplayConfig gameplayConfig) {
		return new ItemStack(resolveClaimWandItem(gameplayConfig.claimWandItemId));
	}

	public static String claimWandName(GameplayConfig gameplayConfig) {
		return createClaimWandStack(gameplayConfig).getHoverName().getString();
	}

	public static String normalizeClaimWandItemId(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return GameplayConfig.DEFAULT_CLAIM_WAND_ITEM_ID;
		}

		return BuiltInRegistries.ITEM.stream()
			.anyMatch(item -> BuiltInRegistries.ITEM.getKey(item).toString().equals(itemId))
			? itemId
			: GameplayConfig.DEFAULT_CLAIM_WAND_ITEM_ID;
	}

	private static Item resolveClaimWandItem(String itemId) {
		String resolvedItemId = normalizeClaimWandItemId(itemId);
		return BuiltInRegistries.ITEM.stream()
			.filter(item -> BuiltInRegistries.ITEM.getKey(item).toString().equals(resolvedItemId))
			.findFirst()
			.orElse(Items.GOLDEN_HOE);
	}
}
