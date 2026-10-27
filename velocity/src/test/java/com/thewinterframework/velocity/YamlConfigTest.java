package com.thewinterframework.velocity;

import com.thewinterframework.velocity.yaml.FileName;
import com.thewinterframework.velocity.yaml.FileNameImpl;
import com.thewinterframework.velocity.yaml.YamlConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class YamlConfigTest {
	@TempDir
	Path directory;

	@Test
	void roundTripsNestedValuesAndReloadsChanges() throws Exception {
		final var path = directory.resolve("nested/config.yml");
		final var config = YamlConfig.create(path);
		config.load();
		config.set("feature.enabled", true);
		config.set("feature.limit", 42);
		config.set("message", "¡Hola, invierno! ❄");
		config.set("servers", List.of("lobby", "survival"));
		config.set("section", Map.of("key", "value"));
		config.set("section.key", "updated");
		config.save();
		final var reloaded = YamlConfig.create(path);
		reloaded.load();
		assertTrue(reloaded.getBoolean("feature.enabled"));
		assertEquals(42, reloaded.getInt("feature.limit"));
		assertEquals(42L, reloaded.getLong("feature.limit"));
		assertEquals(42.0, reloaded.getDouble("feature.limit"));
		assertEquals("¡Hola, invierno! ❄", reloaded.getString("message"));
		assertEquals(List.of("lobby", "survival"), reloaded.getStringList("servers"));
		assertEquals("updated", reloaded.getString("section.key"));
		assertEquals("fallback", reloaded.getString("missing", "fallback"));
		reloaded.set("section.key", null);
		assertFalse(reloaded.contains("section.key"));
		Files.writeString(path, "replacement: true\n");
		reloaded.load();
		assertFalse(reloaded.contains("feature.limit"));
		assertTrue(reloaded.getBoolean("replacement"));
	}

	@Test
	void rejectsMalformedAndNonMappingYamlWithoutReplacingTheCurrentValues() throws Exception {
		final var path = directory.resolve("config.yml");
		final var config = YamlConfig.create(path);
		config.set("valid", true);
		Files.writeString(path, "broken: [\n");
		assertThrows(IllegalStateException.class, config::load);
		assertTrue(config.getBoolean("valid"));
		Files.writeString(path, "- list\n- root\n");
		assertThrows(IllegalStateException.class, config::load);
	}

	@Test
	void loadsScalarKeysAsStrings() throws Exception {
		final var path = directory.resolve("keys.yml");
		final var config = YamlConfig.create(path);
		Files.writeString(path, "1: first\n2: second\nsection:\n  3: third\n");
		config.load();
		assertEquals("first", config.getString("1"));
		assertEquals("second", config.getString("2"));
		assertEquals("third", config.getString("section.3"));
		assertTrue(config.getKeys().containsAll(List.of("1", "2", "section")));
		config.save();
		final var reloaded = YamlConfig.create(path);
		reloaded.load();
		assertEquals("first", reloaded.getString("1"));
		assertEquals("third", reloaded.getString("section.3"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void returnsMutableListsThatDoNotChangeTheConfiguration() throws Exception {
		final var path = directory.resolve("lists.yml");
		final var config = YamlConfig.create(path);
		Files.writeString(path, "servers:\n  - lobby\n");
		config.load();
		final var missing = (List<Object>) config.getList("missing");
		final var servers = (List<Object>) config.getList("servers");
		assertDoesNotThrow(() -> {
			missing.add("lobby");
			servers.add("survival");
			config.getStringList("missing").add("lobby");
		});
		assertTrue(missing.contains("lobby"));
		assertEquals(List.of("lobby"), config.getStringList("servers"), "the configuration must not expose its own list");
	}

	@Test
	void generatedQualifierMatchesTheRuntimeAnnotation() throws Exception {
		final var annotation = getClass().getDeclaredField("qualified").getAnnotation(FileName.class);
		final var generated = new FileNameImpl("config");
		assertEquals(annotation, generated);
		assertEquals(generated, annotation);
		assertEquals(annotation.hashCode(), generated.hashCode());
	}

	@FileName("config")
	private YamlConfig qualified;
}
