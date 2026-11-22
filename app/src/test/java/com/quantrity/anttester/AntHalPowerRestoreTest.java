package com.quantrity.anttester;

import static com.quantrity.anttester.AntHalPowerRestore.STATE_DISABLED;
import static com.quantrity.anttester.AntHalPowerRestore.STATE_DISABLING;
import static com.quantrity.anttester.AntHalPowerRestore.STATE_ENABLED;
import static com.quantrity.anttester.AntHalPowerRestore.STATE_ENABLING;
import static com.quantrity.anttester.AntHalPowerRestore.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dsi.ant.server.IAntHal;

import org.junit.Test;

import java.lang.reflect.Proxy;

public class AntHalPowerRestoreTest {
    @Test public void waitsForAnAcceptedEnableToStartBeforeRestoringPower() throws Exception {
        FakeHal radio = new FakeHal(STATE_DISABLED, STATE_ENABLING, STATE_ENABLED,
                STATE_DISABLING, STATE_DISABLED);

        assertTrue(AntHalPowerRestore.restore(radio.service, false, () -> true));
        assertEquals(1, radio.disableCalls);
        assertEquals(5, radio.stateReads);
    }

    @Test public void retriesARejectedDisableAfterTheNextStateCheck() throws Exception {
        FakeHal radio = new FakeHal(STATE_ENABLED, STATE_ENABLED, STATE_DISABLED);
        radio.rejectFirstDisable = true;

        assertTrue(AntHalPowerRestore.restore(radio.service, true, () -> true));
        assertEquals(2, radio.disableCalls);
    }

    @Test public void reportsTimeoutWhileEnableIsStillPending() throws Exception {
        FakeHal radio = new FakeHal(STATE_ENABLING);

        assertFalse(AntHalPowerRestore.restore(radio.service, false, () -> false));
        assertEquals(0, radio.disableCalls);
    }

    private static final class FakeHal {
        final IAntHal service;
        int stateReads;
        int disableCalls;
        boolean rejectFirstDisable;

        FakeHal(int... states) {
            service = (IAntHal) Proxy.newProxyInstance(IAntHal.class.getClassLoader(),
                    new Class<?>[] {IAntHal.class}, (proxy, method, args) -> {
                        if ("getAntState".equals(method.getName())) {
                            return states[stateReads++];
                        }
                        if ("setAntState".equals(method.getName())) {
                            assertEquals(STATE_DISABLED, ((Integer) args[0]).intValue());
                            disableCalls++;
                            return rejectFirstDisable && disableCalls == 1 ? 0 : SUCCESS;
                        }
                        throw new AssertionError(method.getName());
                    });
        }
    }
}
