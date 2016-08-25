package com.hzmc.logger;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.telephony.TelephonyManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MGAndroidLogger {

    private static MGAndroidLogger instance;

    private Context context;
    private String  webToken;
    private AsyncLoggingWorker loggingWorker;

    private MGAndroidLogger(Context context, boolean useHttpPost, boolean useSsl, boolean isUsingDataHub, String dataHubAddr, int dataHubPort,
                            String token, boolean logHostName) throws IOException {
        this.context = context;
        this.webToken = token;
        loggingWorker = new AsyncLoggingWorker(context, useSsl, useHttpPost, isUsingDataHub, token, dataHubAddr, dataHubPort, logHostName);
    }

    public static synchronized MGAndroidLogger createInstance(Context context, boolean useSsl, String token) throws IOException {
        if(instance != null) {
            instance.loggingWorker.close();
        }
        instance = new MGAndroidLogger(context, false, useSsl, false, null, 0, token, false);
        return instance;
    }

    public static synchronized MGAndroidLogger getInstance() {
        if(instance != null) {
            return instance;
        } else {
            throw new IllegalArgumentException("Logger instance is not initialized. Call createInstance() first!");
        }
    }

    public void log(String message) {
        loggingWorker.addLineToQueue(message);
    }

    public void log(String uid, String host, String uri, Date requestStartTime, long timeCost, int responseCode) throws JSONException {
        log(uid, host, uri, requestStartTime, timeCost, responseCode, false);
    }

    public void log(String uid, String host, String uri, Date requestStartTime, long timeCost, int responseCode, boolean bDebug) throws JSONException {
        JSONObject msg = new JSONObject();
        msg.put("request_host", host==null?"":host);
        msg.put("request_uri", uri==null?"":uri);
        msg.put("request_time", timeCost);
        msg.put("request_start_time", logTimeString(requestStartTime));
        msg.put("response_code", responseCode);
        msg.put("network_env", getNetworkClass(context));
        msg.put("uid", uid==null?"":uid);
        msg.put("debug", bDebug);

        PackageInfo pinfo = null;
        try {
            PackageManager pm = context.getPackageManager();
            pinfo = context.getPackageManager().getPackageInfo(context.getApplicationContext().getPackageName(), 0);
            msg.put("app_version", pinfo.versionName);
            msg.put("build", pinfo.versionCode);
            msg.put("device", new JSONObject().put("model", android.os.Build.MODEL).put("systemVer", Build.VERSION.SDK_INT));

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }


        loggingWorker.addLineToQueue(msg.toString());
    }

    String logTimeString(Date date){
        String datePattern = "yyyy'-'MM'-'dd'T'HH':'mm':'ss.SSS'Z'";
        SimpleDateFormat sFormat = new SimpleDateFormat(datePattern);

        return sFormat.format(date);
    }

    String getNetworkClass(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        if(info==null || !info.isConnected())
            return "-"; //not connected
        if(info.getType() == ConnectivityManager.TYPE_WIFI)
            return "WIFI";
        if(info.getType() == ConnectivityManager.TYPE_MOBILE){
            int networkType = info.getSubtype();
            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN: //api<8 : replace by 11
                    return "2G";
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B: //api<9 : replace by 14
                case TelephonyManager.NETWORK_TYPE_EHRPD:  //api<11 : replace by 12
                case TelephonyManager.NETWORK_TYPE_HSPAP:  //api<13 : replace by 15
                    return "3G";
                case TelephonyManager.NETWORK_TYPE_LTE:    //api<11 : replace by 13
                    return "4G";
                default:
                    return "?";
            }
        }
        return "?";
    }


}
