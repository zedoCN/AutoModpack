package pl.skidam.automodpack_core.client;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pl.skidam.automodpack_core.Constants;
import pl.skidam.automodpack_core.config.ClientConfigJsons;
import pl.skidam.automodpack_core.config.ConnectionJsons;
import pl.skidam.automodpack_core.config.ReconfConfigs;
import pl.skidam.automodpack_core.protocol.ModpackConnectionMode;
import pl.skidam.automodpack_core.utils.AddressHelpers;

class TrustedUpdatePolicyTest {
	private final ClientConfigJsons.ClientConfigFieldsV3 original = Constants.clientConfig;

	@AfterEach
	void restoreConfig() {
		Constants.clientConfig = original;
	}

	@Test
	void disabledConfirmationAppliesToAllOriginsAndDefaultPorts() {
		Constants.clientConfig = new ClientConfigJsons.ClientConfigFieldsV3();
		assertFalse(TrustedUpdatePolicy.allowsPack("l3ic0dd", connection("tech-ali.zedo.top", ModpackConnectionMode.HTTP)));
		Constants.clientConfig.confirmUnverifiedMods = false;
		for (String origin : List.of("tech.zedo.top", "tech-ali.zedo.top", "tech-acc.zedo.top", "other.example:12345")) {
			assertTrue(TrustedUpdatePolicy.allowsPack("l3ic0dd", connection(origin, ModpackConnectionMode.HTTP)), origin);
			assertTrue(TrustedUpdatePolicy.allowsCaEndpoint(connection(origin, ModpackConnectionMode.HTTP)), origin);
		}
		assertTrue(TrustedUpdatePolicy.allowsPack("abc1234", connection("other.example", ModpackConnectionMode.MAGIC)));
		assertFalse(TrustedUpdatePolicy.allowsCaEndpoint(connection("other.example", ModpackConnectionMode.MAGIC)));
		assertFalse(TrustedUpdatePolicy.allowsPack("bad", connection("other.example", ModpackConnectionMode.HTTP)));
		assertFalse(TrustedUpdatePolicy.allowsPack("l3ic0dd", null));
		assertFalse(TrustedUpdatePolicy.allowsPack("l3ic0dd", new ConnectionJsons.ConnectionInfo()));
		Constants.clientConfig.confirmUnverifiedMods = true;
		assertFalse(TrustedUpdatePolicy.allowsPack("l3ic0dd", connection("tech-ali.zedo.top", ModpackConnectionMode.HTTP)));
	}

	@Test
	void confirmationChoiceSurvivesSerializationCopyAndRebase(@TempDir Path directory) throws Exception {
		var expected = new ClientConfigJsons.ClientConfigFieldsV3();
		expected.confirmUnverifiedMods = false;
		Path config = directory.resolve("client.conf");
		ReconfConfigs.save(config, expected, ClientConfigJsons.ClientConfigFieldsV3.class, ClientConfigJsons.ClientConfigFieldsV3::new);
		var read = ReconfConfigs.read(config, ClientConfigJsons.ClientConfigFieldsV3.class).orElseThrow();
		assertEquals(expected, read);
		assertEquals(expected.hashCode(), read.hashCode());
		var planned = read.withPlayMusic(false);
		assertFalse(planned.confirmUnverifiedMods);
		var changed = new ClientConfigJsons.ClientConfigFieldsV3(read);
		changed.confirmUnverifiedMods = true;
		assertTrue(changed.rebase(expected, planned).confirmUnverifiedMods, "a pending plan cannot undo a newly enabled confirmation");
		assertFalse(read.rebase(expected, planned).confirmUnverifiedMods);
	}

	private static ConnectionJsons.ConnectionInfo connection(String origin, ModpackConnectionMode mode) {
		return new ConnectionJsons.ConnectionInfo(AddressHelpers.parseOrigin(origin), AddressHelpers.parseEndpoint("status.xiang233.top:443"), mode, null);
	}
}
