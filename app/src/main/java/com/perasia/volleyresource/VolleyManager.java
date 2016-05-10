package com.perasia.volleyresource;

import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

public class VolleyManager {
    private static RequestQueue mRequestQueue;

    private VolleyManager() {
    }

    public static void init(Context context) {

        mRequestQueue = Volley.newRequestQueue(context);
    }

    public static RequestQueue getRequestQueue() {
        if (mRequestQueue != null) {
            return mRequestQueue;
        } else {
            throw new IllegalStateException("Not initialized");
        }
    }
}
