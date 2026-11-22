package com.quantrity.anttester;

import android.annotation.TargetApi;
import android.content.pm.PackageInfo;

/** Exact ANT Radio Service permission interpretation. */
final class PermissionInspector {
    static final String ANT_HARDWARE_PERMISSION = "com.dsi.ant.permission.ANT";

    private PermissionInspector() { }

    @TargetApi(16)
    static PermissionState inspect(PackageInfo packageInfo) {
        return packageInfo == null ? inspect(null, null)
                : inspect(packageInfo.requestedPermissions, packageInfo.requestedPermissionsFlags);
    }

    static PermissionState inspect(String[] requested, int[] flags) {
        if (requested == null || flags == null) {
            return new PermissionState(PermissionState.Kind.NOT_DECLARED, ANT_HARDWARE_PERMISSION);
        }
        PermissionState fallback = new PermissionState(PermissionState.Kind.NOT_DECLARED, ANT_HARDWARE_PERMISSION);
        for (int i = 0; i < requested.length; i++) {
            String permission = requested[i];
            if (AntHalFirmwareProbe.PERMISSION.equals(permission) || ANT_HARDWARE_PERMISSION.equals(permission)) {
                PermissionState.Kind kind = i >= flags.length ? PermissionState.Kind.NOT_DECLARED
                        : (flags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                        ? PermissionState.Kind.GRANTED : PermissionState.Kind.DENIED;
                PermissionState result = new PermissionState(kind, permission);
                // The radio service uses ANTRADIO to bind the built-in HAL when it declares both.
                if (AntHalFirmwareProbe.PERMISSION.equals(permission)) return result;
                fallback = result;
            }
        }
        return fallback;
    }
}
