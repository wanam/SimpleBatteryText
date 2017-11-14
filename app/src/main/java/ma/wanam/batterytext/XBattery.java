package ma.wanam.batterytext;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XBattery implements IXposedHookLoadPackage {

    private final String SYSTEMUI = "com.android.systemui";
    private Context keyguardStatusBarViewClassContext;
    private TextView lockScreenNativeTextView;
    private TextView lockScreenTextView;
    private ViewGroup lockScreenViewGroup;

    private TextView statusBarTextView;
    private ViewGroup statusBarViewGroup;

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = (int) ((100.0d * ((double) intent.getIntExtra("level", 0))) / ((double) intent.getIntExtra("scale", 100)));

            if (lockScreenTextView != null) {
                lockScreenTextView.setText(String.format("%s%%", new Object[]{Integer.valueOf(level)}));
            }
            if (statusBarTextView != null) {
                statusBarTextView.setText(String.format("%s%%", new Object[]{Integer.valueOf(level)}));
                return;
            }
            return;

        }
    };

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {

        if (lpparam.packageName.equals(SYSTEMUI)) {
            // Lockscreen
            try {
                Class<?> keyguardStatusBarViewClass = XposedHelpers.findClass(SYSTEMUI + ".statusbar.phone.KeyguardStatusBarView", lpparam.classLoader);
                XposedBridge.hookAllConstructors(keyguardStatusBarViewClass, new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
                        keyguardStatusBarViewClassContext = (Context) param.args[0];
                        keyguardStatusBarViewClassContext.registerReceiver(broadcastReceiver, intentFilter);
                    }
                });

                XposedHelpers.findAndHookMethod(keyguardStatusBarViewClass, "onFinishInflate", new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        lockScreenNativeTextView = (TextView) XposedHelpers.getObjectField(param.thisObject, "mBatteryLevel");
                        lockScreenViewGroup = (ViewGroup) ((ViewGroup) param.thisObject).findViewById(keyguardStatusBarViewClassContext.getResources().getIdentifier("system_icons", "id", "com.android.systemui"));
                        lockScreenTextView = new TextView(lockScreenViewGroup.getContext());
                        modifyTextView(lockScreenTextView, lockScreenViewGroup);
                        XposedHelpers.callMethod(param.thisObject, "updateVisibilities");
                    }
                });

                XposedHelpers.findAndHookMethod(keyguardStatusBarViewClass, "updateVisibilities", new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (lockScreenNativeTextView != null) {
                            lockScreenNativeTextView.setVisibility(View.GONE);
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(t);
            }

            // Status Bar
            try {
                XposedHelpers.findAndHookMethod(XposedHelpers.findClass(SYSTEMUI + ".statusbar.phone.PhoneStatusBar", lpparam.classLoader), "makeStatusBarView", new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        statusBarViewGroup = (ViewGroup) ((ViewGroup) XposedHelpers.getObjectField(param.thisObject, "mStatusBarView")).findViewById(((Context) XposedHelpers.getObjectField(param.thisObject, "mContext")).getResources().getIdentifier("system_icons", "id", "com.android.systemui"));
                        statusBarTextView = new TextView(statusBarViewGroup.getContext());
                        modifyTextView(statusBarTextView, statusBarViewGroup);
                    }
                });

                XposedHelpers.findAndHookMethod(XposedHelpers.findClass(SYSTEMUI + ".statusbar.phone.PhoneStatusBarTransitions", lpparam.classLoader), "applyMode", Integer.TYPE, Boolean.TYPE, new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (statusBarTextView != null) {
                            statusBarTextView.setAlpha((Float) XposedHelpers.callMethod(param.thisObject, "getBatteryClockAlpha", param.args[0]));
                        }
                    }
                });

                XposedHelpers.findAndHookMethod(XposedHelpers.findClass(SYSTEMUI + ".statusbar.phone.StatusBarIconController", lpparam.classLoader), "applyIconTint", new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (statusBarTextView != null) {
                            statusBarTextView.setTextColor((Integer) XposedHelpers.getObjectField(param.thisObject, "mIconTint"));
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    }

    private void modifyTextView(TextView textView, ViewGroup viewGroup) {
        if (textView != null && viewGroup != null) {
            if (textView.getParent() != null) {
                ((ViewGroup) textView.getParent()).removeView(textView);
            }
            textView.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
            textView.setTextColor(-1);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.0f);

            textView.setTypeface(Typeface.create("sans-serif-light", Typeface.BOLD));

            textView.setPadding(20, 0, 0, 0);
            viewGroup.addView(textView, viewGroup.getChildCount());

            textView.setVisibility(View.VISIBLE);
        }
    }
}
