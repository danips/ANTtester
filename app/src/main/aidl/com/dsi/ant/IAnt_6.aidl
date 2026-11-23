// Copyright 2011 Dynastream Innovations Inc.
// SPDX-License-Identifier: Apache-2.0
// Transaction IDs from the legacy ANT Android SDK IAnt_6 interface.
package com.dsi.ant;

interface IAnt_6 {
    boolean enable() = 0;
    boolean disable() = 1;
    boolean isEnabled() = 2;
    boolean ANTTxMessage(in byte[] message) = 3;
    boolean claimInterface() = 29;
    boolean releaseInterface() = 32;
}
