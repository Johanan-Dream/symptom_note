#!/usr/bin/env python3
"""Reproducible local SDK build; separate app ID, release-signing key never packaged."""
from pathlib import Path
import argparse, os, secrets, subprocess, zipfile, base64, shutil, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent.parent
parser=argparse.ArgumentParser();parser.add_argument('--debug',action='store_true');parser.add_argument('--output-dir');args=parser.parse_args()
sdk=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'Library/Android/sdk')))
jdk=Path(os.environ.get('PROBE_JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
platform=sdk/'platforms/android-37.0';build=sdk/'build-tools/36.0.0'
out=Path(args.output_dir).resolve() if args.output_dir else root/'research/c02-native-build'/('debug' if args.debug else 'release');out.mkdir(parents=True,exist_ok=True)
for name in ['classes','dex','assets']: (out/name).mkdir(exist_ok=True)
env=os.environ.copy();env['JAVA_HOME']=str(jdk);env['PATH']=str(jdk/'bin')+os.pathsep+env['PATH']
def run(cmd):subprocess.run([str(v) for v in cmd],env=env,check=True)
ns='http://schemas.android.com/apk/res/android';ET.register_namespace('android',ns)
tree=ET.parse(root/'android/c01-probe/app/src/main/AndroidManifest.xml');m=tree.getroot()
m.set('package','example.c02.note');m.set('{%s}versionCode'%ns,'1');m.set('{%s}versionName'%ns,'0.2-candidate')
ET.SubElement(m,'uses-sdk',{'{%s}minSdkVersion'%ns:'34','{%s}targetSdkVersion'%ns:'35'})
app=m.find('application');app.set('{%s}label'%ns,'증상노트');app.set('{%s}debuggable'%ns,'true' if args.debug else 'false')
next(a for a in app.findall('activity') if a.find('intent-filter') is not None).set('{%s}name'%ns,'example.c01.ProbeActivity')
tree.write(out/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
html=(root/'prototype/c02/index.html').read_text()
for f in ['core.js','app.js']:html=html.replace(f'<script src="{f}"></script>','<script>\n'+(root/'prototype/c02'/f).read_text()+'\n</script>')
font=root/'assets/fonts/AstaSans.ttf'
if font.exists():
 shutil.copyfile(font,out/'assets/AstaSans.ttf')
 shutil.copyfile(root/'assets/fonts/OFL.txt',out/'assets/OFL.txt')
 encoded=base64.b64encode(font.read_bytes()).decode('ascii')
 html=html.replace('<style>',"<style>@font-face{font-family:'Asta Sans';src:url(data:font/ttf;base64,"+encoded+") format('truetype');font-weight:100 900;font-style:normal;}</style><style>",1)
(out/'assets/c02.html').write_text(html)
sources=list((root/'android/c01-probe/app/src/main/java/example/c01').glob('*.java'))+[root/'prototype/c01/android-probe/VehicleProbe.java']
run([jdk/'bin/javac','--release','17','-classpath',str(platform/'android.jar')+':'+str(platform/'optional/android.car.jar'),'-d',out/'classes',*sources])
run([jdk/'bin/jar','cf',out/'classes.jar','-C',out/'classes','.'])
run([build/'d8','--lib',platform/'android.jar','--lib',platform/'optional/android.car.jar','--min-api','34','--output',out/'dex',out/'classes.jar'])
run([build/'aapt2','link','-I',platform/'android.jar','--manifest',out/'AndroidManifest.xml','-A',out/'assets','-o',out/'unsigned.apk'])
with zipfile.ZipFile(out/'unsigned.apk','a') as z:z.write(out/'dex/classes.dex','classes.dex')
run([build/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk'])
keydir=root/'.local-signing';keydir.mkdir(mode=0o700,exist_ok=True)
password=keydir/'c02-password';keystore=keydir/'c02-release.jks'
if not password.exists():
 fd=os.open(password,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
 with os.fdopen(fd,'w') as f:f.write(secrets.token_urlsafe(32))
env['C02_SIGNING_PASSWORD']=password.read_text()
if not keystore.exists():
 run([jdk/'bin/keytool','-genkeypair','-keystore',keystore,'-storepass:env','C02_SIGNING_PASSWORD','-keypass:env','C02_SIGNING_PASSWORD','-alias','c02','-dname','CN=Symptom Note App','-keyalg','RSA','-validity','3650'])
 keystore.chmod(0o600)
apk=out/('C02-debug.apk' if args.debug else 'C02-release-candidate.apk')
run([build/'apksigner','sign','--ks',keystore,'--ks-pass','env:C02_SIGNING_PASSWORD','--key-pass','env:C02_SIGNING_PASSWORD','--out',apk,out/'aligned.apk'])
run([build/'apksigner','verify','--verbose',apk]);print(apk)
