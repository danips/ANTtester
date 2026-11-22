package com.quantrity.anttester;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Collections;

public class AntReadinessEvaluatorTest {
    private static final String PERMISSION = "com.dsi.ant.permission.ANT";

    @Test public void checkingIsNotFailure() {
        assertEquals(AntReadiness.CHECKING,
                AntReadinessEvaluator.evaluate(AntSupportSnapshot.checking()).readiness);
    }

    @Test public void builtInRouteIsReadyOnlyWithAdapter() {
        AntSupportSnapshot snapshot = snapshot(true, false, installed(), installed(),
                PermissionState.Kind.GRANTED, AntSupportSnapshot.ServiceState.AVAILABLE,
                Collections.singletonList(new AntAdapterSnapshot("Built-in", "1", true, "a")));
        assertEquals(AntReadiness.READY_BUILT_IN, AntReadinessEvaluator.evaluate(snapshot).readiness);
    }

    @Test public void usbRouteIsReadyOnlyWithExternalAdapterAndUsbService() {
        AntSupportSnapshot snapshot = snapshot(false, true, installed(), installed(),
                PermissionState.Kind.GRANTED, AntSupportSnapshot.ServiceState.AVAILABLE,
                Collections.singletonList(new AntAdapterSnapshot("External", "1", false, "a")));
        assertEquals(AntReadiness.READY_EXTERNAL_USB, AntReadinessEvaluator.evaluate(snapshot).readiness);
    }

    @Test public void usbHostAloneRequiresAdapter() {
        AntSupportSnapshot snapshot = snapshot(false, true, installed(), installed(),
                PermissionState.Kind.GRANTED, AntSupportSnapshot.ServiceState.NO_ADAPTER,
                Collections.<AntAdapterSnapshot>emptyList());
        assertEquals(AntReadiness.SETUP_REQUIRED_ADAPTER, AntReadinessEvaluator.evaluate(snapshot).readiness);
    }

    @Test public void missingServiceIsSetupNotUnsupported() {
        AntSupportSnapshot snapshot = snapshot(true, false, missing(), missing(),
                PermissionState.Kind.NOT_DECLARED, AntSupportSnapshot.ServiceState.SERVICE_MISSING,
                Collections.<AntAdapterSnapshot>emptyList());
        assertEquals(AntReadiness.SETUP_REQUIRED_SERVICE, AntReadinessEvaluator.evaluate(snapshot).readiness);
    }

    @Test public void firmwareReadThroughTheHalDoesNotRequireAntRadioService() {
        AntSupportSnapshot snapshot = snapshot(true, false, missing(), missing(),
                PermissionState.Kind.NOT_DECLARED, AntSupportSnapshot.ServiceState.SERVICE_MISSING,
                Collections.<AntAdapterSnapshot>emptyList());
        snapshot = snapshot.withFirmware(snapshot.firmware.withBuiltIn(
                AntFirmwareFacts.BuiltInState.VERIFIED, "AUM1.04B22"));
        assertEquals(AntReadiness.VERIFIED_BUILT_IN, AntReadinessEvaluator.evaluate(snapshot).readiness);
    }

    @Test public void usbDetectionAndAFirmwareReplyAreDifferentStates() {
        AntSupportSnapshot snapshot = snapshot(false, true, missing(), missing(),
                PermissionState.Kind.NOT_DECLARED, AntSupportSnapshot.ServiceState.SERVICE_MISSING,
                Collections.<AntAdapterSnapshot>emptyList());
        AntSupportSnapshot detected = snapshot.withFirmware(snapshot.firmware.withUsb(
                Collections.<String, String>singletonMap("stick", null), false));
        assertEquals(AntReadiness.DETECTED_EXTERNAL_USB, AntReadinessEvaluator.evaluate(detected).readiness);
        AntSupportSnapshot verified = snapshot.withFirmware(snapshot.firmware.withUsb(
                Collections.singletonMap("stick", "AJK2.05RAF"), false));
        assertEquals(AntReadiness.VERIFIED_EXTERNAL_USB, AntReadinessEvaluator.evaluate(verified).readiness);
    }

    @Test public void missingPermissionMetadataDoesNotInvalidateAnAdapterReply() {
        AntSupportSnapshot snapshot = snapshot(false, true, installed(), installed(),
                PermissionState.Kind.NOT_DECLARED, AntSupportSnapshot.ServiceState.AVAILABLE,
                Collections.singletonList(new AntAdapterSnapshot("External", "1", false, "a")));
        assertEquals(AntReadiness.READY_EXTERNAL_USB, AntReadinessEvaluator.evaluate(snapshot).readiness);
    }

    @Test public void permissionIsBlockedSeparately() {
        AntSupportSnapshot snapshot = snapshot(true, false, installed(), installed(),
                PermissionState.Kind.DENIED, AntSupportSnapshot.ServiceState.NOT_QUERIED,
                Collections.<AntAdapterSnapshot>emptyList());
        assertEquals(AntReadiness.BLOCKED_PERMISSION, AntReadinessEvaluator.evaluate(snapshot).readiness);
    }

    @Test public void errorsAreNotUnsupported() {
        AntSupportSnapshot snapshot = snapshot(true, false, installed(), installed(),
                PermissionState.Kind.GRANTED, AntSupportSnapshot.ServiceState.TIMED_OUT,
                Collections.<AntAdapterSnapshot>emptyList());
        assertEquals(AntReadiness.UNKNOWN_ERROR, AntReadinessEvaluator.evaluate(snapshot).readiness);
    }

    @Test public void noRouteIsUnsupported() {
        AntSupportSnapshot snapshot = snapshot(false, false, missing(), missing(),
                PermissionState.Kind.NOT_DECLARED, AntSupportSnapshot.ServiceState.SERVICE_MISSING,
                Collections.<AntAdapterSnapshot>emptyList());
        assertEquals(AntReadiness.UNSUPPORTED, AntReadinessEvaluator.evaluate(snapshot).readiness);
    }

    private static PackageState installed() {
        return new PackageState("package", PackageState.Kind.INSTALLED_ENABLED, "1", 50000, null);
    }

    private static PackageState missing() {
        return new PackageState("package", PackageState.Kind.MISSING, null, 0, null);
    }

    private static AntSupportSnapshot snapshot(boolean builtIn, boolean usbHost, PackageState radio,
                                               PackageState usb, PermissionState.Kind permission,
                                               AntSupportSnapshot.ServiceState service,
                                               java.util.List<AntAdapterSnapshot> adapters) {
        return new AntSupportSnapshot(AntSupportSnapshot.ProbeStatus.COMPLETE, builtIn, usbHost, true,
                radio, usb, installed(), installed(), new PermissionState(permission, PERMISSION), service,
                adapters, Collections.<UsbDeviceSnapshot>emptyList(), Collections.<String>emptyList());
    }
}
