package com.simpleforapanda.safezone.manager;

import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentStateHelperTest {
	private static final Type MAP_TYPE = new TypeToken<Map<String, Integer>>() { }.getType();

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
	void writeJsonAtomicallyCreatesBackupOnSecondSave() throws IOException {
		Path filePath = createTestFilePath("claims.json");

		PersistentStateHelper.writeJsonAtomically(filePath, Map.of("value", 1), MAP_TYPE, "test data", true,
			PersistentStateHelper.JsonOutput.COMPACT);
		assertFalse(Files.exists(PersistentStateHelper.backupPathFor(filePath)));

		PersistentStateHelper.writeJsonAtomically(filePath, Map.of("value", 2), MAP_TYPE, "test data", true,
			PersistentStateHelper.JsonOutput.COMPACT);

		assertTrue(Files.exists(PersistentStateHelper.backupPathFor(filePath)));
		assertEquals(Map.of("value", 2),
			PersistentStateHelper.readJson(filePath, MAP_TYPE, Map.of(), "test data", true));
		assertEquals(Map.of("value", 1),
			PersistentStateHelper.readJson(PersistentStateHelper.backupPathFor(filePath), MAP_TYPE, Map.of(), "test backup", false));
	}

	@Test
	void readJsonRecoversFromBackupAndRestoresPrimaryFile() throws IOException {
		Path filePath = createTestFilePath("notifications.json");

		PersistentStateHelper.writeJsonAtomically(filePath, Map.of("value", 1), MAP_TYPE, "test data", true,
			PersistentStateHelper.JsonOutput.COMPACT);
		PersistentStateHelper.writeJsonAtomically(filePath, Map.of("value", 2), MAP_TYPE, "test data", true,
			PersistentStateHelper.JsonOutput.COMPACT);
		Files.writeString(filePath, "{not valid json", StandardCharsets.UTF_8);

		Map<String, Integer> recovered = PersistentStateHelper.readJson(filePath, MAP_TYPE, Map.of(), "test data", true);

		assertEquals(Map.of("value", 1), recovered);
		assertEquals(Map.of("value", 1),
			PersistentStateHelper.readJson(filePath, MAP_TYPE, Map.of(), "test data", false));
	}

	@Test
	void writeJsonAtomicallyCompactsRuntimeJson() throws IOException {
		Path filePath = createTestFilePath("player_limits.json");

		PersistentStateHelper.writeJsonAtomically(filePath, Map.of("value", 2), MAP_TYPE, "test data", false,
			PersistentStateHelper.JsonOutput.COMPACT);

		assertEquals("{\"value\":2}", Files.readString(filePath, StandardCharsets.UTF_8));
	}

	@Test
	void writeJsonAtomicallyPrettyPrintsConfigJson() throws IOException {
		Path filePath = createTestFilePath("config.json");

		PersistentStateHelper.writeJsonAtomically(filePath, Map.of("value", 2), MAP_TYPE, "test data", false,
			PersistentStateHelper.JsonOutput.PRETTY);

		assertEquals("{\n  \"value\": 2\n}", Files.readString(filePath, StandardCharsets.UTF_8));
	}

	private Path createTestFilePath(String fileName) throws IOException {
		this.testDirectory = Path.of("build", "test-work", getClass().getSimpleName(), UUID.randomUUID().toString());
		Files.createDirectories(this.testDirectory);
		return this.testDirectory.resolve(fileName);
	}
}
