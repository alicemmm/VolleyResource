package com.perasia.volleyresource;

import android.app.Application;


public class APP extends Application{

    @Override
    public void onCreate() {
        super.onCreate();
        VolleyManager.init(getApplicationContext());
    }
}
