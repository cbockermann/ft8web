package de.bockermann.ft8monitor.qsolog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal ADIF parser: turns an ADIF document (a {@code .adi} file, or the
 * {@code LoggedADIF} payload WSJT-X sends over UDP) into a list of records, each
 * a map of lowercased field name → value. Handles the {@code <name:len:type>value}
 * tag form, skips the header up to {@code <EOH>}, and splits records on {@code <EOR>}.
 */
public final class AdifParser {

    private AdifParser() {
    }

    public static List<Map<String, String>> parse(String adif) {
        List<Map<String, String>> records = new ArrayList<>();
        if (adif == null || adif.isEmpty()) {
            return records;
        }
        // Skip the header (everything up to and including <EOH>), if present.
        int eoh = adif.toLowerCase(Locale.ROOT).indexOf("<eoh>");
        int i = eoh >= 0 ? eoh + 5 : 0;

        Map<String, String> cur = new HashMap<>();
        while (i < adif.length()) {
            int lt = adif.indexOf('<', i);
            if (lt < 0) {
                break;
            }
            int gt = adif.indexOf('>', lt + 1);
            if (gt < 0) {
                break;
            }
            String[] parts = adif.substring(lt + 1, gt).split(":");
            String name = parts[0].trim().toLowerCase(Locale.ROOT);
            if (name.equals("eor")) {
                if (!cur.isEmpty()) {
                    records.add(cur);
                    cur = new HashMap<>();
                }
                i = gt + 1;
                continue;
            }
            if (name.equals("eoh") || parts.length < 2) {
                i = gt + 1; // header marker or a tag without a length — nothing to read
                continue;
            }
            int len;
            try {
                len = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                i = gt + 1;
                continue;
            }
            int vStart = gt + 1;
            int vEnd = Math.min(vStart + len, adif.length());
            cur.put(name, adif.substring(vStart, vEnd).trim());
            i = vEnd;
        }
        if (!cur.isEmpty()) {
            records.add(cur);
        }
        return records;
    }
}
