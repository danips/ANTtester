package com.quantrity.anttester;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Collections;

public class AntServiceStateTest {
    @Test public void unsupportedAdapterProviderIsSetupRequired() {
        assertEquals(AntReadiness.SETUP_REQUIRED_SERVICE,
                evaluate(AntSupportSnapshot.ServiceState.SERVICE_TOO_OLD).readiness);
    }

    @Test public void permissionFailureIsTerminalAndSpecific() {
        assertEquals(AntReadiness.BLOCKED_PERMISSION,
                evaluate(AntSupportSnapshot.ServiceState.PERMISSION_BLOCKED).readiness);
    }

    @Test public void bindFailureIsUnknownNotUnsupported() {
        assertEquals(AntReadiness.UNKNOWN_ERROR,
                evaluate(AntSupportSnapshot.ServiceState.BIND_FAILED).readiness);
    }

    @Test public void timeoutIsTerminalAndUnknown() {
        assertEquals(AntReadiness.UNKNOWN_ERROR,
                evaluate(AntSupportSnapshot.ServiceState.TIMED_OUT).readiness);
    }

    @Test public void noAdapterIsDistinctFromQueryFailure() {
        assertEquals(AntReadiness.SETUP_REQUIRED_ADAPTER,
                evaluate(AntSupportSnapshot.ServiceState.NO_ADAPTER).readiness);
    }

    private static AntReadinessResult evaluate(AntSupportSnapshot.ServiceState serviceState) {
        PackageState installed = new PackageState("radio", PackageState.Kind.INSTALLED_ENABLED,
                "1", 50000, null);
        AntSupportSnapshot snapshot = new AntSupportSnapshot(AntSupportSnapshot.ProbeStatus.COMPLETE,
                true, false, false, installed, installed, installed, installed,
                new PermissionState(PermissionState.Kind.GRANTED, PermissionInspector.ANT_HARDWARE_PERMISSION),
                serviceState, Collections.<AntAdapterSnapshot>emptyList(),
                Collections.<UsbDeviceSnapshot>emptyList(), Collections.<String>emptyList());
        return AntReadinessEvaluator.evaluate(snapshot);
    }
}
