package com.bfp.alert;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

public class NetworkUtils {

    public static boolean isOnline(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager)
                ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.Network net = cm.getActiveNetwork();
        if (net == null) return false;
        NetworkCapabilities caps =
                cm.getNetworkCapabilities(net);
        return caps != null && (
                caps.hasTransport(
                        NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(
                                NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        caps.hasTransport(
                                NetworkCapabilities.TRANSPORT_ETHERNET));
    }
}