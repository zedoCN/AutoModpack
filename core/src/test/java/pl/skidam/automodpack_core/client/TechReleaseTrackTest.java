package pl.skidam.automodpack_core.client;

import static org.junit.jupiter.api.Assertions.*;
import static pl.skidam.automodpack_core.protocol.NetUtils.USER_AGENT;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pl.skidam.automodpack_core.Constants;
import pl.skidam.automodpack_core.config.ClientConfigJsons;
import pl.skidam.automodpack_core.config.ConnectionJsons;
import pl.skidam.automodpack_core.config.ModpackJsons;
import pl.skidam.automodpack_core.modpack.generation.PackDocument;
import pl.skidam.automodpack_core.modpack.generation.TestPacks;
import pl.skidam.automodpack_core.modpack.group.GroupManifestValidator;
import pl.skidam.automodpack_core.modpack.group.SelectionIntent;
import pl.skidam.automodpack_core.protocol.ModpackConnectionMode;
import pl.skidam.automodpack_core.storage.TestDataRoot;
import pl.skidam.automodpack_core.update.ClientStorage;
import pl.skidam.automodpack_core.utils.HashUtils;

class TechReleaseTrackTest {
	private final ClientConfigJsons.ClientConfigFieldsV3 original = Constants.clientConfig;
	private static final String MARKED = USER_AGENT + " " + TechReleaseTrack.MARKER;
	@TempDir
	Path temporary;

	@BeforeEach
	void trustTech() {
		Constants.clientConfig = new ClientConfigJsons.ClientConfigFieldsV3();
		Constants.clientConfig.confirmUnverifiedMods = false;
	}

	@AfterEach
	void restoreConfig() {
		Constants.clientConfig = original;
	}

	@Test
	void freshAndVerifiedRequiredOnlyInstancesUseNewTrackWithoutTouchingFiles() throws Exception {
		ClientStorage fresh = storage("fresh");
		Files.createDirectories(fresh.gameDirectory().resolve("mods"));
		Files.writeString(fresh.gameDirectory().resolve("mods/personal.jar"), "personal bytes");
		Map<String, String> before = snapshot();
		assertEquals(MARKED, TechReleaseTrack.userAgent(fresh, tech()));
		assertEquals(before, snapshot());
		ClientStorage current = storage("required");
		stage(current, "l3ic0dd", "mods/required.jar", true);
		before = snapshot();
		assertEquals(MARKED, TechReleaseTrack.userAgent(current, tech()));
		assertEquals(before, snapshot());
	}

	@Test
	void eachPublishedAuxiliaryPathKeepsOldTrackAcrossCaseAndPlatforms() throws Exception {
		List<String> paths = List.of(
				"config/chatimage-playback.json", "config/chatimageconfig.json", "config/simplesorter.json",
				"mods/ChatImage-1.4.7-port.7-neoforge+26.2.jar", "mods/Clumps-neoforge-26.2-26.2.1.jar",
				"mods/DistantHorizons-3.3.3-26.2-fabric-neoforge.jar", "mods/Jade-mc26.2-NeoForge-26.2.10.jar",
				"mods/RollingGate-neoforge-26.2-1.1.0+build.70.jar", "mods/ScalableLux-neoforge-0.3.0-alpha.0.16-all.jar",
				"mods/SimpleSorterR-neoforge-26.2-3.6.1.jar", "mods/appleskin-neoforge-mc26.2-3.0.10.jar",
				"mods/c2me-neoforge-mc26.2-0.4.2-alpha.0.96.jar", "mods/cloth-config-26.2.155.jar",
				"mods/jei-26.2-neoforge-30.39.0.232.jar", "mods/journeymap-neoforge-26.2-6.0.9.jar",
				"mods/lithium-neoforge-0.25.3+mc26.2.jar", "mods/mezz_config-26.2-neoforge-0.6.6-standalone.jar",
				"mods/vanillapings-26.2-neoforge-1.11.1-camera.3-neoforge.1.jar");
		for (int i = 0; i < paths.size(); i++) {
			ClientStorage old = storage("old" + i);
			stage(old, "l3ic0dd", paths.get(i), true);
			Map<String, String> before = snapshot();
			assertEquals(USER_AGENT, TechReleaseTrack.userAgent(old, tech()), paths.get(i));
			assertEquals(before, snapshot());
		}
	}

	@Test
	void damagedLegacyAndInterruptedStatesFailClosedWithoutRepairingEvidence() throws Exception {
		for (String kind : List.of("pointer", "journal", "policy", "legacy", "orphan", "incoming", "backup", "transaction")) {
			ClientStorage storage = storage(kind);
			PackDocument document = stage(storage, "l3ic0dd", "mods/required.jar", !kind.equals("legacy"));
			assertEquals(kind.equals("legacy") ? USER_AGENT : MARKED, TechReleaseTrack.userAgent(storage, tech()));
			switch (kind) {
				case "pointer" -> Files.writeString(storage.stateFile(), "broken pointer");
				case "journal" -> Files.writeString(storage.historyJournalFile("l3ic0dd"), "broken journal");
				case "policy" -> Files.writeString(storage.objectFile(document.policySha1()), "broken policy");
				case "orphan" -> {
					Files.delete(storage.stateFile());
					Files.createDirectories(storage.activeDirectory());
				}
				case "incoming" -> Files.createDirectories(storage.incomingDirectory());
				case "backup" -> Files.createDirectories(storage.backupDirectory());
				case "transaction" -> Files.writeString(storage.transactionFile(), "unfinished transaction");
				default -> {
				}
			}
			Map<String, String> before = snapshot();
			assertEquals(USER_AGENT, TechReleaseTrack.userAgent(storage, tech()), kind);
			assertEquals(before, snapshot());
		}
	}

	@Test
	void trackSelectionUsesPackEndpointAndConfirmationSwitch() throws Exception {
		ClientStorage fresh = storage("fresh");
		assertEquals(USER_AGENT + " " + TechReleaseTrack.MARKER, TechReleaseTrack.userAgent(fresh, connection("localhost", "status.xiang233.top", 443)));
		assertEquals(USER_AGENT + " " + TechReleaseTrack.MARKER, TechReleaseTrack.userAgent(fresh, connection("other.example", "status.xiang233.top", 443)));
		assertEquals(USER_AGENT, TechReleaseTrack.userAgent(fresh, connection("tech-ali.zedo.top", "other.example", 443)));
		assertEquals(USER_AGENT, TechReleaseTrack.userAgent(fresh, connection("tech-ali.zedo.top", "status.xiang233.top", 444)));
		ClientStorage other = storage("other");
		stage(other, "abc1234", "mods/required.jar", true);
		assertEquals(USER_AGENT, TechReleaseTrack.userAgent(other, tech()));
		Constants.clientConfig.confirmUnverifiedMods = true;
		assertEquals(USER_AGENT, TechReleaseTrack.userAgent(fresh, tech()));
	}

	private ClientStorage storage(String name) throws Exception {
		return TestDataRoot.open(temporary.resolve(name).resolve("game"), temporary.resolve(name).resolve("data"));
	}

	private static ConnectionJsons.ConnectionInfo tech() {
		return connection("tech-ali.zedo.top", "status.xiang233.top", 443);
	}

	private static ConnectionJsons.ConnectionInfo connection(String origin, String endpoint, int port) {
		return new ConnectionJsons.ConnectionInfo(InetSocketAddress.createUnresolved(origin, 25566), InetSocketAddress.createUnresolved(endpoint, port), ModpackConnectionMode.HTTP, null);
	}

	private static PackDocument stage(ClientStorage storage, String id, String path, boolean selection) throws Exception {
		var fields = TestPacks.manifest("test", "config/test.json", "payload").toFields();
		fields.modpackId = id;
		fields.categories.get("General").get("main").files = Map.of(path,
				new ModpackJsons.CompleteModpackContentFields.GroupFileFields("7", path.startsWith("mods/") ? "mod" : "config", false, HashUtils.sha1("payload"), null));
		PackDocument document = TestPacks.document(GroupManifestValidator.validate(fields));
		TestPacks.stageGeneration(storage, document);
		if (selection) storage.writeActiveState(id, document.contentToken(), document.ownershipLedger().toFields(), new SelectionIntent(Set.of("main")));
		else storage.writeActiveState(id, document.contentToken(), document.ownershipLedger().toFields());
		return document;
	}

	private Map<String, String> snapshot() throws Exception {
		Map<String, String> result = new TreeMap<>();
		try (var files = Files.walk(temporary)) {
			for (Path file : files.toList()) result.put(temporary.relativize(file).toString(), Files.isDirectory(file) ? "directory" : HashUtils.getHash(file));
		}
		return result;
	}
}
