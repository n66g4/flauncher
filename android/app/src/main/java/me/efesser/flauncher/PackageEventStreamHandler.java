package me.efesser.flauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.EventChannel;

/**
 * Package install/uninstall events for Android versions before LauncherApps (API 21).
 */
public class PackageEventStreamHandler implements EventChannel.StreamHandler
{
    private final MainActivity _activity;
    private BroadcastReceiver _receiver;

    public PackageEventStreamHandler(MainActivity activity)
    {
        _activity = activity;
    }

    @Override
    public void onListen(Object arguments, EventChannel.EventSink events)
    {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");

        _receiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                Uri data = intent.getData();
                if (data == null) {
                    return;
                }

                String packageName = data.getSchemeSpecificPart();
                String action = intent.getAction();
                if (action == null) {
                    return;
                }

                switch (action)
                {
                    case Intent.ACTION_PACKAGE_REMOVED -> events.success(Map.of(
                            "action", "PACKAGE_REMOVED",
                            "packageName", packageName));
                    case Intent.ACTION_PACKAGE_ADDED -> {
                        Map<String, Serializable> application = _activity.getApplication(packageName);
                        if (!application.isEmpty()) {
                            events.success(Map.of(
                                    "action", "PACKAGE_ADDED",
                                    "activityInfo", application));
                        }
                    }
                    case Intent.ACTION_PACKAGE_CHANGED -> {
                        Map<String, Serializable> application = _activity.getApplication(packageName);
                        if (!application.isEmpty()) {
                            events.success(Map.of(
                                    "action", "PACKAGE_CHANGED",
                                    "activityInfo", application));
                        }
                    }
                    default -> { }
                }
            }
        };

        _activity.registerReceiver(_receiver, filter);
    }

    @Override
    public void onCancel(Object arguments)
    {
        if (_receiver != null) {
            _activity.unregisterReceiver(_receiver);
            _receiver = null;
        }
    }
}
