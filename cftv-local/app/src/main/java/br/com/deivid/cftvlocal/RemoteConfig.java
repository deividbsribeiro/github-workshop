package br.com.deivid.cftvlocal;

import android.content.Context;

final class RemoteConfig {
    static final String KEY_SN = "remote_sn";
    static final String KEY_UUID = "funsdk_uuid";
    static final String KEY_APP_KEY = "funsdk_app_key";
    static final String KEY_APP_SECRET = "funsdk_app_secret";
    static final String KEY_MOVED_CARD = "funsdk_moved_card";

    private RemoteConfig() {}

    static String sn(Context context) {
        return value(context, KEY_SN);
    }

    static String uuid(Context context) {
        return value(context, KEY_UUID);
    }

    static String appKey(Context context) {
        return value(context, KEY_APP_KEY);
    }

    static String appSecret(Context context) {
        return value(context, KEY_APP_SECRET);
    }

    static int movedCard(Context context) {
        try {
            return Integer.parseInt(value(context, KEY_MOVED_CARD));
        } catch (Exception ignored) {
            return -1;
        }
    }

    static boolean isComplete(Context context) {
        return !sn(context).isEmpty()
                && !uuid(context).isEmpty()
                && !appKey(context).isEmpty()
                && !appSecret(context).isEmpty()
                && movedCard(context) >= 0;
    }

    static void put(Context context, String key, String value) {
        SecurePrefs.put(context, key, value == null ? "" : value.trim());
    }

    private static String value(Context context, String key) {
        String value = SecurePrefs.get(context, key);
        return value == null ? "" : value.trim();
    }
}
