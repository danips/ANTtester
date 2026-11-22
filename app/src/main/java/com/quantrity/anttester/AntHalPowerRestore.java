package com.quantrity.anttester;

import android.os.RemoteException;

import com.dsi.ant.server.IAntHal;

final class AntHalPowerRestore {
    static final int SUCCESS = 1;
    static final int STATE_ENABLING = 1;
    static final int STATE_ENABLED = 2;
    static final int STATE_DISABLING = 3;
    static final int STATE_DISABLED = 4;

    interface WaitForTransition {
        boolean awaitNextPoll();
    }

    private AntHalPowerRestore() { }

    static boolean restore(IAntHal hal, boolean sawEnable, WaitForTransition wait)
            throws RemoteException {
        boolean disableAccepted = false;
        do {
            int state = hal.getAntState();
            // An accepted enable request can still report DISABLED before startup begins.
            if (state == STATE_DISABLED && sawEnable) return true;
            if (state == STATE_ENABLING || state == STATE_ENABLED || state == STATE_DISABLING) {
                sawEnable = true;
            }
            if (state == STATE_ENABLED && !disableAccepted) {
                disableAccepted = hal.setAntState(STATE_DISABLED) == SUCCESS;
            }
        } while (wait.awaitNextPoll());
        return false;
    }
}
