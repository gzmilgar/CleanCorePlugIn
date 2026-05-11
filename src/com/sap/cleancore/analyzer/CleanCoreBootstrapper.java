package com.sap.cleancore.analyzer;

import com.sap.cleancore.analyzer.mapping.MappingRepository;
import com.sap.cleancore.analyzer.mapping.SapApiHubClient;
import com.sap.cleancore.analyzer.mapping.SapCloudificationBridge;
import com.sap.cleancore.analyzer.preferences.CleanCorePreferences;

/**
 * Background bootstrap fired once when the plug-in activates.
 *
 * Responsibilities (all on a daemon background thread so the UI never blocks):
 *   1. Load the bundled baseline mapping JSON (fast, in-bundle).
 *   2. Run the SAP Cloudification Repository bridge — bridge itself decides
 *      whether to load from local cache or download fresh from GitHub.
 *      Either way, the parsed entries are merged into MappingRepository.
 *   3. Optionally pull api.sap.com catalog (best-effort, silent on failure).
 *
 * Errors are swallowed — bootstrap failure must never block the UI. The user
 * can always click the manual sync buttons in Mapping Maintenance to retry.
 */
public class CleanCoreBootstrapper {

    private static boolean started = false;

    public static synchronized void runOnce() {
        if (started) return;
        started = true;

        Thread t = new Thread(() -> {
            try {
                MappingRepository.getInstance().loadIfNeeded();
            } catch (Throwable ignored) {}

            try {
                // ALWAYS run the bridge — it handles cache-vs-network internally
                // and is the only path that actually merges entries into
                // MappingRepository. Previous "isStale ? skip" gating left the
                // repo at baseline size on warm starts.
                new SapCloudificationBridge().syncIntoRepository();
            } catch (Throwable ignored) {
                // Network may be down; user can still click manual sync later.
            }

            try {
                String url = CleanCorePreferences.getApiHubUrl();
                if (url != null && !url.isEmpty()) {
                    SapApiHubClient client = new SapApiHubClient(url);
                    MappingRepository.getInstance().mergeRemote(client.fetch());
                }
            } catch (Throwable ignored) {}
        }, "CleanCore-Bootstrap");
        t.setDaemon(true);
        t.start();
    }
}
