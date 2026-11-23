package com.quantrity.anttester;

import static org.junit.Assert.assertEquals;

import android.content.pm.PackageInfo;

import org.junit.Test;

public class PermissionInspectorTest {
    private static final String PERMISSION = PermissionInspector.ANT_HARDWARE_PERMISSION;

    @Test public void exactPermissionMustBeGranted() {
        String[] permissions = {"com.dsi.ant.permission.ANT_OTHER", PERMISSION};
        int[] flags = {PackageInfo.REQUESTED_PERMISSION_GRANTED, 0};
        assertEquals(PermissionState.Kind.DENIED, PermissionInspector.inspect(permissions, flags).kind);
        flags[1] = PackageInfo.REQUESTED_PERMISSION_GRANTED;
        assertEquals(PermissionState.Kind.GRANTED, PermissionInspector.inspect(permissions, flags).kind);
    }

    @Test public void incompleteMetadataIsNotAnExplicitDenial() {
        assertEquals(PermissionState.Kind.NOT_DECLARED, PermissionInspector.inspect(null, null).kind);
        assertEquals(PermissionState.Kind.NOT_DECLARED,
                PermissionInspector.inspect(new String[]{PERMISSION}, new int[0]).kind);
    }

    @Test public void usesTheHalPermissionWhenTheRadioServiceDeclaresBoth() {
        PermissionState result = PermissionInspector.inspect(
                new String[]{PERMISSION, BuiltInAntFirmwareProbe.HAL_PERMISSION},
                new int[]{PackageInfo.REQUESTED_PERMISSION_GRANTED, 0});
        assertEquals(BuiltInAntFirmwareProbe.HAL_PERMISSION, result.permission);
        assertEquals(PermissionState.Kind.DENIED, result.kind);
    }
}
