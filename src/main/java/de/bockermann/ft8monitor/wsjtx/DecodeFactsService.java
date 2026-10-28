package de.bockermann.ft8monitor.wsjtx;

import de.bockermann.ft8monitor.geo.CallsignFlags;
import de.bockermann.ft8monitor.geo.GeoLookup;
import de.bockermann.ft8monitor.overview.Maidenhead;
import de.bockermann.ft8monitor.qso.Callsigns;
import de.bockermann.ft8monitor.qsolog.QsoLogRepository;
import de.bockermann.ft8monitor.settings.SettingsService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves {@link DecodeFacts} for a decode — the inputs the Control-view filter
 * predicates evaluate against ({@code worked}, {@code last_worked}, {@code country},
 * {@code distance}, {@code age}, …). The predicate <em>values</em> are single-sourced
 * here on the backend; the filter expression itself is evaluated client-side.
 */
@Service
public class DecodeFactsService {

    private static final double EARTH_KM = 6371.0;

    private final QsoLogRepository qsos;
    private final CallsignFlags flags;
    private final GeoLookup geo;
    private final SettingsService settings;

    // First time we saw each call this session, for age(call). Bounded.
    private final Map<String, Long> firstSeen = new ConcurrentHashMap<>();

    public DecodeFactsService(QsoLogRepository qsos, CallsignFlags flags, GeoLookup geo,
                              SettingsService settings) {
        this.qsos = qsos;
        this.flags = flags;
        this.geo = geo;
        this.settings = settings;
    }

    public DecodeFacts resolve(WsjtxCodec.Decode d) {
        String msg = d.message() == null ? "" : d.message();
        String me = settings.get().callsign().toUpperCase(Locale.ROOT);

        String call = "";
        boolean directedAtMe = false;
        switch (Callsigns.parse(msg)) {
            case Callsigns.Stations.Cq c -> call = up(c.caller());
            case Callsigns.Stations.Directed dd -> {
                String a = up(dd.a());
                String b = up(dd.b());
                if (!me.isEmpty() && a.equals(me)) {
                    call = b;
                    directedAtMe = true;
                } else {
                    call = a;
                }
            }
            case Callsigns.Stations.None ignored -> {
            }
        }

        long now = Instant.now().getEpochSecond();
        long ageSec = 0;
        boolean worked = false;
        Long lastWorkedSec = null;
        int workedCount = 0;
        String cc = "";
        String country = "";
        Integer distanceKm = null;
        String grid = Maidenhead.squareOf(msg);
        if (grid == null) {
            grid = "";
        }

        if (!call.isEmpty()) {
            long seen = firstSeen.computeIfAbsent(call, k -> now);
            if (firstSeen.size() > 5000) {
                firstSeen.clear(); // crude bound; fine for a rolling session
            }
            ageSec = Math.max(0, now - seen);

            QsoLogRepository.CallStats st = qsos.callStats(call);
            workedCount = st.count();
            worked = workedCount > 0;
            if (st.lastEpoch() != null) {
                lastWorkedSec = Math.max(0, now - st.lastEpoch());
            }
            cc = nz(flags.countryCode(call));
            country = cc.isEmpty() ? "" : geo.countryName(cc);
            distanceKm = distance(grid);
        }

        return new DecodeFacts(call, grid, cc, country, worked, lastWorkedSec, workedCount,
                distanceKm, directedAtMe, ageSec);
    }

    private Integer distance(String theirGrid) {
        String myGrid = settings.get().locator();
        if (theirGrid == null || theirGrid.isBlank() || myGrid == null || myGrid.isBlank()) {
            return null;
        }
        double[] a = Maidenhead.center(myGrid);
        double[] b = Maidenhead.center(theirGrid);
        if (a == null || b == null) {
            return null;
        }
        double lat1 = Math.toRadians(a[0]);
        double lat2 = Math.toRadians(b[0]);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b[1] - a[1]);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return (int) Math.round(EARTH_KM * 2 * Math.asin(Math.min(1, Math.sqrt(h))));
    }

    private static String up(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
