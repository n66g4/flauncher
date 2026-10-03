package me.efesser.flauncher;

import android.app.Application;
import android.content.Context;
import android.os.Build;

public class FLauncherApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            System.loadLibrary("kitkat_shim");
        }
        super.attachBaseContext(base);
    }
}
