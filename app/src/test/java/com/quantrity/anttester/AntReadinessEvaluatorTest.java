package com.quantrity.anttester;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
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
                Collections.singletonList(new AntAdapterSnapshot(AntAdapterSnapshot.Kind.BUILT_IN, "1", "a")));
        assertEquals(AntReadiness.READY_BUILT_IN, AntReadinessEvaluator.evaluate(snapshot).readiness);
    }

    @Test public void usbRouteIsReadyOnlyWithExternalAdapterAndUsbService() {
        AntSupportSnapshot snapshot = snapshot(false, true, installed(), installed(),
                PermissionState.Kind.GRANTED, AntSupportSnapshot.ServiceState.AVAILABLE,
                Collections.singletonList(new AntAdapterSnapshot(AntAdapterSnapshot.Kind.EXTERNAL, "1", "a")));
        assertEquals(AntReadiness.READY_EXTERNAL_USB, AntReadinessEvaluator.evaluate(snapshot).readiness);
    }

    @Test public void unclassifiedAdapterCannotEstablishEitherReadinessRoute() {
        AntAdapterSnapshot adapter = new AntAdapterSnapshot(AntAdapterSnapshot.Kind.UNKNOWN, "1", "a");
        AntSupportSnapshot snapshot = snapshot(false, true, installed(), installed(),
                PermissionState.Kind.GRANTED, AntSupportSnapshot.ServiceState.AVAILABLE,
                Collections.singletonList(adapter));
        assertFalse(snapshot.hasBuiltInAnt());
        assertEquals(AntReadiness.UNKNOWN_ERROR, AntReadinessEvaluator.evaluate(snapshot).readiness);

        AntSupportSnapshot withBuiltInFeature = snapshot(true, true, installed(), installed(),
                PermissionState.Kind.GRANTED, AntSupportSnapshot.ServiceState.AVAILABLE,
                Collections.singletonList(adapter));
        assertTrue(withBuiltInFeature.hasBuiltInAnt());
        assertEquals(AntReadiness.UNKNOWN_ERROR, AntReadinessEvaluator.evaluate(withBuiltInFeature).readiness);
    }

    @Test public void adapterDiscoveryPreservesThePackagePermissionWarning() {
        AntSupportSnapshot beforeDiscovery = snapshot(true, true, installed(), installed(),
                PermissionState.Kind.DENIED, AntSupportSnapshot.ServiceState.QUERYING,
                Collections.<AntAdapterSnapshot>emptyList());
        AntSupportSnapshot discovered = beforeDiscovery.withService(AntSupportSnapshot.ServiceState.AVAILABLE,
                Arrays.asList(new AntAdapterSnapshot(AntAdapterSnapshot.Kind.UNKNOWN, null, "a"),
                        new AntAdapterSnapshot(AntAdapterSnapshot.Kind.EXTERNAL, "AJK2.05RAF", "b")), null);
        assertTrue(beforeDiscovery.hasAntPermissionWarning());
        assertTrue(discovered.hasAntPermissionWarning());
        assertEquals(AntReadiness.READY_EXTERNAL_USB, AntReadinessEvaluator.evaluate(discovered).readiness);
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
        assertTrue(snapshot.allowsDirectBuiltInProbe());
        snapshot = snapshot.withFirmware(snapshot.firmware.withBuiltIn(
                AntFirmwareFacts.BuiltInState.VERIFIED, "AUM1.04B22"));
        assertEquals(AntReadiness.VERIFIED_BUILT_IN, AntReadinessEvaluator.evaluate(snapshot).readiness);
    }

    @Test public void disabledRadioServiceLeavesTheBuiltInHalRouteAvailable() {
        PackageState disabled = new PackageState("radio", PackageState.Kind.INSTALLED_DISABLED, "3.0.0", 30000, null);
        AntSupportSnapshot snapshot = snapshot(true, false, disabled, missing(),
                PermissionState.Kind.NOT_DECLARED, AntSupportSnapshot.ServiceState.SERVICE_DISABLED,
                Collections.<AntAdapterSnapshot>emptyList());
        assertTrue(snapshot.allowsDirectBuiltInProbe());
        AntSupportSnapshot verified = snapshot.withFirmware(snapshot.firmware.withBuiltIn(
                AntFirmwareFacts.BuiltInState.VERIFIED, "AUM1.04B22"));
        assertEquals(AntReadiness.VERIFIED_BUILT_IN, AntReadinessEvaluator.evaluate(verified).readiness);
    }

    @Test public void installedOrUnverifiedRadioServiceDoesNotStartTheDirectHalProbe() {
        for (PackageState.Kind kind : new PackageState.Kind[] {PackageState.Kind.INSTALLED_ENABLED,
                PackageState.Kind.VERSION_TOO_OLD, PackageState.Kind.NOT_VISIBLE, PackageState.Kind.QUERY_ERROR}) {
            PackageState radio = new PackageState("radio", kind, null, 0, null);
            AntSupportSnapshot snapshot = snapshot(true, false, radio, missing(),
                    PermissionState.Kind.NOT_DECLARED, AntSupportSnapshot.ServiceState.NOT_QUERIED,
                    Collections.<AntAdapterSnapshot>emptyList());
            assertFalse(kind.name(), snapshot.allowsDirectBuiltInProbe());
        }
        assertFalse(snapshot(false, true, missing(), missing(), PermissionState.Kind.NOT_DECLARED,
                AntSupportSnapshot.ServiceState.SERVICE_MISSING,
                Collections.<AntAdapterSnapshot>emptyList()).allowsDirectBuiltInProbe());
    }

    @Test public void usesTheLegacyRouteOnlyWhenTheBuiltInRadioIsUnambiguous() {
        PackageState old = new PackageState("radio", PackageState.Kind.VERSION_TOO_OLD, "3.0.0", 30000, null);
        AntSupportSnapshot snapshot = legacySnapshot(old, true, false);
        assertTrue(snapshot.allowsLegacyBuiltInProbe(16, false));
        assertFalse(snapshot.allowsLegacyBuiltInProbe(23, false));
        assertFalse(snapshot.allowsLegacyBuiltInProbe(16, true));
        assertFalse(legacySnapshot(old, true, true).allowsLegacyBuiltInProbe(16, false));
        assertFalse(legacySnapshot(old, false, false).allowsLegacyBuiltInProbe(16, false));
        assertFalse(legacySnapshot(installed(), true, false).allowsLegacyBuiltInProbe(16, false));
    }

    private static AntSupportSnapshot legacySnapshot(PackageState radio, boolean builtIn, boolean addOn) {
        return new AntSupportSnapshot(AntSupportSnapshot.ProbeStatus.COMPLETE, builtIn, false, addOn,
                radio, missing(), installed(), installed(),
                new PermissionState(PermissionState.Kind.GRANTED, PERMISSION),
                AntSupportSnapshot.ServiceState.SERVICE_TOO_OLD,
                Collections.<AntAdapterSnapshot>emptyList(), Collections.<UsbDeviceSnapshot>emptyList(),
                Collections.<String>emptyList());
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
                Collections.singletonList(new AntAdapterSnapshot(AntAdapterSnapshot.Kind.EXTERNAL, "1", "a")));
        assertEquals(AntReadiness.READY_EXTERNAL_USB, AntReadinessEvaluator.evaluate(snapshot).readiness);
        assertFalse(snapshot.hasAntPermissionWarning());
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
