package de.bockermann.ft8monitor.rig;

import org.junit.jupiter.api.Test;

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
}
