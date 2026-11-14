from pathlib import Path
import os,subprocess,zipfile,argparse
parser=argparse.ArgumentParser();parser.add_argument("--design",action="store_true");args=parser.parse_args()
root=Path(__file__).resolve().parent.parent
sdk=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'Library/Android/sdk')))
jdk=Path(os.environ.get('PROBE_JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
build=sdk/'build-tools/36.0.0';platform=sdk/'platforms/android-37.0/android.jar'
out=root/('research/c02-native-build/design-verification' if args.design else 'research/c02-native-build/demo-verification');out.mkdir(parents=True,exist_ok=True)
for n in ['classes','dex']:(out/n).mkdir(exist_ok=True)
env=os.environ.copy();env['JAVA_HOME']=str(jdk);env['PATH']=str(jdk/'bin')+os.pathsep+env['PATH'];env['C02_SIGNING_PASSWORD']=(root/'.local-signing/c02-password').read_text()
def run(*cmd):subprocess.run([str(x) for x in cmd],env=env,check=True)
(out/'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="example.c02.verify"><uses-sdk android:minSdkVersion="34" android:targetSdkVersion="35"/><application android:label="C02 verification" android:debuggable="false"/><instrumentation android:name="example.c02.verify.FlowCheck" android:targetPackage="example.c02.note"/></manifest>''')
run(jdk/'bin/javac','--release','17','-classpath',platform,'-d',out/'classes',root/('android/c02-design-verification/FlowCheck.java' if args.design else 'android/c02-demo-verification/FlowCheck.java'))
run(jdk/'bin/jar','cf',out/'classes.jar','-C',out/'classes','.')
run(build/'d8','--lib',platform,'--min-api','34','--output',out/'dex',out/'classes.jar')
run(build/'aapt2','link','-I',platform,'--manifest',out/'AndroidManifest.xml','-o',out/'unsigned.apk')
with zipfile.ZipFile(out/'unsigned.apk','a') as z:z.write(out/'dex/classes.dex','classes.dex')
run(build/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk')
run(build/'apksigner','sign','--ks',root/'.local-signing/c02-release.jks','--ks-pass','env:C02_SIGNING_PASSWORD','--key-pass','env:C02_SIGNING_PASSWORD','--out',out/'C02-verification.apk',out/'aligned.apk')
run(build/'apksigner','verify',out/'C02-verification.apk')
