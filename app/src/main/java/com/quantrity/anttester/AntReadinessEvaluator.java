package com.quantrity.anttester;

/** Pure product semantics for the top-level ANT readiness result. */
final class AntReadinessEvaluator {
    private AntReadinessEvaluator() { }

    static AntReadinessResult evaluate(AntSupportSnapshot snapshot) {
        if (snapshot == null || snapshot.probeStatus == AntSupportSnapshot.ProbeStatus.CHECKING) {
            return new AntReadinessResult(AntReadiness.CHECKING, "Checking ANT support");
        }
        if (snapshot.serviceState == AntSupportSnapshot.ServiceState.AVAILABLE) {
            if (hasBuiltIn(snapshot)) {
                return new AntReadinessResult(AntReadiness.READY_BUILT_IN, "Built-in ANT adapter ready");
            }
            if (snapshot.usbHostSupported && hasExternal(snapshot)
                    && (installed(snapshot.usbService) || snapshot.antAddOnSupported)) {
                return new AntReadinessResult(AntReadiness.READY_EXTERNAL_USB, "External ANT adapter ready");
            }
        }
        if (snapshot.firmware.builtInVersion != null) {
            return new AntReadinessResult(AntReadiness.VERIFIED_BUILT_IN, "Built-in ANT firmware verified");
        }
        if (snapshot.firmware.hasVerifiedUsb()) {
            return new AntReadinessResult(AntReadiness.VERIFIED_EXTERNAL_USB, "External ANT verified via USB");
        }
        if (snapshot.firmware.builtInState == AntFirmwareFacts.BuiltInState.CHECKING
                || snapshot.firmware.usbChecking
                || snapshot.serviceState == AntSupportSnapshot.ServiceState.QUERYING) {
            return new AntReadinessResult(AntReadiness.CHECKING, "Checking ANT support");
        }
        if (snapshot.firmware.builtInState == AntFirmwareFacts.BuiltInState.PERMISSION_REQUIRED) {
            return new AntReadinessResult(AntReadiness.BLOCKED_PERMISSION, "Allow ANT hardware permission");
        }
        if (!snapshot.firmware.usbVersions.isEmpty()) {
            return new AntReadinessResult(AntReadiness.DETECTED_EXTERNAL_USB, "Detected via USB");
        }
        if (snapshot.firmware.builtInState == AntFirmwareFacts.BuiltInState.FAILED) {
            return new AntReadinessResult(AntReadiness.UNKNOWN_ERROR, "Could not verify ANT support");
        }
        if (snapshot.probeStatus == AntSupportSnapshot.ProbeStatus.UNKNOWN_ERROR
                || hasQueryError(snapshot)) {
            return new AntReadinessResult(AntReadiness.UNKNOWN_ERROR, "Could not verify ANT support");
        }
        if (snapshot.serviceState == AntSupportSnapshot.ServiceState.SERVICE_TOO_OLD
                || snapshot.radioService != null
                && snapshot.radioService.kind == PackageState.Kind.VERSION_TOO_OLD) {
            return new AntReadinessResult(AntReadiness.SETUP_REQUIRED_SERVICE,
                    "Install a newer ANT Radio Service");
        }
        if (snapshot.builtInFeatureDeclared && snapshot.antPermission.kind == PermissionState.Kind.DENIED
                && installed(snapshot.radioService)) {
            return new AntReadinessResult(AntReadiness.BLOCKED_PERMISSION,
                    "Allow ANT hardware permission");
        }
        if (snapshot.serviceState == AntSupportSnapshot.ServiceState.PERMISSION_BLOCKED) {
            return new AntReadinessResult(AntReadiness.BLOCKED_PERMISSION,
                    "Allow ANT hardware permission");
        }
        if (snapshot.serviceState == AntSupportSnapshot.ServiceState.BIND_FAILED
                || snapshot.serviceState == AntSupportSnapshot.ServiceState.QUERY_ERROR
                || snapshot.serviceState == AntSupportSnapshot.ServiceState.TIMED_OUT) {
            return new AntReadinessResult(AntReadiness.UNKNOWN_ERROR,
                    "ANT Radio Service could not be queried");
        }
        if (!snapshot.builtInFeatureDeclared && !snapshot.usbHostSupported) {
            return new AntReadinessResult(AntReadiness.UNSUPPORTED, "No ANT hardware route detected");
        }
        if (snapshot.radioService == null || snapshot.radioService.kind == PackageState.Kind.MISSING
                || snapshot.radioService.kind == PackageState.Kind.NOT_VISIBLE
                || snapshot.radioService.kind == PackageState.Kind.INSTALLED_DISABLED
                || snapshot.serviceState == AntSupportSnapshot.ServiceState.SERVICE_MISSING
                || snapshot.serviceState == AntSupportSnapshot.ServiceState.SERVICE_DISABLED) {
            return new AntReadinessResult(AntReadiness.SETUP_REQUIRED_SERVICE,
                    "Install or enable ANT Radio Service");
        }
        if (snapshot.usbHostSupported && hasExternal(snapshot)) {
            return new AntReadinessResult(AntReadiness.SETUP_REQUIRED_SERVICE, "Install ANT USB Service");
        }
        if (snapshot.serviceState == AntSupportSnapshot.ServiceState.NO_ADAPTER
                || snapshot.serviceState == AntSupportSnapshot.ServiceState.NOT_QUERIED
                || snapshot.adapters.isEmpty()) {
            return new AntReadinessResult(AntReadiness.SETUP_REQUIRED_ADAPTER,
                    "Connect a compatible ANT adapter");
        }
        return new AntReadinessResult(AntReadiness.UNKNOWN_ERROR, "ANT support facts are contradictory");
    }

    private static boolean hasBuiltIn(AntSupportSnapshot snapshot) {
        for (AntAdapterSnapshot adapter : snapshot.adapters) if (adapter.builtIn) return true;
        return false;
    }

    private static boolean hasExternal(AntSupportSnapshot snapshot) {
        for (AntAdapterSnapshot adapter : snapshot.adapters) if (!adapter.builtIn) return true;
        return false;
    }

    private static boolean hasQueryError(AntSupportSnapshot snapshot) {
        return packageError(snapshot.radioService) || snapshot.usbHostSupported && packageError(snapshot.usbService);
    }

    private static boolean packageError(PackageState state) {
        return state != null && state.kind == PackageState.Kind.QUERY_ERROR;
    }

    private static boolean installed(PackageState state) {
        return state != null && state.isInstalledEnabled();
    }
}
