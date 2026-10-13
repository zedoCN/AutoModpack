package pl.skidam.automodpack_core.client;

import static pl.skidam.automodpack_core.protocol.NetUtils.USER_AGENT;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.Locale;
import java.util.Set;

import pl.skidam.automodpack_core.config.ConfigTools;
import pl.skidam.automodpack_core.config.ConnectionJsons;
import pl.skidam.automodpack_core.config.ModpackJsons;
import pl.skidam.automodpack_core.modpack.generation.Journal;
import pl.skidam.automodpack_core.modpack.generation.JournalEntry;
import pl.skidam.automodpack_core.modpack.generation.OwnershipLedger;
import pl.skidam.automodpack_core.modpack.generation.PackDocument;
import pl.skidam.automodpack_core.modpack.group.ClientPlatform;
import pl.skidam.automodpack_core.modpack.group.GroupManifestValidator;
import pl.skidam.automodpack_core.modpack.group.LogicalPath;
import pl.skidam.automodpack_core.modpack.group.SelectedModpackTarget;
import pl.skidam.automodpack_core.modpack.group.SelectionIntent;
import pl.skidam.automodpack_core.protocol.ModpackConnectionMode;
import pl.skidam.automodpack_core.update.ClientStorage;
import pl.skidam.automodpack_core.utils.HashUtils;

/** Read-only compatibility gate for the already published TECH pack, frozen once per transport. */
public final class TechReleaseTrack {
	public static final String MARKER = "tech-required-only/1";
	private static final String PACK_ID = "l3ic0dd";
	private static final Set<String> WITHDRAWN_PATHS = Set.of(
			"config/chatimage-playback.json", "config/chatimageconfig.json", "config/simplesorter.json",
			"mods/chatimage-1.4.7-port.7-neoforge+26.2.jar", "mods/clumps-neoforge-26.2-26.2.1.jar",
			"mods/distanthorizons-3.3.3-26.2-fabric-neoforge.jar", "mods/jade-mc26.2-neoforge-26.2.10.jar",
			"mods/rollinggate-neoforge-26.2-1.1.0+build.70.jar", "mods/scalablelux-neoforge-0.3.0-alpha.0.16-all.jar",
			"mods/simplesorterr-neoforge-26.2-3.6.1.jar", "mods/appleskin-neoforge-mc26.2-3.0.10.jar",
			"mods/c2me-neoforge-mc26.2-0.4.2-alpha.0.96.jar", "mods/cloth-config-26.2.155.jar",
			"mods/jei-26.2-neoforge-30.39.0.232.jar", "mods/journeymap-neoforge-26.2-6.0.9.jar",
			"mods/lithium-neoforge-0.25.3+mc26.2.jar", "mods/mezz_config-26.2-neoforge-0.6.6-standalone.jar",
			"mods/vanillapings-26.2-neoforge-1.11.1-camera.3-neoforge.1.jar");

	private TechReleaseTrack() {}

	public static String userAgent(ClientStorage storage, ConnectionJsons.ConnectionInfo connection) {
		if (storage == null || connection == null || connection.connectionMode != ModpackConnectionMode.HTTP
				|| connection.endpoint == null || !"status.xiang233.top".equalsIgnoreCase(connection.endpoint.getHostString()) || connection.endpoint.getPort() != 443
				|| !TrustedUpdatePolicy.allowsPack(PACK_ID, connection))
			return USER_AGENT;
		try {
			// Interrupted publication and legacy pointers without applied selection cannot prove safe removal.
			if (Files.exists(storage.transactionFile(), LinkOption.NOFOLLOW_LINKS) || Files.exists(storage.incomingDirectory(), LinkOption.NOFOLLOW_LINKS)
					|| Files.exists(storage.backupDirectory(), LinkOption.NOFOLLOW_LINKS))
				return USER_AGENT;
			var active = storage.readActiveState();
			if (active == null) {
				// A missing pointer alongside an orphaned projection is not a new install.
				if (!Files.notExists(storage.activeDirectory(), LinkOption.NOFOLLOW_LINKS)) return USER_AGENT;
				return USER_AGENT + " " + MARKER;
			}
			if (!PACK_ID.equals(active.modpackId) || active.selectedGroups == null) return USER_AGENT;
			// The normal mirror APIs set aside damaged files; this gate must remain strictly read-only.
			var journal = storage.historyJournalFile(PACK_ID);
			if (!Files.isRegularFile(journal, LinkOption.NOFOLLOW_LINKS)) return USER_AGENT;
			JournalEntry appliedEntry = null;
			for (var entry : Journal.openComplete(journal).entries())
				if (active.contentToken.equals(entry.contentToken())) appliedEntry = entry;
			if (appliedEntry == null) return USER_AGENT;
			var policy = storage.objectFile(appliedEntry.policySha1());
			if (!Files.isRegularFile(policy, LinkOption.NOFOLLOW_LINKS) || !appliedEntry.policySha1().equals(HashUtils.getHash(policy))) return USER_AGENT;
			var fields = ConfigTools.read(policy, ModpackJsons.CompleteModpackContentFields.class).orElseThrow();
			var document = new PackDocument(GroupManifestValidator.validate(fields), active.contentToken, appliedEntry.policySha1(), appliedEntry.createdAt(),
					OwnershipLedger.fromFields(active.ownershipLedger), "");
			if (!PACK_ID.equals(document.manifest().modpackId())) return USER_AGENT;
			SelectionIntent applied = new SelectionIntent(active.selectedGroups, active.selectedCategories, active.excludedGroups,
					active.selectedPlatform == null ? null : ClientPlatform.parse(active.selectedPlatform));
			var target = SelectedModpackTarget.prepare(document, null, applied, ClientPlatform.effective(applied));
			for (var item : target.flatTarget().list)
				if (WITHDRAWN_PATHS.contains(LogicalPath.normalize(item.file).toLowerCase(Locale.ROOT))) return USER_AGENT;
			return USER_AGENT + " " + MARKER;
		} catch (Exception unavailable) {
			// Failure is the old track, never permission to retire files whose ownership is uncertain.
			return USER_AGENT;
		}
	}
}
