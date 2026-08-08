package com.quantrity.anttester;

import static org.junit.Assert.assertEquals;

import android.content.pm.PackageInfo;

import org.junit.Test;

public class PermissionInspectorTest {
    private static final String PERMISSION = PermissionInspector.ANT_HARDWARE_PERMISSION;

    @Test public void exactPermissionMustBeGranted() {
        PackageInfo info = info(new String[]{"com.dsi.ant.permission.ANT_OTHER", PERMISSION},
                new int[]{PackageInfo.REQUESTED_PERMISSION_GRANTED, 0});
        assertEquals(PermissionState.Kind.DENIED, PermissionInspector.inspect(info).kind);
        info.requestedPermissionsFlags[1] = PackageInfo.REQUESTED_PERMISSION_GRANTED;
        assertEquals(PermissionState.Kind.GRANTED, PermissionInspector.inspect(info).kind);
    }

    @Test public void nullAndMismatchedArraysAreNotExceptions() {
        assertEquals(PermissionState.Kind.NOT_DECLARED, PermissionInspector.inspect(info(null, null)).kind);
        assertEquals(PermissionState.Kind.DENIED,
                PermissionInspector.inspect(info(new String[]{PERMISSION}, new int[0])).kind);
    }

    private static PackageInfo info(String[] permissions, int[] flags) {
        PackageInfo info = new PackageInfo();
        info.requestedPermissions = permissions;
        info.requestedPermissionsFlags = flags;
        return info;
    }
}
