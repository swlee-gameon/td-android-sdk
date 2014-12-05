package com.treasuredata.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import io.keen.client.java.KeenCallback;
import io.keen.client.java.KeenClient;
import org.komamitsu.android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class TreasureData {
    private static final String TAG = TreasureData.class.getSimpleName();
    private static final String VERSION = "0.1.5";
    private static final String LABEL_ADD_EVENT = "addEvent";
    private static final String LABEL_UPLOAD_EVENTS = "uploadEvents";
    private static final Pattern DATABASE_NAME_PATTERN = Pattern.compile("^[0-9a-z_]{3,255}$");
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[0-9a-z_]{3,255}$");
    private static final String SHARED_PREF_NAME = "td_sdk_info";
    private static final String SHARED_PREF_KEY_UUID = "uuid";
    private static final String AUTO_KEY_UUID = "td_uuid";
    private static final String AUTO_KEY_BOARD = "td_board";
    private static final String AUTO_KEY_BRAND = "td_brand";
    private static final String AUTO_KEY_DEVICE = "td_device";
    private static final String AUTO_KEY_DISPLAY = "td_display";
    private static final String AUTO_KEY_MODEL = "td_model";
    private static final String AUTO_KEY_OS_VER = "td_os_ver";
    private static final String AUTO_KEY_OS_TYPE = "td_os_type";
    private static final String OS_TYPE = "Android";

    static {
        TDHttpHandler.VERSION = TreasureData.VERSION;
    }

    private static TreasureData sharedInstance;

    private TDClient client;
    private volatile TDCallback addEventCallBack;
    private volatile TDCallback uploadEventsCallBack;
    private volatile KeenCallback addEventKeenCallBack = createKeenCallback(LABEL_ADD_EVENT, null);
    private volatile KeenCallback uploadEventsKeenCallBack = createKeenCallback(LABEL_UPLOAD_EVENTS, null);
    private volatile boolean autoAppendUniqId;
    private volatile boolean autoAppendModelInformation;
    private String uuid;

    public static TreasureData initializeSharedInstance(Context context, String apiKey) {
        sharedInstance = new TreasureData(context, apiKey);
        return sharedInstance;
    }

    public static TreasureData initializeSharedInstance(Context context) {
        return initializeSharedInstance(context, null);
    }

    public static TreasureData sharedInstance() {
        if (sharedInstance == null) {
            Log.w(TAG, "sharedInstance is initialized properly");
            return new NullTreasureData();
        }
        return sharedInstance;
    }

    public String getUUID(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        synchronized (this) {
            String uuid = sharedPreferences.getString(SHARED_PREF_KEY_UUID, null);
            if (uuid == null) {
                uuid = UUID.randomUUID().toString();
                sharedPreferences.edit().putString(SHARED_PREF_KEY_UUID, uuid).commit();
            }
            return uuid;
        }
    }

    public TreasureData(Context context, String apiKey) {
        Context applicationContext = context.getApplicationContext();
        uuid = getUUID(applicationContext);

        if (apiKey == null && TDClient.getDefaultApiKey() == null) {
            Log.e(TAG, "initializeApiKey() hasn't called yet");
            return;
        }

        try {
            client = new TDClient(applicationContext.getApplicationContext(), apiKey);
        } catch (IOException e) {
            Log.e(TAG, "Failed to construct TreasureData object", e);
        }
    }

    public TreasureData(Context context) {
        this(context, null);
    }

    public static void enableLogging() {
        TDLogging.enableLogging();
    }

    public static void disableLogging() {
        TDLogging.disableLogging();
    }

    public static void initializeApiEndpoint(String apiEndpoint) {
        TDClient.setApiEndpoint(apiEndpoint);
    }

    public static void initializeDefaultApiKey(String defaultApiKey) {
        TDClient.setDefaultApiKey(defaultApiKey);
    }

    public static void initializeEncryptionKey(String encryptionKey) {
        TDClient.setEncryptionKey(encryptionKey);
    }

    public static void enableEventCompression() {
        TDHttpHandler.enableEventCompression();
    }

    public static void disableEventCompression() {
        TDHttpHandler.disableEventCompression();
    }

    public synchronized void setAddEventCallBack(TDCallback callBack) {
        this.addEventCallBack = callBack;
        this.addEventKeenCallBack = createKeenCallback(LABEL_ADD_EVENT, callBack);
    }

    public TDCallback getAddEventCallBack() {
        return this.addEventCallBack;
    }

    public synchronized void setUploadEventsCallBack(TDCallback callBack) {
        this.uploadEventsCallBack = callBack;
        this.uploadEventsKeenCallBack = createKeenCallback(LABEL_UPLOAD_EVENTS, callBack);
    }

    public TDCallback getUploadEventsCallBack() {
        return this.uploadEventsCallBack;
    }

    public void addEvent(String database, String table, String key, Object value) {
        addEventWithCallback(database, table, key, value, null);
    }

    public void addEvent(String database, String table, Map<String, Object> record) {
        addEventWithCallback(database, table, record, null);
    }

    public void addEventWithCallback(String database, String table, String key, Object value, TDCallback callback) {
        HashMap<String, Object> record = new HashMap<String, Object>(1);
        record.put(key, value);
        addEventWithCallback(database, table, record, callback);
    }

    public void appendUniqId(Map<String, Object> record) {
        record.put(AUTO_KEY_UUID, uuid);
    }

    public void appendModelInformation(Map<String, Object> record) {
        record.put(AUTO_KEY_BOARD, Build.BOARD);
        record.put(AUTO_KEY_BRAND, Build.BRAND);
        record.put(AUTO_KEY_DEVICE, Build.DEVICE);
        record.put(AUTO_KEY_DISPLAY, Build.DISPLAY);
        record.put(AUTO_KEY_DEVICE, Build.DEVICE);
        record.put(AUTO_KEY_MODEL, Build.MODEL);
        record.put(AUTO_KEY_OS_VER, Build.VERSION.SDK_INT);
        record.put(AUTO_KEY_OS_TYPE, OS_TYPE);
    }

    public void addEventWithCallback(String database, String table, Map<String, Object> record, TDCallback callback) {
        if (client == null) {
            Log.w(TAG, "TDClient is null");
            return;
        }

        if (callback == null) {
            callback = addEventCallBack;
        }

        if (autoAppendUniqId) {
            appendUniqId(record);
        }

        if (autoAppendModelInformation) {
            appendModelInformation(record);
        }

        if (!(DATABASE_NAME_PATTERN.matcher(database).find() && TABLE_NAME_PATTERN.matcher(table).find())) {
            String errMsg = String.format("database and table need to be consist of lower letters, numbers or '_': database=%s, table=%s", database, table);
            if (TDLogging.isEnabled())
                Log.e(TAG, errMsg);
            callback.onError(KeenClient.ERROR_CODE_INVALID_PARAM, new IllegalArgumentException(errMsg));
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(database).append(".").append(table);
        client.queueEvent(null, sb.toString(), record, null, createKeenCallback(LABEL_ADD_EVENT, callback));
    }

    public void uploadEvents() {
        uploadEventsWithCallback(null);
    }

    public void uploadEventsWithCallback(TDCallback callback) {
        if (client == null) {
            Log.w(TAG, "TDClient is null");
            return;
        }

        if (callback == null) {
            callback = uploadEventsCallBack;
        }
        client.sendQueuedEventsAsync(null, createKeenCallback(LABEL_UPLOAD_EVENTS, callback));
    }

    private static KeenClient.KeenCallbackWithErrorCode createKeenCallback(final String methodName, final TDCallback callback) {
        KeenClient.KeenCallbackWithErrorCode keenCallback = new KeenClient.KeenCallbackWithErrorCode() {
            private String currentErrorCode;

            @Override
            public void onSuccess() {
                if (callback != null) {
                    callback.onSuccess();
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (TDLogging.isEnabled())
                    Log.e(TAG, methodName + " failed: " + e.getMessage());

                if (callback != null) {
                    callback.onError(getErrorCode(), e);
                }
            }

           @Override
           public void setErrorCode(String errorCode) {
               this.currentErrorCode = errorCode;
           }

           @Override
           public String getErrorCode() {
               return this.currentErrorCode;
           }
        };
        return keenCallback;
    }

    public void setDebugMode(boolean debug) {
        if (client == null) {
            Log.w(TAG, "TDClient is null");
            return;
        }

        client.setDebugMode(debug);
    }

    public void setAutoAppendUniqId(boolean autoAppendUniqId) {
        this.autoAppendUniqId = autoAppendUniqId;
    }

    public void setAutoAppendModelInformation(boolean autoAppendModelInformation) {
        this.autoAppendModelInformation = autoAppendModelInformation;
    }

    // Only for testing
    @Deprecated
    TreasureData() {
    }

    @Deprecated
    void setClient(TDClient mockClient) {
        this.client = mockClient;
    }

    static class NullTreasureData extends TreasureData {
        @Override
        public synchronized void setAddEventCallBack(TDCallback callBack) {
        }

        @Override
        public TDCallback getAddEventCallBack() {
            return null;
        }

        @Override
        public synchronized void setUploadEventsCallBack(TDCallback callBack) {
        }

        @Override
        public TDCallback getUploadEventsCallBack() {
            return null;
        }

        @Override
        public void addEvent(String database, String table, String key, Object value) {
        }

        @Override
        public void addEvent(String database, String table, Map<String, Object> record) {
        }

        @Override
        public void addEventWithCallback(String database, String table, String key, Object value, TDCallback callback) {
        }

        @Override
        public void addEventWithCallback(String database, String table, Map<String, Object> record, TDCallback callback) {
        }

        @Override
        public void uploadEvents() {
        }

        @Override
        public void uploadEventsWithCallback(TDCallback callback) {
        }

        @Override
        public void setDebugMode(boolean debug) {
        }
    }
}
