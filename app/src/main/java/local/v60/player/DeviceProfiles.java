package local.v60.player;

import android.content.Context;
import android.media.AudioDeviceInfo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Device identity is hashed locally; absent devices retain the base route profile. */
final class DeviceProfiles {
    static boolean enabled(Context c){return c.getSharedPreferences("yin_control",0).getBoolean("device_profiles",false);}
    static String key(Context c,String profile){
        if(!enabled(c)||"speaker".equals(profile))return profile;
        AudioDeviceInfo d=AudioSettings.predictedDevice(c);if(d==null||!AudioSettings.route(d).equals(profile))return profile;
        try{String identity=d.getType()+":"+d.getAddress()+":"+d.getProductName();byte[] digest=MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));StringBuilder key=new StringBuilder(profile+"@");for(int i=0;i<12;i++)key.append(String.format(java.util.Locale.ROOT,"%02x",digest[i]&255));return key.toString();}catch(Exception unavailable){return profile;}
    }
}
