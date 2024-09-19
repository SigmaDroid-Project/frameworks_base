package com.android.internal.util.crdroid;

import android.os.RemoteException;
import android.os.ServiceManager;
import android.security.IKeyboxService;
import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Manager class for handling keybox providers.
 * @hide
 */
public final class KeyProviderManager {
    private static final String TAG = "KeyProviderManager";

    private static final IKeyboxProvider PROVIDER = new DefaultKeyboxProvider();

    private KeyProviderManager() {
    }

    public static IKeyboxProvider getProvider() {
        return PROVIDER;
    }

    public static boolean isKeyboxAvailable() {
        return PROVIDER.hasKeybox();
    }

    private static class DefaultKeyboxProvider implements IKeyboxProvider {
        private final Map<String, String> keyboxData = new HashMap<>();

        private DefaultKeyboxProvider() {
            loadKeyboxData();
        }

        private void loadKeyboxData() {
            IKeyboxService keyboxService = IKeyboxService.Stub.asInterface(
                ServiceManager.getService("keybox"));
        
            if (keyboxService != null) {
                try {
                    String[] keyboxArray = null;
                    int retryCount = 0;
                    final int MAX_RETRIES = 10;
                    final int RETRY_DELAY_MS = 1000;
        
                    // Retry until data is available or max retries reached
                    while ((keyboxArray == null || keyboxArray.length == 0) && retryCount < MAX_RETRIES) {
                        keyboxArray = keyboxService.getKeyboxData();
                        if (keyboxArray == null || keyboxArray.length == 0) {
                            Log.w(TAG, "Keybox data not yet available, retrying...");
                            Thread.sleep(RETRY_DELAY_MS);
                            retryCount++;
                        } else {
                            break;
                        }
                    }
        
                    if (keyboxArray == null || keyboxArray.length == 0) {
                        Log.e(TAG, "Failed to retrieve Keybox data after retries");
                        return;
                    }
        
                    Arrays.stream(keyboxArray)
                            .map(entry -> entry.split(":", 2))
                            .filter(parts -> parts.length == 2)
                            .forEach(parts -> keyboxData.put(parts[0], parts[1]));
        
                    if (!hasKeybox()) {
                        Log.w(TAG, "Incomplete keybox data loaded from KeyboxService");
                    }
        
                } catch (RemoteException | InterruptedException e) {
                    Log.e(TAG, "Error accessing KeyboxService", e);
                }
            } else {
                Log.e(TAG, "KeyboxService is not available");
            }
        }
        
        @Override
        public boolean hasKeybox() {
            return Arrays.asList("EC.PRIV", "EC.CERT_1", "EC.CERT_2", "EC.CERT_3",
                    "RSA.PRIV", "RSA.CERT_1", "RSA.CERT_2", "RSA.CERT_3")
                    .stream()
                    .allMatch(keyboxData::containsKey);
        }

        @Override
        public String getEcPrivateKey() {
            return keyboxData.get("EC.PRIV");
        }

        @Override
        public String getRsaPrivateKey() {
            return keyboxData.get("RSA.PRIV");
        }

        @Override
        public String[] getEcCertificateChain() {
            return getCertificateChain("EC");
        }

        @Override
        public String[] getRsaCertificateChain() {
            return getCertificateChain("RSA");
        }

        private String[] getCertificateChain(String prefix) {
            return new String[]{
                    keyboxData.get(prefix + ".CERT_1"),
                    keyboxData.get(prefix + ".CERT_2"),
                    keyboxData.get(prefix + ".CERT_3")
            };
        }
    }
}
