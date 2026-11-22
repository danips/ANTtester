// Copyright 2011 Dynastream Innovations
// SPDX-License-Identifier: Apache-2.0
// Binder contract from https://github.com/ant-wireless/Android_ANTHALService
package com.dsi.ant.server;

import com.dsi.ant.server.IAntHalCallback;

interface IAntHal {
    int setAntState(int state);
    int getAntState();
    int ANTTxMessage(in byte[] message);
    int registerAntHalCallback(IAntHalCallback callback);
    int unregisterAntHalCallback(IAntHalCallback callback);
    int getServiceLibraryVersionCode();
    String getServiceLibraryVersionName();
}
