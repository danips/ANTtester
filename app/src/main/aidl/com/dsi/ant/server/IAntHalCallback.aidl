// Copyright 2011 Dynastream Innovations
// SPDX-License-Identifier: Apache-2.0
// Binder contract from https://github.com/ant-wireless/Android_ANTHALService
package com.dsi.ant.server;

interface IAntHalCallback {
    void antHalStateChanged(int state);
    void antHalRxMessage(in byte[] message);
}
