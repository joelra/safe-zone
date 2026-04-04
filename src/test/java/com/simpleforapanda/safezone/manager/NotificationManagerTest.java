package com.simpleforapanda.safezone.manager;

import com.google.gson.reflect.TypeToken;
import com.simpleforapanda.safezone.data.AdminNotification;
import com.simpleforapanda.safezone.data.GameplayConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationManagerTest {
	private static final Type NOTIFICATION_LIST_TYPE = new TypeToken<List<AdminNotification>>() { }.getType();

	private final AtomicLong now = new AtomicLong(TimeUnit.DAYS.toMillis(40));
	private Path testDirectory;

	@AfterEach
	void tearDown() throws IOException {
		if (this.testDirectory == null || Files.notExists(this.testDirectory)) {
			return;
		}

		try (Stream<Path> paths = Files.walk(this.testDirectory)) {
			paths.sorted((left, right) -> right.getNameCount() - left.getNameCount())
				.forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (IOException exception) {
						throw new RuntimeException(exception);
					}
				});
		}
	}

	@Test
	void consumePendingNotificationsRemovesDeliveredEntriesAndPersistsJson() throws IOException {
		NotificationManager manager = createManager(new GameplayConfig());
		Path dataDirectory = createTestDirectory();

		manager.load(dataDirectory, false);
		manager.queueMessage("owner-1", "Owner", "Admin", "Hello from admin");

		List<AdminNotification> deliveredNotifications = manager.consumePendingNotifications("owner-1");

		assertEquals(1, deliveredNotifications.size());
		assertEquals(0, manager.pendingNotificationCount());
		assertEquals(List.of(), readNotifications(dataDirectory.resolve("notifications.json")));
		assertEquals("[]", Files.readString(dataDirectory.resolve("notifications.json"), StandardCharsets.UTF_8));
	}

	@Test
	void loadPrunesExpiredNotificationsAndRewritesJson() throws IOException {
		Path dataDirectory = createTestDirectory();
		Path notificationsFile = dataDirectory.resolve("notifications.json");
		List<AdminNotification> storedNotifications = List.of(
			new AdminNotification("owner-expired", "Expired", "Admin", "Too old", this.now.get() - TimeUnit.DAYS.toMillis(31)),
			new AdminNotification("owner-fresh", "Fresh", "Admin", "Still pending", this.now.get() - TimeUnit.DAYS.toMillis(5))
		);
		PersistentStateHelper.writeJsonAtomically(notificationsFile, storedNotifications, NOTIFICATION_LIST_TYPE,
			"test notifications", false, PersistentStateHelper.JsonOutput.COMPACT);

		NotificationManager manager = createManager(new GameplayConfig());
		manager.load(dataDirectory, false);

		assertEquals(1, manager.pendingNotificationCount());
		List<AdminNotification> persistedNotifications = readNotifications(notificationsFile);
		assertEquals(1, persistedNotifications.size());
		assertEquals("owner-fresh", persistedNotifications.getFirst().ownerUuid);
		assertTrue(persistedNotifications.getFirst().message.contains("Still pending"));
		assertTrue(Files.readString(notificationsFile, StandardCharsets.UTF_8).startsWith("[{\"ownerUuid\":\"owner-fresh\""));
	}

	@Test
	void queueMessageSkipsStorageWhenNotificationsAreDisabled() throws IOException {
		GameplayConfig gameplayConfig = new GameplayConfig();
		gameplayConfig.notificationsEnabled = false;
		NotificationManager manager = createManager(gameplayConfig);
		Path dataDirectory = createTestDirectory();

		manager.load(dataDirectory, false);
		manager.queueMessage("owner-1", "Owner", "Admin", "Hello from admin");

		assertEquals(0, manager.pendingNotificationCount());
		assertEquals(List.of(), readNotifications(dataDirectory.resolve("notifications.json")));
	}

	@Test
	void loadClearsStoredNotificationsWhenNotificationsAreDisabled() throws IOException {
		Path dataDirectory = createTestDirectory();
		Path notificationsFile = dataDirectory.resolve("notifications.json");
		List<AdminNotification> storedNotifications = List.of(
			new AdminNotification("owner-1", "Owner", "Admin", "Queued earlier", this.now.get() - TimeUnit.DAYS.toMillis(1))
		);
		PersistentStateHelper.writeJsonAtomically(notificationsFile, storedNotifications, NOTIFICATION_LIST_TYPE,
			"test notifications", false, PersistentStateHelper.JsonOutput.COMPACT);

		GameplayConfig gameplayConfig = new GameplayConfig();
		gameplayConfig.notificationsEnabled = false;
		NotificationManager manager = createManager(gameplayConfig);

		manager.load(dataDirectory, false);

		assertEquals(0, manager.pendingNotificationCount());
		assertEquals(List.of(), readNotifications(notificationsFile));
		assertEquals("[]", Files.readString(notificationsFile, StandardCharsets.UTF_8));
	}

	@Test
	void purgeAllNotificationsClearsPersistedJson() throws IOException {
		NotificationManager manager = createManager(new GameplayConfig());
		Path dataDirectory = createTestDirectory();

		manager.load(dataDirectory, false);
		manager.queueMessage("owner-1", "Owner", "Admin", "First");
		manager.queueMessage("owner-2", "Owner Two", "Admin", "Second");

		assertEquals(2, manager.purgeAllNotifications());
		assertEquals(0, manager.pendingNotificationCount());
		assertEquals(List.of(), readNotifications(dataDirectory.resolve("notifications.json")));
		assertEquals("[]", Files.readString(dataDirectory.resolve("notifications.json"), StandardCharsets.UTF_8));
	}

	private List<AdminNotification> readNotifications(Path filePath) {
		return PersistentStateHelper.readJson(filePath, NOTIFICATION_LIST_TYPE, List.of(), "test notifications", false);
	}

	private Path createTestDirectory() throws IOException {
		this.testDirectory = Path.of("build", "test-work", getClass().getSimpleName(), UUID.randomUUID().toString());
		Files.createDirectories(this.testDirectory);
		return this.testDirectory;
	}

	private NotificationManager createManager(GameplayConfig gameplayConfig) {
		return new NotificationManager(this.now::get, gameplayConfig::copy);
	}
}
