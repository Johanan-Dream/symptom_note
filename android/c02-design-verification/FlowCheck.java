package example.c02.verify;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.Button;
import android.graphics.Bitmap;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Separate signed instrumentation APK. Uses real app controls; never injects vehicle samples. */
public class FlowCheck extends Instrumentation {
 private Activity activity;
 private final JSONObject result=new JSONObject();
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 private void mark(String stage){Bundle b=new Bundle();b.putString("stage",stage);sendStatus(1,b);}
 private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private View find(View view,String text){if(view instanceof Button&&text.equals(((Button)view).getText().toString()))return view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View v=find(g.getChildAt(i),text);if(v!=null)return v;}}return null;}
 private WebView web(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup){for(int i=0;i<((ViewGroup)v).getChildCount();i++){WebView w=web(((ViewGroup)v).getChildAt(i));if(w!=null)return w;}}return null;}
 private void click(String text)throws Exception{
  final boolean[] clicked={false};
  for(int i=0;i<30;i++){runOnMainSync(()->{View v=find(activity.getWindow().getDecorView(),text);if(v!=null&&v.isShown()&&v.isEnabled())clicked[0]=v.performClick();});if(clicked[0])return;Thread.sleep(500);}
  throw new AssertionError("Control unavailable: "+text);
 }
 private String shell(String command)throws Exception{try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(command))){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
 private JSONObject snapshot()throws Exception{return new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(getTargetContext().getFilesDir(),"vehicle-recording.json").toPath()),StandardCharsets.UTF_8));}
 private int fresh(JSONObject doc,String cutoff)throws Exception{int n=0;JSONArray events=doc.getJSONArray("events");for(int i=0;i<events.length();i++){JSONObject e=events.getJSONObject(i);if("speed".equals(e.optString("type"))&&"ok".equals(e.optString("status"))&&new BigInteger(e.getString("receivedNs")).compareTo(new BigInteger(cutoff))>0)n++;}return n;}
 private Activity launch(){Intent intent=new Intent().setComponent(new ComponentName("example.c02.note","example.c01.ProbeActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);return startActivitySync(intent);}
 private void screenshot(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();check(b!=null,"Screenshot unavailable");Bitmap scaled=Bitmap.createScaledBitmap(b,1280,Math.max(1,b.getHeight()*1280/b.getWidth()),true);ByteArrayOutputStream bytes=new ByteArrayOutputStream();scaled.compress(Bitmap.CompressFormat.PNG,100,bytes);Bundle bundle=new Bundle();bundle.putString(name,Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP));sendStatus(1,bundle);scaled.recycle();if(scaled!=b)b.recycle();}

 private String js(String script)throws Exception{
  CountDownLatch latch=new CountDownLatch(1);final String[] out={null};
  runOnMainSync(()->{WebView w=web(activity.getWindow().getDecorView());check(w!=null,"WebView missing");w.evaluateJavascript(script,v->{out[0]=v;latch.countDown();});});
  check(latch.await(5,TimeUnit.SECONDS),"JavaScript timeout");return out[0];
 }
 private JSONObject report()throws Exception{
  String value=js("JSON.stringify({source:typeof current!=='undefined'&&current?current.provenance:null,count:typeof current!=='undefined'&&current?current.sampleCount:0,error:document.getElementById('error').textContent})");
  Object decoded=new JSONTokener(value).nextValue();return new JSONObject((String)decoded);
 }
 @Override public void onStart(){boolean passed=false;String stage="launch";
  try{
   activity=launch();waitForIdleSync();Thread.sleep(4000);screenshot("design_home_png");
   JSONObject stored=snapshot();check(!stored.optBoolean("activeAtCheckpoint",true),"Stop the current recording before demo");
   stage="open card";mark(stage);ActivityMonitor monitor=addMonitor("example.c01.ConsultationActivity",null,false);
   click("저장된 기록으로 상담 카드 만들기");Activity next=waitForMonitorWithTimeout(monitor,15000);removeMonitor(monitor);check(next!=null,"Card did not open");activity=next;
   JSONObject info=null;for(int i=0;i<30;i++){try{info=report();if("android-probe".equals(info.optString("source"))&&info.optInt("count")>0)break;}catch(Exception ignored){}Thread.sleep(500);}
   check(info!=null&&"android-probe".equals(info.optString("source"))&&info.optInt("count")>0,"No actual recording loaded");
   // The installed app and vehicle samples are unchanged. Only form input is automated.
   stage="form input";mark(stage);screenshot("design_form_png");
   // Demonstrate actual preset buttons; no synthetic typing animation or voice simulation.
   js("document.querySelector('[data-field=\"symptom\"]').scrollIntoView({block:'center'});'ok'");Thread.sleep(2000);
   check("true".equals(js("(()=>{document.querySelector('[data-field=\"symptom\"]').click();return document.getElementById('symptom').value.includes('달그락 소리가 들림');})()")),"Quick selection failed");
   result.put("quickSelectionPassed",true);Thread.sleep(2500);
   js("document.querySelector('[data-field=\"location\"]').scrollIntoView({block:'center'});document.querySelector('[data-field=\"location\"]').click();'ok'");Thread.sleep(2500);
   js("document.querySelectorAll('[data-field=\"conditions\"]')[1].click();'ok'");Thread.sleep(2500);
   String note="시연용 선택 (실제 증상 아님): 달그락 소리가 들림";
   js("document.getElementById('symptom').value="+JSONObject.quote(note)+";document.getElementById('symptom').scrollIntoView({block:'center'});'ok'");Thread.sleep(2500);
   js("document.getElementById('build').scrollIntoView({block:'center'});'ok'");Thread.sleep(2000);
   stage="generate card";mark(stage);screenshot("design_input_png");js("document.getElementById('build').click();document.getElementById('report').scrollIntoView({block:'start'});'ok'");Thread.sleep(3500);
   info=report();check("android-probe".equals(info.optString("source"))&&info.optInt("count")>0&&info.optString("error").isEmpty(),"Report generation failed");
   js("window.scrollBy(0,350);'ok'");Thread.sleep(3000);
   stage="save card";mark(stage);File file=new File(getTargetContext().getFilesDir(),"consultation-card.html");long old=file.lastModified();click("상담 카드 앱 내부 저장");
   for(int i=0;i<20&&file.lastModified()<=old;i++)Thread.sleep(250);
   check(file.lastModified()>old,"Card save not updated");String html=new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);check(html.contains(note)&&html.contains("가져온 Android 관측 파일"),"Saved card did not contain demo input and actual data provenance");
   Thread.sleep(3500);screenshot("demo_saved_png");result.put("card",info).put("automatedFormInput",true).put("symptomDescriptionIsExample",true).put("vehicleSamplesInjected",false).put("savedCardBytes",file.length());passed=true;
  }catch(Throwable e){try{result.put("failedStage",stage).put("error",e.toString());screenshot("failure_png");}catch(Exception ignored){}}
  finally{try{result.put("passed",passed).put("realVehicle",false);}catch(Exception ignored){}Bundle b=new Bundle();b.putString("result_json",result.toString());finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,b);}
 }
}
