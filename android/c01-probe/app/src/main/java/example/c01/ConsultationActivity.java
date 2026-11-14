package example.c01;

import android.app.Activity;
import android.car.Car;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.os.*;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Local card view, without JavaScript/native bridge or network navigation. */
public final class ConsultationActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private TextView status;
    private Button save, voice;
    private String pendingSpeech;
    private android.app.AlertDialog speechDialog;
    private Car car;
    private CarUxRestrictionsManager ux;
    private boolean visible, allowed, loaded, saving;
    private int epoch;
    private String document, loadFailure;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        VehicleStyle.configure(this);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(VehicleStyle.BG);
        int pad=VehicleStyle.dp(this,16);layout.setPadding(pad,pad,pad,0);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setOrientation(LinearLayout.HORIZONTAL);layout.addView(toolbar);
        Button back=new Button(this);back.setText("‹ 기록 화면");VehicleStyle.button(back,false);toolbar.addView(back,new LinearLayout.LayoutParams(VehicleStyle.dp(this,220),-2));back.setOnClickListener(v->finish());
        TextView title=VehicleStyle.text(this,"  상담 준비  ",24,VehicleStyle.TEXT);toolbar.addView(title);
        status = VehicleStyle.text(this,"",16,VehicleStyle.MUTED); layout.addView(status);
        save = new Button(this); save.setText("상담 카드 앱 내부 저장"); VehicleStyle.button(save,true);
        toolbar.addView(save,new LinearLayout.LayoutParams(0,-2,1));
        voice=new Button(this);voice.setText(speechAvailable()?"말로 증상 입력":"음성 입력 미지원 · 항목으로 선택하세요");VehicleStyle.button(voice,false);layout.addView(voice);if(!speechAvailable())voice.setVisibility(View.GONE);
        voice.setOnClickListener(v->{if(!visible||!allowed||!loaded)return;
            if(!speechAvailable()){status.setText("이 기기에 음성 인식 앱이 없습니다. 아래 항목으로 선택해 주세요.");return;}
            speechDialog=new android.app.AlertDialog.Builder(this).setTitle("말로 증상 입력")
                .setMessage("안전하게 정차한 뒤 증상을 말해 주세요. 기기의 음성 인식 앱이 처리하며, 해당 서비스 설정에 따라 음성이 서버로 전송될 수 있습니다. 인식 결과는 확인 후 메모에 추가합니다.")
                .setNegativeButton("취소",null).setPositiveButton("말하기",(d,w)->{if(visible&&allowed){try{startActivityForResult(speechIntent(),41);}catch(RuntimeException e){status.setText("음성 입력을 시작할 수 없습니다. 아래 항목을 선택해 주세요.");}}}).create();speechDialog.show();
        });
        web = new WebView(this);web.setBackgroundColor(VehicleStyle.BG); layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(layout);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setBlockNetworkLoads(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest r) { return true; }
            @Override public void onPageFinished(WebView w,String url) {
                if(document!=null) w.evaluateJavascript("(()=>{try{imported=C02.importProbe(JSON.parse("+JSONObject.quote(document)+"));importError=false;el('start').value='0';el('end').value=String(imported.durationMs/1000);el('start').max=el('end').max=String(imported.durationMs/1000);render();}catch(e){importError=true;current=null;el('report').textContent='';el('error').textContent=e.message;}})()",null);
            }
        });
        save.setOnClickListener(v->{
            if(!visible||!allowed||saving)return;
            saving=true;save.setEnabled(false);save.setText("저장 중…");
            web.evaluateJavascript("render()?C02.consultationDocument(current):null",result->{
                if(!visible||!allowed){saving=false;save.setText("상담 카드 앱 내부 저장");return;}
                try {
                    Object value=new JSONTokener(result).nextValue();
                    if(!(value instanceof String))throw new IOException("card not ready");
                    AtomicFile target=new AtomicFile(new File(getFilesDir(),"consultation-card.html"));
                    FileOutputStream out=null;
                    try {out=target.startWrite();out.write(((String)value).getBytes(StandardCharsets.UTF_8));target.finishWrite(out);}
                    catch(Exception e){if(out!=null)target.failWrite(out);throw e;}
                    status.setText("상담 카드 앱 내부 저장 완료 · 외부 공유는 아직 지원하지 않습니다.");
                }catch(Exception e){status.setText("저장 실패: "+e.getClass().getSimpleName());}
                finally{saving=false;save.setText("상담 카드 앱 내부 저장");save.setEnabled(visible&&allowed&&loaded);}
            });
        });
        try {
            boolean demo=getIntent().getBooleanExtra("demo",false);
            if(!demo) {
                File source=new File(getFilesDir(),"vehicle-recording.json");
                if(source.length()>8*1024*1024)throw new IOException("too large");
                try(InputStream in=new AtomicFile(source).openRead()){document=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
            }
            String html;
            try(InputStream in=getAssets().open("c02.html")){html=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
            // Native data is supplied only after page load. Hide synthetic controls and unsupported browser exports.
            String css="<style>#export,#html,#print,header,.import-tools,#use-fixture,.browser-notice{display:none}</style>";
            if(!demo)css+="<style>.fixture-tools,#example{display:none}#report{visibility:hidden}</style><script>window.addEventListener('load',()=>{document.getElementById('report').style.visibility='visible';document.getElementById('report').textContent='관측 기록을 불러오는 중';document.getElementById('session-info').textContent='앱 내부 관측 기록 · 출처 미검증';});</script>";
            css+="<script>document.documentElement.classList.add('"+(VehicleStyle.night?"night":"day")+"');</script>";
            css+="<script>document.querySelector('.input-hint').textContent="+JSONObject.quote(speechAvailable()?"말로 증상을 입력하거나 가까운 항목을 골라 주세요.":"가까운 항목을 골라 주세요. 이 기기는 음성 입력을 지원하지 않습니다.")+";</script>";
            loaded=true;
            web.loadDataWithBaseURL("https://local.invalid/",LocalHtml.appendBeforeEnd(html,css),"text/html","UTF-8",null);
        } catch(Exception e){loadFailure="저장된 기록을 열지 못했습니다. 먼저 기록을 시작하고 종료하세요.";status.setText(loadFailure);web.setVisibility(View.GONE);save.setVisibility(View.GONE);}
        updateGate(false);
    }
    private void updateGate(boolean ok) {
        allowed=visible&&ok;web.setVisibility(allowed&&loaded?View.VISIBLE:View.GONE);save.setEnabled(allowed&&loaded&&!saving);voice.setEnabled(allowed&&loaded);
        if(!allowed&&speechDialog!=null){speechDialog.dismiss();speechDialog=null;}
        if(!allowed)status.setText("차량 UX 상태 확인 전 또는 제한 활성 · 입력 잠금 (주차 판정 아님)");
        else status.setText(loadFailure!=null?loadFailure:"안전하게 정차한 뒤 작성하세요 · 고장 원인을 판정하는 기능이 아닙니다.");
        if(allowed)handler.post(this::offerSpeech);
    }
    private android.content.Intent speechIntent(){return new android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE,"ko-KR")
        .putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS,1)
        .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT,"어떤 증상이었는지 말해 주세요");}
    private boolean speechAvailable(){return speechIntent().resolveActivity(getPackageManager())!=null;}
    @Override public void onActivityResult(int request,int result,android.content.Intent data){super.onActivityResult(request,result,data);
        if(request!=41)return;
        if(result==RESULT_OK&&data!=null){java.util.ArrayList<String> matches=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
            if(matches!=null&&!matches.isEmpty()&&matches.get(0)!=null){pendingSpeech=matches.get(0).trim();if(pendingSpeech.length()>1500)pendingSpeech=pendingSpeech.substring(0,1500);}}
        handler.post(this::offerSpeech);
    }
    private void offerSpeech(){if(!visible||!allowed||!loaded||pendingSpeech==null||pendingSpeech.isEmpty()||speechDialog!=null&&speechDialog.isShowing())return;
        final String transcript=pendingSpeech;pendingSpeech=null;
        speechDialog=new android.app.AlertDialog.Builder(this).setTitle("이 내용이 맞나요?").setMessage(transcript)
          .setNegativeButton("버리기",null).setPositiveButton("메모에 추가",(d,w)->{if(!visible||!allowed)return;
            web.evaluateJavascript("(()=>{const f=document.getElementById('symptom');const t="+JSONObject.quote(transcript)+";const next=f.value?f.value+'\\n'+t:t;if(next.length>1500){document.getElementById('error').textContent='메모는 1500자까지 입력할 수 있습니다';return;}f.value=next;f.scrollIntoView({block:'center'});})()",null);
          }).create();speechDialog.show();
    }
    @Override public void onStart(){super.onStart();visible=true;final int current=++epoch;web.onResume();
        try{car=Car.createCar(this,handler,Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,(c,ready)->{
            if(!visible||current!=epoch)return;updateGate(false);
            if(ux!=null){try{ux.unregisterListener();}catch(RuntimeException ignored){}ux=null;}
            if(!ready)return;car=c;
            try{ux=(CarUxRestrictionsManager)c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
                ux.registerListener(r->{if(visible&&current==epoch)updateGate(r!=null&&!r.isRequiresDistractionOptimization()&&r.getActiveRestrictions()==0);});
                var r=ux.getCurrentCarUxRestrictions();updateGate(r!=null&&!r.isRequiresDistractionOptimization()&&r.getActiveRestrictions()==0);
            }catch(RuntimeException e){updateGate(false);}
        });}catch(RuntimeException e){updateGate(false);}
    }
    @Override public void onStop(){visible=false;epoch++;updateGate(false);web.onPause();if(ux!=null){try{ux.unregisterListener();}catch(RuntimeException ignored){}ux=null;}if(car!=null){try{car.disconnect();}catch(RuntimeException ignored){}car=null;}super.onStop();}
    @Override public void onDestroy(){web.destroy();super.onDestroy();}
}
