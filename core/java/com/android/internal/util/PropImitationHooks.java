/*
 * Copyright (C) 2022 Paranoid Android
 * Copyright (C) 2022 StatiXOS
 * Copyright (C) 2023 the RisingOS Android Project
 *           (C) 2023 ArrowOS
 *           (C) 2023 The LibreMobileOS Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util;

import android.app.ActivityTaskManager;
import android.app.Application;
import android.app.TaskStackListener;
import android.content.Context;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Binder;
import android.os.Process;
import android.os.Build.VERSION;
import android.os.SystemProperties;
import android.util.Log;

import com.android.internal.R;
import com.android.internal.util.pixeldust.PixeldustUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.util.Random;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class PropImitationHooks {

    private static final String TAG = "PropImitationHooks";
    private static final boolean DEBUG = true;
    
    private static final String PRODUCT_DEVICE = "ro.product.device";

    private static final String sMainFP = "google/felix/felix:14/UP1A.231105.003/11010452:user/release-keys";
    private static final String sMainModel = "Pixel Fold";
    private static final String sStockFp = SystemProperties.get("ro.vendor.build.fingerprint");

    private static final String PACKAGE_ARCORE = "com.google.ar.core";
    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PROCESS_GMS_UNSTABLE = PACKAGE_GMS + ".unstable";
    private static final String PACKAGE_GPHOTOS = "com.google.android.apps.photos";
    private static final String PACKAGE_GCAM = "com.google.android.GoogleCamera";
    private static final String PACKAGE_SNAPCHAT = "com.snapchat.android";

    private static final String PACKAGE_SUBSCRIPTION_RED = "com.google.android.apps.subscriptions.red";
    private static final String PACKAGE_TURBO = "com.google.android.apps.turbo";
    private static final String PACKAGE_VELVET = "com.google.android.googlequicksearchbox";
    private static final String PACKAGE_GBOARD = "com.google.android.inputmethod.latin";
    private static final String PACKAGE_SETUPWIZARD = "com.google.android.setupwizard";
    private static final String PACKAGE_EMOJI_WALLPAPER = "com.google.android.apps.emojiwallpaper";
    private static final String PACKAGE_CINEMATIC_PHOTOS = "com.google.android.wallpaper.effects";
    private static final String PACKAGE_GOOGLE_WALLPAPERS = "com.google.android.wallpaper";

    private static final ComponentName GMS_ADD_ACCOUNT_ACTIVITY = ComponentName.unflattenFromString(
            "com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity");

    private static final Map<String, Object> sMainSpoofProps = createGoogleSpoofProps(sMainModel, sMainFP);
    private static final Map<String, Object> gPhotosProps = createGoogleSpoofProps("Pixel XL", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys");

    private static Map<String, Object> createGameProps(String model, String manufacturer) {
        Map<String, Object> props = new HashMap<>();
        props.put("MODEL", model);
        props.put("MANUFACTURER", manufacturer);
        return props;
    }

    private static Map<String, Object> createGoogleSpoofProps(String model, String fingerprint) {
        Map<String, Object> props = new HashMap<>();
        props.put("BRAND", "google");
        props.put("MANUFACTURER", "Google");
        props.put("ID", getBuildID(fingerprint));
        props.put("DEVICE", getDeviceName(fingerprint));
        props.put("PRODUCT", getDeviceName(fingerprint));
        props.put("MODEL", model);
        props.put("FINGERPRINT", fingerprint);
        props.put("TYPE", "user");
        props.put("TAGS", "release-keys");
        return props;
    }

    private static String getDeviceName(String fingerprint) {
        String[] parts = fingerprint.split("/");
        if (parts.length >= 2) {
            return parts[1];
        } else {
            return "";
        }
    }

    private static String getBuildID(String fingerprint) {
        Pattern pattern = Pattern.compile("([A-Za-z0-9]+\\.\\d+\\.\\d+\\.\\w+)");
        Matcher matcher = pattern.matcher(fingerprint);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static volatile boolean sIsGms, sIsFinsky, sIsSetupWizard;
    private static volatile String sProcessName;

    public static void setProps(Context context, Application app) {
        final String packageName = app.getPackageName();
        final String processName = app.getProcessName();
        if (app == null || packageName == null || processName == null) {
            return;
        }
        sProcessName = processName;
        sIsGms = packageName.equals(PACKAGE_GMS) && processName.equals(PROCESS_GMS_UNSTABLE);
        sIsFinsky = packageName.equals(PACKAGE_FINSKY);
        sIsSetupWizard = packageName.equals(PACKAGE_SETUPWIZARD);

        if (sIsGms) {
            if (shouldTryToCertifyDevice()) {
                dlog("Spoofing build for GMS");
                spoofBuildGms(context);
            }
        } else {
            switch (packageName) {
                case PACKAGE_SUBSCRIPTION_RED:
                case PACKAGE_TURBO:
                case PACKAGE_VELVET:
                case PACKAGE_GBOARD:
                case PACKAGE_SETUPWIZARD:
                case PACKAGE_GMS:
                case PACKAGE_GCAM:
                case PACKAGE_CINEMATIC_PHOTOS:
                case PACKAGE_GOOGLE_WALLPAPERS:
                case PACKAGE_EMOJI_WALLPAPER:
                    if (packageName.equals(PACKAGE_GCAM) && !SystemProperties.getBoolean("persist.sys.pixelprops.gcam", false)) {
                        dlog("Not spoofing as " + sMainModel + " for: " + packageName);
                        break;
                    }
                    dlog("Spoofing as " + sMainModel + " for: " + packageName);
                    sMainSpoofProps.forEach((k, v) -> setPropValue(k, v));
                    setPropValue("TIME", System.currentTimeMillis());
                    break;
                case PACKAGE_ARCORE:
                    dlog("Setting stock fingerprint for: " + packageName);
                    setPropValue("FINGERPRINT", sStockFp);
                    break;
                case PACKAGE_SNAPCHAT:
                    dlog("Spoofing build for: " + packageName);
                    spoofBuildGms(context);
                    break;
                case PACKAGE_GPHOTOS:
                    if (SystemProperties.getBoolean("persist.sys.pixelprops.gphotos", true)) {
                        dlog("Spoofing as Pixel XL for: " + packageName);
                        gPhotosProps.forEach((k, v) -> setPropValue(k, v));
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static boolean shouldTryToCertifyDevice() {
        final boolean[] shouldCertify = {true};
        final boolean was = isGmsAddAccountActivityOnTop();
        final String reason = "GmsAddAccountActivityOnTop";
        dlog("Skip spoofing build for GMS, because " + reason + "!");
        TaskStackListener taskStackListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() {
                final boolean isNow = isGmsAddAccountActivityOnTop();
                if (isNow ^ was) {
                    dlog(String.format("%s changed: isNow=%b, was=%b, killing myself!", reason, isNow, was));
                    shouldCertify[0] = false;
                }
            }
        };
        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register task stack listener!", e);
        }
        if (shouldCertify[0]) {
            try {
                ActivityTaskManager.getService().unregisterTaskStackListener(taskStackListener); // this will be registered on next query
            } catch (Exception e) {}
        }
        return shouldCertify[0];
    }

    private static boolean isGmsAddAccountActivityOnTop() {
        try {
            final ActivityTaskManager.RootTaskInfo focusedTask =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            return focusedTask != null && focusedTask.topActivity != null
                    && focusedTask.topActivity.equals(GMS_ADD_ACCOUNT_ACTIVITY);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get top activity!", e);
        }
        return false;
    }

    private static void setPropValue(String key, Object value) {
        try {
            dlog("Setting prop " + key + " to " + value.toString());
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static void setVersionField(String key, Integer value) {
        try {
            // Unlock
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            // Edit
            field.set(null, value);
            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static void spoofBuildGms(Context context) {
        String packageName = "com.goolag.pif";

        if (!PixeldustUtils.isPackageInstalled(context, packageName)) {
            Log.e(TAG, "'" + packageName + "' is not installed.");
            return;
        }

        PackageManager pm = context.getPackageManager();

        try {
            Resources resources = pm.getResourcesForApplication(packageName);

            int resourceId = resources.getIdentifier("device_arrays", "array", packageName);
            if (resourceId != 0) {
                String[] deviceArrays = resources.getStringArray(resourceId);

                if (deviceArrays.length > 0) {
                    int randomIndex = new Random().nextInt(deviceArrays.length);
                    int selectedArrayResId = resources.getIdentifier(deviceArrays[randomIndex], "array", packageName);
                    String[] selectedDeviceProps = resources.getStringArray(selectedArrayResId);

                    dlog("PRODUCT: " + selectedDeviceProps[0]);
                    setPropValue("PRODUCT", selectedDeviceProps[0]);

                    dlog("DEVICE: " + (selectedDeviceProps[1].isEmpty() ? getDeviceName(selectedDeviceProps[5]) : selectedDeviceProps[1]));
                    setPropValue("DEVICE", selectedDeviceProps[1].isEmpty() ? getDeviceName(selectedDeviceProps[5]) : selectedDeviceProps[1]);

                    dlog("MANUFACTURER: " + selectedDeviceProps[2]);
                    setPropValue("MANUFACTURER", selectedDeviceProps[2]);

                    dlog("BRAND: " + selectedDeviceProps[3]);
                    setPropValue("BRAND", selectedDeviceProps[3]);

                    dlog("MODEL: " + selectedDeviceProps[4]);
                    setPropValue("MODEL", selectedDeviceProps[4]);

                    dlog("FINGERPRINT: " + selectedDeviceProps[5]);
                    setPropValue("FINGERPRINT", selectedDeviceProps[5]);

                    dlog("SECURITY_PATCH: " + selectedDeviceProps[6]);
                    setVersionFieldString("SECURITY_PATCH", selectedDeviceProps[6]);

                    if (!selectedDeviceProps[7].isEmpty() && selectedDeviceProps[7].matches("2[3-6]")) {
                        dlog("DEVICE_INITIAL_SDK_INT: " + selectedDeviceProps[7]);
                        setVersionFieldInt("DEVICE_INITIAL_SDK_INT", Integer.parseInt(selectedDeviceProps[7]));
                    } else {
                        Log.e(TAG, "Value for DEVICE_INITIAL_SDK_INT must be between 23-26!");
                    }

                    dlog("ID: " + (selectedDeviceProps[8].isEmpty() ? getBuildID(selectedDeviceProps[5]) : selectedDeviceProps[8]));
                    setPropValue("ID", selectedDeviceProps[8].isEmpty() ? getBuildID(selectedDeviceProps[5]) : selectedDeviceProps[8]);

                    dlog("TYPE: " + (selectedDeviceProps[9].isEmpty() ? "user" : selectedDeviceProps[9]));
                    setPropValue("TYPE", selectedDeviceProps[9].isEmpty() ? "user" : selectedDeviceProps[9]);

                    dlog("TAGS: " + (selectedDeviceProps[10].isEmpty() ? "release-keys" : selectedDeviceProps[10]));
                    setPropValue("TAGS", selectedDeviceProps[10].isEmpty() ? "release-keys" : selectedDeviceProps[10]);
                } else {
                    Log.e(TAG, "No device arrays found.");
                }
            } else {
                Log.e(TAG, "Resource 'device_arrays' not found.");
            }

        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error getting resources for '" + packageName + "': " + e.getMessage());
        }
    }

    private static void setVersionFieldInt(String key, int value) {
        try {
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static void setVersionFieldString(String key, String value) {
        try {
            // Unlock
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);

            // Edit
            field.set(null, value);

            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static boolean isCallerSafetyNet() {
        return sIsGms && Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    public static void onEngineGetCertificateChain() {
        // Check stack for SafetyNet or Play Integrity
        if ((isCallerSafetyNet() || sIsFinsky) && !sIsSetupWizard && shouldTryToCertifyDevice()) {
            dlog("Blocked key attestation sIsGms=" + sIsGms + " sIsFinsky=" + sIsFinsky);
            throw new UnsupportedOperationException();
        }
    }

    public static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, "[" + sProcessName + "] " + msg);
    }
}
