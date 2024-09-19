package com.android.server.security;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.security.IKeyboxService;
import android.util.Slog;

public class KeyboxService extends IKeyboxService.Stub {
    private static final String TAG = "KeyboxService";
    private String[] keyboxData;

    public KeyboxService(Context context) {
        // Constructor can be used for initialization if needed
    }

    @Override
    public void setKeyboxData(String[] keyboxData) throws RemoteException {
        // TODO: Add permission checks to ensure only authorized apps can set data
        // if (getContext().checkCallingPermission("android.permission.ACCESS_KEYBOX_SERVICE") != PackageManager.PERMISSION_GRANTED) {
        //     throw new SecurityException("Requires ACCESS_KEYBOX_SERVICE permission");
        // }
        this.keyboxData = keyboxData;
        Slog.i(TAG, "Keybox data set by client");
    }

    @Override
    public String[] getKeyboxData() throws RemoteException {
        if (keyboxData == null) {
            Slog.w(TAG, "Keybox data is null");
        }
        return keyboxData;
    }
}
