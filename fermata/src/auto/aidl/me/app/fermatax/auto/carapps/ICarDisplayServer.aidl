package me.app.fermatax.auto.carapps;

import android.content.ComponentName;
import android.view.MotionEvent;
import android.view.Surface;

interface ICarDisplayServer {
    const int CAP_VD = 1;
    const int CAP_TRUSTED = 2;
    const int CAP_INJECT = 4;
    const int CAP_IME = 16;
    const int CAP_OWN_POWER_GROUP = 32;
    const int CAP_TASKS = 64;
    const int CAP_FOCUS = 128;

    int capabilities();
    int attach(in Surface s, int w, int h, int dpi, IBinder client);
    void setSurface(in @nullable Surface s, int w, int h, int dpi);
    void inject(in MotionEvent e);
    oneway void injectKey(int keyCode);
    int launch(in ComponentName c, int intentFlags);
    oneway void launchOnPhone(in ComponentName c, int intentFlags);
    String closeTopApp(boolean force);
    void release();
    int serverVersion();
}
