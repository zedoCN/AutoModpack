package pl.skidam.automodpack_core.client;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pl.skidam.automodpack_core.Constants;
import pl.skidam.automodpack_core.config.ClientConfigJsons;
import pl.skidam.automodpack_core.config.ConnectionJsons;
import pl.skidam.automodpack_core.modpack.generation.TestPacks;
import pl.skidam.automodpack_core.modpack.group.ClientPlatform;
import pl.skidam.automodpack_core.modpack.group.GroupManifestValidator;
import pl.skidam.automodpack_core.modpack.group.SelectedModpackTarget;
import pl.skidam.automodpack_core.platforms.FetchManager;
import pl.skidam.automodpack_core.platforms.PlatformCache;
import pl.skidam.automodpack_core.protocol.ModpackConnectionMode;
import pl.skidam.automodpack_core.storage.TestDataRoot;
import pl.skidam.automodpack_core.update.ClientObjectStore;
import pl.skidam.automodpack_core.utils.HashUtils;

class UpdateSessionTrustedSourcesTest {
	@Test
	void disabledConfirmationDoesNotStartPlatformQueries(@TempDir Path root) throws Exception {
		var original = Constants.clientConfig;
		try {
			Constants.clientConfig = new ClientConfigJsons.ClientConfigFieldsV3();
			Constants.clientConfig.confirmUnverifiedMods = false;
			var storage = TestDataRoot.open(root.resolve("game"), root.resolve("data"));
			var fields = TestPacks.manifest("test", "config/example.txt", "payload").toFields();
			var file = fields.categories.get("General").get("main").files.remove("config/example.txt");
			file.type = "mod";
			fields.categories.get("General").get("main").files.put("mods/example.jar", file);
			var target = SelectedModpackTarget.prepareDefault(TestPacks.document(GroupManifestValidator.validate(fields)), ClientPlatform.current());
			try (var cache = PlatformCache.open(storage.platformCacheDirectory())) {
				var sources = new SourceCatalogue(() -> target, cache);
				sources.startSourceFetch();
				assertNull(sources.sourceFetch(target.flatTarget().list));
				assertEquals(0, sources.sourceAvailability().totalFiles());
			}
		} finally {
			Constants.clientConfig = original;
		}
	}

	@Test
	void confirmationSwitchControlsThirdPartyLookupForEveryOrigin(@TempDir Path root) throws Exception {
		var original = Constants.clientConfig;
		try {
			Constants.clientConfig = new ClientConfigJsons.ClientConfigFieldsV3();
			Constants.clientConfig.confirmUnverifiedMods = false;
			for (boolean confirm : List.of(false, true)) {
				Constants.clientConfig.confirmUnverifiedMods = confirm;
				String origin = confirm ? "other.example" : "tech-ali.zedo.top";
				var storage = TestDataRoot.open(root.resolve(origin).resolve("game"), root.resolve(origin).resolve("data"));
				var fields = TestPacks.manifest("test", "config/example.txt", "payload").toFields();
				fields.modpackId = "l3ic0dd";
				var target = SelectedModpackTarget.prepareDefault(TestPacks.document(GroupManifestValidator.validate(fields)), ClientPlatform.current());
				ClientObjectStore.storeObject(storage, HashUtils.sha1("payload"), "payload".getBytes(StandardCharsets.UTF_8));
				try (var cache = PlatformCache.open(storage.platformCacheDirectory())) {
					var sources = new SourceCatalogue(() -> target, cache);
					AtomicInteger waits = new AtomicInteger();
					var pending = new FetchManager(List.of(), cache) {
						@Override
						public void fetch() {
							waits.incrementAndGet();
						}
					};
					var field = SourceCatalogue.class.getDeclaredField("sourceFetchManager");
					field.setAccessible(true);
					field.set(sources, pending);
					var connection = new ConnectionJsons.ConnectionInfo(InetSocketAddress.createUnresolved(origin, 25566), InetSocketAddress.createUnresolved("status.xiang233.top", 443), ModpackConnectionMode.HTTP,
							null);
					var builder = new ClientUpdatePlanBuilder(storage, request -> {}, "neoforge");
					var objects = new ModpackObjectAcquisition(storage, cache, sources, builder, connection, null, new AtomicBoolean(), () -> "test", () -> {}, "");
					var session = new UpdateSession(storage, builder, objects, sources, null, connection, target, true, Map.of(), false);
					session.prepare(true, false);
					assertEquals(confirm ? 1 : 0, waits.get(), origin);
					objects.release();
				}
			}
		} finally {
			Constants.clientConfig = original;
		}
	}
}
