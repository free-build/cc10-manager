package com.codex.cc10manager.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public class LauncherActivity extends Activity {
    private static final String MANAGER_PACKAGE = "com.codex.cc10manager.system";
    private static final ComponentName MANAGER_COMPONENT = new ComponentName(
            MANAGER_PACKAGE,
            "com.codex.cc10manager.MainActivity");

    private boolean launchAttempted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openManager();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!launchAttempted && !isFinishing()) {
            openManager();
        }
    }

    private void openManager() {
        launchAttempted = true;
        Intent intent = getPackageManager().getLaunchIntentForPackage(MANAGER_PACKAGE);
        if (intent == null) {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(MANAGER_COMPONENT);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "CC10优化管理器系统应用未安装",
                    Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
