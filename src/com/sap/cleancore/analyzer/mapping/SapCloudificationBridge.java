package com.sap.cleancore.analyzer.mapping;

import com.sap.cleancore.analyzer.data.ReleaseObject;
import com.sap.cleancore.analyzer.data.SapReleaseDataService;
import com.sap.cleancore.analyzer.model.MappingEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter that pulls SAP's official Cloudification Repository data
 * (https://sap.github.io/abap-atc-cr-cv-s4hc/) via the existing
 * com.sap.cleancore.analyzer.data.SapReleaseDataService and converts the
 * tadirObject/successor entries into our MappingEntry model.
 *
 * The upstream JSON shape:
 *   { "objectReleaseInfo": [
 *       { "tadirObject": "TABL", "tadirObjName": "BSEG",
 *         "state": "notToBeReleased",
 *         "successors": [{ "tadirObject": "CDS_STOB",
 *                          "tadirObjName": "I_OPERATIONALACCTGDOCITEM" }] },
 *       ...
 *   ] }
 *
 * Mapping rules:
 *   TABL  -> LegacyType.TABLE / ModernType.CDS_VIEW (if successor type CDS_STOB)
 *   FUGR  -> LegacyType.FM    / ModernType.FM       (function group successor)
 *   FUNC  -> LegacyType.FM
 *   CLAS  -> LegacyType.CLASS / ModernType.CLASS
 *   INTF  -> LegacyType.INTERFACE
 *   DDLS  -> LegacyType.CDS
 *   default -> LegacyType.OTHER
 *
 * State translation:
 *   "released"          -> RELEASED
 *   "notReleased"       -> NOT_RELEASED
 *   "notToBeReleased"   -> DEPRECATED   (SAP explicitly says: don't use)
 *   "deprecated"        -> DEPRECATED
 *   anything else       -> UNKNOWN
 */
public class SapCloudificationBridge {

    /** Refresh the local cache if it is older than this. */
    private static final long CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000;

    /** Returns number of entries merged into the repository. */
    public int syncIntoRepository() throws Exception {
        SapReleaseDataService svc = SapReleaseDataService.getInstance();

        // If RAM cache is empty, hydrate it from disk (fast).
        if (!svc.isLoaded()) {
            svc.loadFromCache();
        }

        // Refresh disk cache from GitHub if it's missing or older than TTL.
        // Bridge stays usable even on slow networks — we don't block on this
        // when we already have a non-stale cache.
        if (isCacheStaleOrMissing(svc)) {
            try {
                downloadBlocking(svc);
            } catch (Exception ignored) {
                // network failure — fall back to whatever we already have in RAM/disk
            }
        }

        if (!svc.isLoaded()) {
            throw new Exception("Could not load SAP Cloudification Repository data (cache miss + network failure).");
        }

        List<MappingEntry> mapped = new ArrayList<>();
        // Iterate via the indexed types map — exposed through public getters/search.
        for (String tadirObject : svc.getAvailableObjectTypes()) {
            List<ReleaseObject> list = svc.search("", tadirObject, null, 100000);
            for (ReleaseObject ro : list) {
                if (ro.getSuccessors() == null || ro.getSuccessors().isEmpty()) continue;
                // Each successor becomes its own mapping row keyed on legacy.
                for (ReleaseObject.Successor s : ro.getSuccessors()) {
                    MappingEntry e = new MappingEntry();
                    e.setLegacyName(ro.getTadirObjName());
                    e.setLegacyType(mapLegacy(ro.getTadirObject()));
                    e.setModernName(s.getTadirObjName());
                    e.setModernType(mapModern(s.getTadirObject()));
                    e.setReleaseState(mapState(ro.getState()));
                    e.setSource(MappingEntry.Source.API_HUB); // we treat the repo as an authoritative external feed
                    e.setNotes("SAP Cloudification Repo: " + (ro.getApplicationComponent() != null
                            ? ro.getApplicationComponent() : "")
                            + (ro.getSoftwareComponent() != null
                                ? " (" + ro.getSoftwareComponent() + ")" : "")
                            + " | upstream type " + ro.getTadirObject() + " -> " + s.getTadirObject());
                    mapped.add(e);
                }
                // If state is notToBeReleased and no successor — still record so analysis flags it.
                // (covered above only when successors exist; the absence of successors is handled below.)
            }
            // Capture "notToBeReleased / deprecated" objects WITHOUT successors
            for (ReleaseObject ro : list) {
                if (ro.getSuccessors() != null && !ro.getSuccessors().isEmpty()) continue;
                String state = ro.getState() != null ? ro.getState().toLowerCase(Locale.ROOT) : "";
                if (!state.contains("notreleased") && !state.contains("nottobereleased")
                        && !state.contains("deprecated")) continue;
                MappingEntry e = new MappingEntry();
                e.setLegacyName(ro.getTadirObjName());
                e.setLegacyType(mapLegacy(ro.getTadirObject()));
                e.setModernName("(no replacement)");
                e.setModernType(MappingEntry.ModernType.NO_REPLACEMENT);
                e.setReleaseState(mapState(ro.getState()));
                e.setSource(MappingEntry.Source.API_HUB);
                e.setNotes("SAP Cloudification Repo: deprecated / not-to-be-released; redesign required.");
                mapped.add(e);
            }
        }

        MappingRepository.getInstance().mergeRemote(mapped);
        return mapped.size();
    }

    /** Disk cache missing, or last download older than CACHE_TTL_MS. */
    private boolean isCacheStaleOrMissing(SapReleaseDataService svc) {
        try {
            java.nio.file.Path cache = java.nio.file.Path.of(
                    System.getProperty("user.home"), ".rap-generator", "sap-release-data.json");
            if (!java.nio.file.Files.exists(cache)) return true;
            long age = System.currentTimeMillis()
                    - java.nio.file.Files.getLastModifiedTime(cache).toMillis();
            return age > CACHE_TTL_MS;
        } catch (Exception e) {
            return true; // play it safe — try a refresh
        }
    }

    private void downloadBlocking(SapReleaseDataService svc) throws Exception {
        final Object lock = new Object();
        final boolean[] done = { false };
        svc.downloadDataAsync(() -> {
            synchronized (lock) {
                done[0] = true;
                lock.notifyAll();
            }
        });
        // Wait up to 60s
        synchronized (lock) {
            long deadline = System.currentTimeMillis() + 60_000L;
            while (!done[0] && System.currentTimeMillis() < deadline) {
                lock.wait(2000);
            }
        }
    }

    private MappingEntry.LegacyType mapLegacy(String tadir) {
        if (tadir == null) return MappingEntry.LegacyType.OTHER;
        switch (tadir.toUpperCase(Locale.ROOT)) {
            case "TABL": return MappingEntry.LegacyType.TABLE;
            case "FUGR":
            case "FUNC": return MappingEntry.LegacyType.FM;
            case "CLAS": return MappingEntry.LegacyType.CLASS;
            case "INTF": return MappingEntry.LegacyType.INTERFACE;
            case "DDLS":
            case "CDS_STOB": return MappingEntry.LegacyType.CDS;
            case "PROG": return MappingEntry.LegacyType.REPORT;
            default: return MappingEntry.LegacyType.OTHER;
        }
    }

    private MappingEntry.ModernType mapModern(String tadir) {
        if (tadir == null) return MappingEntry.ModernType.NO_REPLACEMENT;
        switch (tadir.toUpperCase(Locale.ROOT)) {
            case "CDS_STOB":
            case "DDLS": return MappingEntry.ModernType.CDS_VIEW;
            case "CLAS": return MappingEntry.ModernType.CLASS;
            case "FUGR":
            case "FUNC": return MappingEntry.ModernType.FM;
            case "INTF": return MappingEntry.ModernType.CLASS;
            case "SRVB":
            case "SRVD": return MappingEntry.ModernType.API_ODATA;
            default: return MappingEntry.ModernType.NO_REPLACEMENT;
        }
    }

    private MappingEntry.ReleaseState mapState(String state) {
        if (state == null) return MappingEntry.ReleaseState.UNKNOWN;
        String s = state.toLowerCase(Locale.ROOT);
        if (s.equals("released")) return MappingEntry.ReleaseState.RELEASED;
        if (s.contains("nottobereleased") || s.contains("not-to-be-released"))
            return MappingEntry.ReleaseState.DEPRECATED;
        if (s.contains("notreleased") || s.contains("not-released"))
            return MappingEntry.ReleaseState.NOT_RELEASED;
        if (s.contains("deprecated")) return MappingEntry.ReleaseState.DEPRECATED;
        if (s.contains("removed")) return MappingEntry.ReleaseState.REMOVED;
        return MappingEntry.ReleaseState.UNKNOWN;
    }
}
