#!/usr/bin/env python3
"""Run in the user's ADB-authorized terminal; capture genuine app execution."""
from pathlib import Path
import subprocess,os,datetime,json,re,hashlib,base64,sys,time
root=Path(__file__).resolve().parent.parent
os.chdir(root)
adb=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'Library/Android/sdk')))/'platform-tools/adb'
serial=os.environ.get('C02_DEVICE','emulator-5554')
out=root/'research/pleos-runtime'/('c02-design-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'));out.mkdir(parents=True)
app=root/'research/c02-native-build/design-v6/C02-release-candidate.apk'
expected=hashlib.sha256(app.read_bytes()).hexdigest()
result={'purpose':'design review; automated input, not real driving validation','passed':False,'stage':'connect','automatedFormInput':True}
record=None;pid=None;device_video='/sdcard/'+out.name+'.mp4'
def run(*args,timeout=30):
 p=subprocess.run([str(adb),'-s',serial,*args],capture_output=True,text=True,timeout=timeout)
 if p.returncode:raise RuntimeError(p.stderr or p.stdout or str(args))
 return p.stdout
try:
 print('에뮬레이터 연결 확인',flush=True)
 devices=subprocess.run([str(adb),'devices'],capture_output=True,text=True,check=True).stdout
 if serial+'\tdevice' not in devices:
  emulator=adb.parent.parent/'emulator/emulator'
  avds=subprocess.run([str(emulator),'-list-avds'],capture_output=True,text=True,check=True).stdout.splitlines()
  avd=next((a for a in avds if a=='Pleos_Connect_v2.1.0_2'),next((a for a in avds if 'Pleos' in a),None))
  if not avd:raise RuntimeError('Pleos 가상 기기를 찾지 못했습니다. Device Manager에서 실행해 주세요.')
  print('Pleos 에뮬레이터를 시작합니다. 부팅을 기다리는 중…',flush=True)
  with open(out/'emulator.txt','w') as log:subprocess.Popen([str(emulator),'-avd',avd],stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
  run('wait-for-device',timeout=150)
  for attempt in range(90):
   if run('shell','getprop','sys.boot_completed').strip()=='1':break
   time.sleep(2)
  else:raise RuntimeError('에뮬레이터 부팅 시간 초과')
 assert run('get-state').strip()=='device'
 print('새 디자인 APK 설치 (기존 앱 데이터 유지)',flush=True)
 run('install','-r',str(app),timeout=90)
 user=run('shell','am','get-current-user').strip();assert user.isdigit()
 paths=run('shell','pm','path','--user',user,'example.c02.note').splitlines()
 apk=next(x.removeprefix('package:') for x in paths if x.endswith('/base.apk'))
 run('pull',apk,str(out/'installed.apk'))
 result['apk_sha256']=hashlib.sha256((out/'installed.apk').read_bytes()).hexdigest()
 assert result['apk_sha256']==expected,'Installed app differs from design candidate'
 print('새 디자인 APK 일치. 별도 조작 도구 설치',flush=True)
 run('install','-r',str(root/'research/c02-native-build/design-verification/C02-verification.apk'),timeout=60)
 run('shell','am','force-stop','example.c02.note')
 run('shell','pm','grant','--user',user,'example.c02.note','android.car.permission.CAR_SPEED')
 try:result['speech_handlers']=run('shell','cmd','package','query-activities','--brief','--user',user,'-a','android.speech.action.RECOGNIZE_SPEECH').strip()
 except Exception as e:result['speech_query_error']=str(e)
 result['stage']='recording'
 record=subprocess.Popen([str(adb),'-s',serial,'shell',f'echo $$; exec screenrecord --size 1280x720 --bit-rate 4000000 --time-limit 120 {device_video}'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 pid=record.stdout.readline().strip();assert pid.isdigit(),'screenrecord did not return a process ID'
 print('녹화 중: 앱 화면을 건드리지 말고 약 50초 기다려 주세요.',flush=True)
 p=subprocess.run([str(adb),'-s',serial,'shell','am','instrument','-w','-r','example.c02.verify/example.c02.verify.FlowCheck'],capture_output=True,text=True,timeout=100)
 raw=p.stdout+p.stderr;(out/'instrumentation.txt').write_text(raw)
 match=re.search(r'INSTRUMENTATION_RESULT: result_json=(.*)',raw)
 assert match,'No structured app result; see instrumentation.txt'
 result['native']=json.loads(match.group(1))
 for name in ['design_home_png','design_form_png','design_input_png','demo_saved_png','failure_png']:
  m=re.search(r'INSTRUMENTATION_STATUS: '+name+r'=([A-Za-z0-9+/=]+)',raw)
  if m:(out/(name+'.png')).write_bytes(base64.b64decode(m.group(1),validate=True))
 assert result['native'].get('passed') is True,str(result['native'])
 result['stage']='app-flow-passed'
except Exception as e:
 result['error']=str(e);print('확인 필요:',e,flush=True)
finally:
 if pid and pid.isdigit():
  try:run('shell','kill','-2',pid)
  except Exception:pass
 if record:
  try:record.communicate(timeout=12)
  except subprocess.TimeoutExpired:record.terminate();record.communicate(timeout=5)
 try:
  run('pull',device_video,str(out/'actual-operation.mp4'))
  p=subprocess.run(['ffprobe','-v','error','-show_entries','format=duration:stream=codec_name,width,height','-of','json',str(out/'actual-operation.mp4')],capture_output=True,text=True,check=True)
  result['video']=json.loads(p.stdout)
  assert float(result['video']['format']['duration'])>=15,'Video too short'
  subprocess.run(['ffmpeg','-v','error','-i',str(out/'actual-operation.mp4'),'-f','null','-'],check=True,capture_output=True)
  result['passed']=result.get('stage')=='app-flow-passed'
 except Exception as e:result['video_error']=str(e)
 (out/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
 (root/'research/pleos-runtime/latest-design-capture.json').write_text(json.dumps({'directory':str(out),'result':result},ensure_ascii=False,indent=2)+'\n')
 if result['passed']:
  print('완료: 실제 조작 녹화와 카드 저장 검증 성공',flush=True)
 else:print('미완료: 아래 폴더의 로그를 확인해야 합니다.',flush=True)
 print(out,flush=True)
sys.exit(0 if result['passed'] else 1)
