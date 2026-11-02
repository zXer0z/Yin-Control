package local.v60.player;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.widget.*;
import android.view.Gravity;
import java.util.concurrent.Executors;

/** Persistent control shell: sound preferences and measured signal route. */
public final class MainActivity extends Activity {
    private static final java.util.concurrent.ExecutorService rootWorker=Executors.newSingleThreadExecutor();
    private TextView status;
    private Switch enabled;
    private YinUi ui;
    private YinSoundPage sound;
    private YinRoutePage route;
    private YinConfigPage config;
    private FrameLayout content;
    private final TextView[] tabs=new TextView[3];
    private int selected;
    private String importProfile="",importKey="";
    private boolean importImpulse;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){refreshStatus();if(selected==1&&route!=null)route.refresh(false);if(selected==0&&sound!=null)sound.refreshRoutes();main.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);YinUi.load(this);ui=new YinUi(this);getWindow().setDecorFitsSystemWindows(false);
        if(saved!=null){importProfile=saved.getString("importProfile","");importKey=saved.getString("importKey","");importImpulse=saved.getBoolean("importImpulse");}
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);getWindow().setNavigationBarColor(YinUi.BG);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},44);
        LinearLayout root=ui.column();root.setBackgroundColor(YinUi.BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());root.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        LinearLayout heading=ui.column();heading.setPadding(dp(22),dp(12),dp(22),dp(6));heading.addView(ui.title("Yin Control",25));root.addView(heading);
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        sound=new YinSoundPage(this);route=new YinRoutePage(this);content.addView(sound.view);content.addView(route.view);route.view.setVisibility(android.view.View.GONE);config=new YinConfigPage(this);content.addView(config.view);config.view.setVisibility(android.view.View.GONE);
        LinearLayout nav=ui.row();nav.setPadding(dp(20),dp(8),dp(20),dp(10));String[] names={"Sonido","Ruta de audio","Config"};
        for(int i=0;i<3;i++){final int index=i;TextView t=ui.title(names[i],14);t.setGravity(Gravity.CENTER);t.setPadding(dp(8),dp(16),dp(8),dp(16));tabs[i]=t;LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(4),0,dp(4),0);nav.addView(t,p);t.setOnClickListener(v->select(index));}root.addView(nav);setContentView(root);select(saved==null?0:saved.getInt("page",0));
        if(getSharedPreferences("yin_control",0).getBoolean("enabled",false)){startForegroundService(new Intent(this,YinService.class).setAction("local.v60.player.KEEP_ALIVE"));if(getSharedPreferences("yin_control",0).getBoolean("persistent",true))rootWorker.execute(()->{try{KeepAlive.install(this);}catch(Exception error){main.post(()->{if(!isDestroyed())Toast.makeText(this,"Supervisor pendiente: "+error.getMessage(),Toast.LENGTH_LONG).show();});}});}
    }
    LinearLayout controllerBar(){
        LinearLayout bar=new LinearLayout(this);bar.setOrientation(1);bar.setPadding(dp(16),dp(10),dp(16),dp(10));bar.setBackground(ui.shape(YinUi.SELECTED,22));
        enabled=new Switch(this);ui.tint(enabled);enabled.setText("Motor de audio");enabled.setTextColor(YinUi.INK);enabled.setTextSize(15);enabled.setChecked(getSharedPreferences("yin_control",0).getBoolean("enabled",false));
        bar.addView(enabled);status=new TextView(this);status.setTextColor(YinUi.ACCENT);status.setTextSize(11);status.setPadding(0,dp(5),0,0);status.setMaxLines(2);bar.addView(status);
        enabled.setOnCheckedChangeListener((button,value)->activate(value));
        refreshStatus();return bar;
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void select(int page){selected=Math.max(0,Math.min(2,page));config.view.setVisibility(selected==2?android.view.View.VISIBLE:android.view.View.GONE);sound.view.setVisibility(selected==0?android.view.View.VISIBLE:android.view.View.GONE);route.view.setVisibility(selected==1?android.view.View.VISIBLE:android.view.View.GONE);for(int i=0;i<3;i++){tabs[i].setBackground(ui.shape(i==selected?YinUi.SELECTED:YinUi.BG,18));tabs[i].setTextColor(i==selected?YinUi.ACCENT:YinUi.MUTED);}if(selected==1)route.refresh(true);}
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("page",selected);out.putString("importProfile",importProfile);out.putString("importKey",importKey);out.putBoolean("importImpulse",importImpulse);super.onSaveInstanceState(out);}
    void importEffect(String profile,String key,boolean impulse){importProfile=profile;importKey=key;importImpulse=impulse;Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(picker,708);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=708||result!=RESULT_OK||data==null||data.getData()==null||importProfile.isEmpty())return;Toast.makeText(this,"Preparando archivo…",Toast.LENGTH_SHORT).show();EffectImports.read(this,data.getData(),importProfile,importKey,importImpulse,message->{if(isDestroyed())return;Toast.makeText(this,message,Toast.LENGTH_LONG).show();sound.refreshSettings();});}
    private void activate(boolean value){
        enabled.setEnabled(false);status.setText(value?"Verificando root y preparando el servicio…":"Desactivando Yin…");
        rootWorker.execute(()->{
            String message=null;
            try{
                if(value){RootAccess.run("true",20);if(getSharedPreferences("yin_control",0).getBoolean("persistent",true))KeepAlive.install(this);}
                else KeepAlive.disable();
                getSharedPreferences("yin_control",0).edit().putBoolean("enabled",value).commit();
            }catch(Exception error){message=error.getMessage();}
            final String result=message;
            main.post(()->{if(isDestroyed())return;enabled.setEnabled(true);if(result!=null){enabled.setOnCheckedChangeListener(null);enabled.setChecked(!value);enabled.setOnCheckedChangeListener((b,v)->activate(v));Toast.makeText(this,result,Toast.LENGTH_LONG).show();}
                else if(value)startForegroundService(new Intent(this,YinService.class).setAction("local.v60.player.KEEP_ALIVE"));
                else{YinService.disable();stopService(new Intent(this,YinService.class));}refreshStatus();});
        });
    }
    void profilesReset(){sound.refreshSettings();sound.refreshRoutes();}
    void reloadAppearance(){recreate();}
    void persistent(boolean value,java.util.function.Consumer<String> done){rootWorker.execute(()->{String error=null;try{if(value&&getSharedPreferences("yin_control",0).getBoolean("enabled",false))KeepAlive.install(this);else KeepAlive.disable();getSharedPreferences("yin_control",0).edit().putBoolean("persistent",value).commit();}catch(Exception e){error=e.getMessage();}final String result=error;main.post(()->done.accept(result));});}
    void refreshStatus(){if(status==null)return;Bundle state=YinService.publicStatus();status.setText(state.getBoolean("ok")&&state.getBoolean("playing")?"Conectado a Yang · "+AudioSignalInfo.frequency(state.getInt("rate"))+" · "+AudioSettings.encodingName(state.getInt("encoding")):state.getBoolean("ready")?"Listo · esperando música de Yang":state.getString("message","Yin en espera de Yang"));}
    @Override protected void onResume(){super.onResume();main.post(refresh);}
    @Override protected void onPause(){main.removeCallbacks(refresh);super.onPause();}
}
