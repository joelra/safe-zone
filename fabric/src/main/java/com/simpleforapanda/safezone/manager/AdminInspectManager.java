package com.simpleforapanda.safezone.manager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminInspectManager {
	private static final Set<UUID> INSPECTING_PLAYERS = ConcurrentHashMap.newKeySet();

	private AdminInspectManager() {
	}

	public static boolean toggle(UUID playerId) {
		if (INSPECTING_PLAYERS.remove(playerId)) {
			return false;
		}

		INSPECTING_PLAYERS.add(playerId);
		return true;
	}

	public static boolean isInspecting(UUID playerId) {
		return INSPECTING_PLAYERS.contains(playerId);
	}

	public static void clear() {
		INSPECTING_PLAYERS.clear();
	}
}
