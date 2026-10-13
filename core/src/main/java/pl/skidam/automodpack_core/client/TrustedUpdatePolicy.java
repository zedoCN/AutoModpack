package pl.skidam.automodpack_core.client;

import static pl.skidam.automodpack_core.Constants.clientConfig;

import pl.skidam.automodpack_core.config.ConnectionJsons;
import pl.skidam.automodpack_core.modpack.ModpackId;
import pl.skidam.automodpack_core.protocol.ModpackConnectionMode;

/** The local confirmation switch applies to all complete connections, without an origin allowlist. */
public final class TrustedUpdatePolicy {
	private TrustedUpdatePolicy() {}

	public static boolean allowsPack(String modpackId, ConnectionJsons.ConnectionInfo connection) {
		return automaticUpdates() && ModpackId.isValid(modpackId) && connection != null && connection.isComplete();
	}

	/** Use normal CA and hostname validation for HTTPS; existing certificate pins remain enforced. */
	public static boolean allowsCaEndpoint(ConnectionJsons.ConnectionInfo connection) {
		return automaticUpdates() && connection != null && connection.isComplete() && connection.connectionMode == ModpackConnectionMode.HTTP;
	}

	private static boolean automaticUpdates() {
		return clientConfig != null && !clientConfig.confirmUnverifiedMods;
	}
}
