package br.com.deivid.cftvlocal;

import android.content.Context;

import com.basic.G;
import com.lib.FunSDK;
import com.lib.sdk.struct.SDBDeviceInfo;
import com.manager.XMFunSDKManager;
import com.manager.db.DevDataCenter;
import com.manager.db.XMDevInfo;
import com.manager.device.DeviceManager;

final class FunSdkBridge {
    interface LoginCallback {
        void onSuccess();
        void onFailure(int errorId);
    }

    private static boolean initialized;
    private static String initializedFingerprint = "";

    private FunSdkBridge() {}

    static synchronized boolean ensureInitialized(Context context) {
        Context app = context.getApplicationContext();
        if (!RemoteConfig.isComplete(app)) return false;

        String fingerprint = RemoteConfig.uuid(app) + "|"
                + RemoteConfig.appKey(app) + "|"
                + RemoteConfig.movedCard(app);
        if (initialized && fingerprint.equals(initializedFingerprint)) return true;

        XMFunSDKManager manager = XMFunSDKManager.getInstance();
        if (!manager.isLoadLibrarySuccess()) return false;

        manager.initXMCloudPlatform(
                app,
                RemoteConfig.uuid(app),
                RemoteConfig.appKey(app),
                RemoteConfig.appSecret(app),
                RemoteConfig.movedCard(app),
                true);
        manager.initLog();

        initialized = true;
        initializedFingerprint = fingerprint;
        return true;
    }

    static void login(Context context, String username, String password, LoginCallback callback) {
        Context app = context.getApplicationContext();
        if (!ensureInitialized(app)) {
            callback.onFailure(-1);
            return;
        }

        String devId = RemoteConfig.sn(app).toLowerCase();
        addDeviceToDataCenter(devId, username, password);

        DeviceManager.getInstance().loginDev(devId, new DeviceManager.OnDevManagerListener() {
            @Override
            public void onSuccess(String id, int operationType, Object result) {
                callback.onSuccess();
            }

            @Override
            public void onFailed(String id, int msgId, String jsonName, int errorId) {
                callback.onFailure(errorId);
            }
        });
    }

    private static void addDeviceToDataCenter(String devId, String username, String password) {
        SDBDeviceInfo deviceInfo = new SDBDeviceInfo();
        G.SetValue(deviceInfo.st_0_Devmac, devId);
        G.SetValue(deviceInfo.st_1_Devname, devId);
        G.SetValue(deviceInfo.st_4_loginName, username);
        deviceInfo.st_7_nType = 0;

        XMDevInfo xmDevInfo = new XMDevInfo();
        xmDevInfo.setDevPassword(password);
        xmDevInfo.setDevUserName(username);
        xmDevInfo.sdbDevInfoToXMDevInfo(deviceInfo);

        DevDataCenter.getInstance().addDev(xmDevInfo);
        FunSDK.AddDevInfoToDataCenter(G.ObjToBytes(xmDevInfo.getSdbDevInfo()), 0, 0, "");
    }
}
