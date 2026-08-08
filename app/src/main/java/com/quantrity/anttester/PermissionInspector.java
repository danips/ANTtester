package com.quantrity.anttester;

import android.annotation.TargetApi;
import android.content.pm.PackageInfo;

/** Exact ANT Radio Service permission interpretation. */
final class PermissionInspector {
    static final String ANT_HARDWARE_PERMISSION = "com.dsi.ant.permission.ANT";

    private PermissionInspector() { }

    @TargetApi(16)
    static PermissionState inspect(PackageInfo packageInfo) {
        if (packageInfo == null || packageInfo.requestedPermissions == null
                || packageInfo.requestedPermissionsFlags == null) {
            return new PermissionState(PermissionState.Kind.NOT_DECLARED, ANT_HARDWARE_PERMISSION);
        }
        String[] requested = packageInfo.requestedPermissions;
        int[] flags = packageInfo.requestedPermissionsFlags;
        for (int i = 0; i < requested.length; i++) {
            if (ANT_HARDWARE_PERMISSION.equals(requested[i])) {
                return new PermissionState(i < flags.length
                        && (flags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                        ? PermissionState.Kind.GRANTED : PermissionState.Kind.DENIED,
                        ANT_HARDWARE_PERMISSION);
            }
        }
        return new PermissionState(PermissionState.Kind.NOT_DECLARED, ANT_HARDWARE_PERMISSION);
    }
}
