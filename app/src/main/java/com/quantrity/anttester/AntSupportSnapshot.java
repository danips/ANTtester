package com.quantrity.anttester;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Complete immutable facts used to derive the top-level ANT result. */
final class AntSupportSnapshot {
    enum ProbeStatus { CHECKING, COMPLETE, UNKNOWN_ERROR }
    enum ServiceState { NOT_QUERIED, QUERYING, AVAILABLE, NO_ADAPTER, SERVICE_MISSING, SERVICE_DISABLED,
        SERVICE_TOO_OLD, PERMISSION_BLOCKED, BIND_FAILED, TIMED_OUT, QUERY_ERROR }

    final ProbeStatus probeStatus;
    final boolean builtInFeatureDeclared;
    final boolean usbHostSupported;
    final boolean antAddOnSupported;
    final PackageState radioService;
    final PackageState usbService;
    final PackageState pluginsService;
    final PackageState halService;
    final PermissionState antPermission;
    final ServiceState serviceState;
    final List<AntAdapterSnapshot> adapters;
    final List<UsbDeviceSnapshot> usbDevices;
    final List<String> issues;
    final AntFirmwareFacts firmware;

    AntSupportSnapshot(ProbeStatus probeStatus, boolean builtInFeatureDeclared, boolean usbHostSupported,
                       boolean antAddOnSupported, PackageState radioService, PackageState usbService,
                       PackageState pluginsService, PackageState halService, PermissionState antPermission,
                       ServiceState serviceState, List<AntAdapterSnapshot> adapters,
                       List<UsbDeviceSnapshot> usbDevices, List<String> issues) {
        this(probeStatus, builtInFeatureDeclared, usbHostSupported, antAddOnSupported, radioService,
                usbService, pluginsService, halService, antPermission, serviceState, adapters,
                usbDevices, issues, AntFirmwareFacts.empty());
    }

    private AntSupportSnapshot(ProbeStatus probeStatus, boolean builtInFeatureDeclared, boolean usbHostSupported,
                               boolean antAddOnSupported, PackageState radioService, PackageState usbService,
                               PackageState pluginsService, PackageState halService, PermissionState antPermission,
                               ServiceState serviceState, List<AntAdapterSnapshot> adapters,
                               List<UsbDeviceSnapshot> usbDevices, List<String> issues, AntFirmwareFacts firmware) {
        this.probeStatus = probeStatus;
        this.builtInFeatureDeclared = builtInFeatureDeclared;
        this.usbHostSupported = usbHostSupported;
        this.antAddOnSupported = antAddOnSupported;
        this.radioService = radioService;
        this.usbService = usbService;
        this.pluginsService = pluginsService;
        this.halService = halService;
        this.antPermission = antPermission;
        this.serviceState = serviceState;
        this.adapters = immutable(adapters);
        this.usbDevices = immutable(usbDevices);
        this.issues = immutable(issues);
        this.firmware = firmware;
    }

    static AntSupportSnapshot checking() {
        return new AntSupportSnapshot(ProbeStatus.CHECKING, false, false, false, null, null, null, null,
                new PermissionState(PermissionState.Kind.NOT_DECLARED, "com.dsi.ant.permission.ANT"),
                ServiceState.NOT_QUERIED, Collections.<AntAdapterSnapshot>emptyList(),
                Collections.<UsbDeviceSnapshot>emptyList(), Collections.<String>emptyList());
    }

    AntSupportSnapshot withService(ServiceState state, List<AntAdapterSnapshot> adapterResults, String issue) {
        ArrayList<String> nextIssues = new ArrayList<String>(issues);
        if (issue != null) nextIssues.add(issue);
        return new AntSupportSnapshot(probeStatus, builtInFeatureDeclared, usbHostSupported, antAddOnSupported,
                radioService, usbService, pluginsService, halService, antPermission, state, adapterResults,
                usbDevices, nextIssues, firmware);
    }

    AntSupportSnapshot withFirmware(AntFirmwareFacts readings) {
        return new AntSupportSnapshot(probeStatus, builtInFeatureDeclared, usbHostSupported, antAddOnSupported,
                radioService, usbService, pluginsService, halService, antPermission, serviceState, adapters,
                usbDevices, issues, readings);
    }

    boolean hasAdapter(AntAdapterSnapshot.Kind kind) {
        for (AntAdapterSnapshot adapter : adapters) {
            if (adapter.kind == kind) return true;
        }
        return false;
    }

    boolean hasBuiltInAnt() {
        return builtInFeatureDeclared || firmware.builtInVersion != null
                || hasAdapter(AntAdapterSnapshot.Kind.BUILT_IN);
    }

    boolean hasAntPermissionWarning() {
        return serviceState == ServiceState.PERMISSION_BLOCKED
                || (hasBuiltInAnt() && radioService != null && radioService.isInstalledEnabled()
                && antPermission.kind == PermissionState.Kind.DENIED);
    }

    boolean allowsDirectBuiltInProbe() {
        return builtInFeatureDeclared && radioService != null
                && (radioService.kind == PackageState.Kind.MISSING
                || radioService.kind == PackageState.Kind.INSTALLED_DISABLED);
    }

    boolean allowsLegacyBuiltInProbe(int sdkVersion, boolean usbDevicesPresent) {
        // The legacy interface does not identify which adapter answered the version query.
        return sdkVersion < 23 && builtInFeatureDeclared && !usbDevicesPresent && !antAddOnSupported
                && radioService != null && radioService.kind == PackageState.Kind.VERSION_TOO_OLD;
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values == null
                ? Collections.<T>emptyList() : values));
    }
}
