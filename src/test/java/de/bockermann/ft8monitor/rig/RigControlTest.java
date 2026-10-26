package de.bockermann.ft8monitor.rig;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RigControlTest {

    @Test
    void isOkRecognisesRprtZeroOnly() {
        assertTrue(RigControl.isOk("RPRT 0"));
        assertTrue(RigControl.isOk("RPRT 0\n"));
        assertFalse(RigControl.isOk("RPRT -1"));
        assertFalse(RigControl.isOk(""));
        assertFalse(RigControl.isOk(null));
    }

    @Test
    void disabledNeverTalksToRig() {
        RigControl rig = new RigControl(false, "127.0.0.1", 4532);
        RigControl.Result r = rig.setFrequency(14_074_000L);
        assertFalse(r.ok());
        assertTrue(r.message().contains("rigctld disabled"));
    }

    @Test
    void rejectsInvalidFrequencyWhenEnabled() {
        RigControl rig = new RigControl(true, "127.0.0.1", 4532);
        assertFalse(rig.setFrequency(0).ok());
    }

    @Test
    void tuneSendsFrequencyThenModeOnOneConnection() throws Exception {
        List<String> received = Collections.synchronizedList(new ArrayList<>());
        try (ServerSocket server = mockRigctld(received, "RPRT 0", "RPRT 0")) {
            RigControl rig = new RigControl(true, "127.0.0.1", server.getLocalPort());
            RigControl.Result r = rig.tune(14_074_000L, "PKTUSB");
            assertTrue(r.ok(), r.message());
        }
        assertEquals(List.of("F 14074000", "M PKTUSB 0"), received);
    }

    @Test
    void reportsModeFailureAfterFrequencySet() throws Exception {
        List<String> received = Collections.synchronizedList(new ArrayList<>());
        try (ServerSocket server = mockRigctld(received, "RPRT 0", "RPRT -1")) {
            RigControl rig = new RigControl(true, "127.0.0.1", server.getLocalPort());
            RigControl.Result r = rig.tune(14_074_000L, "PKTUSB");
            assertFalse(r.ok());
            assertTrue(r.message().contains("mode"), r.message());
        }
    }

    /** A throwaway rigctld that replies to the first two commands with the given lines. */
    private static ServerSocket mockRigctld(List<String> received, String... replies) throws Exception {
        ServerSocket server = new ServerSocket(0);
        Thread t = new Thread(() -> {
            try (Socket s = server.accept()) {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
                OutputStream out = s.getOutputStream();
                for (String reply : replies) {
                    String line = in.readLine();
                    if (line == null) {
                        break;
                    }
                    received.add(line);
                    out.write((reply + "\n").getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                }
            } catch (Exception ignored) {
                // test teardown
            }
        });
        t.setDaemon(true);
        t.start();
        return server;
    }
}
