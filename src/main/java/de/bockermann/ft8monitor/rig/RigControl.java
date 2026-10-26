package de.bockermann.ft8monitor.rig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Sets the rig frequency through a Hamlib <b>rigctld</b> TCP socket.
 *
 * <p>The WSJT-X UDP protocol cannot set the dial frequency, so to retune from the
 * Control view we talk to a running {@code rigctld} that owns the single CAT
 * port. Point WSJT-X at "Hamlib NET rigctl" (same rigctld) so it keeps working
 * and follows the frequency we set. Off by default ({@code ft8.rigctld.enabled}),
 * since it requires that shared-CAT setup.
 */
@Service
public class RigControl {

    private static final Logger log = LoggerFactory.getLogger(RigControl.class);
    private static final int TIMEOUT_MS = 2000;

    private final boolean enabled;
    private final String host;
    private final int port;

    public RigControl(@Value("${ft8.rigctld.enabled:false}") boolean enabled,
                      @Value("${ft8.rigctld.host:127.0.0.1}") String host,
                      @Value("${ft8.rigctld.port:4532}") int port) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Set the dial frequency (Hz) only. */
    public Result setFrequency(long hz) {
        return tune(hz, null);
    }

    /**
     * Set the dial frequency and, if {@code mode} is given, the rig mode — over a
     * single rigctld connection ({@code F <hz>} then {@code M <mode> 0}). A band
     * change thus lands FT8-ready (e.g. PKTUSB = Data-U).
     */
    public Result tune(long hz, String mode) {
        if (!enabled) {
            return new Result(false, "rigctld disabled — set ft8.rigctld.enabled=true and point "
                    + "WSJT-X at Hamlib NET rigctl to retune from here");
        }
        if (hz <= 0) {
            return new Result(false, "invalid frequency");
        }
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            s.setSoTimeout(TIMEOUT_MS);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();

            out.write(("F " + hz + "\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String fResp = readLine(in);
            if (!isOk(fResp)) {
                return new Result(false, "rigctld set-freq error: " + nz(fResp));
            }

            if (mode != null && !mode.isBlank()) {
                // passband 0 = keep the rig's default for that mode
                out.write(("M " + mode.trim() + " 0\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                String mResp = readLine(in);
                if (!isOk(mResp)) {
                    return new Result(false, "frequency set, but mode '" + mode + "' failed: " + nz(mResp));
                }
                log.info("rigctld: set {} Hz, mode {}", hz, mode.trim());
            } else {
                log.info("rigctld: set {} Hz", hz);
            }
            return new Result(true, "ok");
        } catch (Exception e) {
            return new Result(false, "rigctld unreachable at " + host + ":" + port + " (" + e.getMessage() + ")");
        }
    }

    private static String nz(String resp) {
        return resp == null ? "no response" : resp.trim();
    }

    /** rigctld replies {@code RPRT 0} on success, {@code RPRT <neg>} on error. */
    static boolean isOk(String response) {
        return response != null && response.contains("RPRT 0");
    }

    private static String readLine(InputStream in) throws java.io.IOException {
        StringBuilder sb = new StringBuilder(16);
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            sb.append((char) c);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Outcome of a rig command. */
    public record Result(boolean ok, String message) {
    }
}
