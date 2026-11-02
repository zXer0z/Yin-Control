package local.v60.player;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import android.util.Base64;

/** A user-controlled supervisor; no HAL, kernel, SELinux, firmware or mixer edits. */
final class KeepAlive {
    static void install(Context context) throws Exception {
        String script="#!/system/bin/sh\n"+
            "lock=/data/adb/yin-control/supervisor.lock\n"+
            "if ! mkdir \"$lock\" 2>/dev/null; then old=$(cat \"$lock/pid\" 2>/dev/null); if [ -n \"$old\" ] && kill -0 \"$old\" 2>/dev/null; then exit 0; fi; rm -f \"$lock/pid\"; rmdir \"$lock\" 2>/dev/null; mkdir \"$lock\" 2>/dev/null || exit 0; fi\n"+
            "echo $$ > \"$lock/pid\"\n"+
            "trap 'rm -f \"$lock/pid\"; rmdir \"$lock\" 2>/dev/null' EXIT\n"+
            "while [ \"$(getprop sys.boot_completed)\" != 1 ]; do sleep 5; done\n"+
            "while [ -f /data/adb/yin-control/enabled ]; do\n"+
            " if ! pidof local.v60.player >/dev/null 2>&1 || ! dumpsys activity services local.v60.player/.YinService 2>/dev/null | grep -q 'ServiceRecord.*local.v60.player/.YinService'; then\n"+
            "  am start-foreground-service -n local.v60.player/.YinService -a local.v60.player.KEEP_ALIVE >/dev/null 2>&1\n"+
            " fi\n sleep 60\ndone\n";
        String encoded=Base64.encodeToString(script.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
        RootAccess.run("mkdir -p /data/adb/yin-control /data/adb/service.d; touch /data/adb/yin-control/enabled; printf %s "+RootAccess.quote(encoded)+" | base64 -d > /data/adb/service.d/yin-control.sh; chmod 700 /data/adb/service.d/yin-control.sh; "+
            "sh /data/adb/service.d/yin-control.sh </dev/null >/dev/null 2>&1 &",10);
    }
    static void disable() throws Exception {
        RootAccess.run("rm -f /data/adb/yin-control/enabled /data/adb/service.d/yin-control.sh",8);
    }
}
