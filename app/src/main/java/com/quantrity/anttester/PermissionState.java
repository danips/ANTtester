package com.quantrity.anttester;

final class PermissionState {
    enum Kind { GRANTED, NOT_DECLARED, DENIED, QUERY_ERROR }
    final Kind kind;
    final String permission;

    PermissionState(Kind kind, String permission) {
        this.kind = kind;
        this.permission = permission;
    }

    boolean isGranted() { return kind == Kind.GRANTED; }
}
