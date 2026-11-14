# 증상노트

정비소에서 재현되지 않는 증상을 설명하기 위한 Pleos Connect 차량용 앱입니다. 차주의 기억과 차량의 속도 기록을 구분해 한 장의 상담 카드로 정리합니다.

## 바로 확인하기

- [검증한 APK 다운로드](https://github.com/Johanan-Dream/symptom_note/raw/refs/heads/main/downloads/C02-release-candidate.apk)
- [구현·검증 범위](docs/verification.md)

## 사용 흐름

주차 상태에서 기록 시작 → 내비게이션 사용 → 도착 후 기록 종료 → 기억나는 구간과 증상·위치·상황 선택 → 상담 카드 확인·내부 저장.

현재 수집 신호는 차량 속도입니다. 고장 진단, 소음 분석, 외부 공유, 음성 입력은 현재 완료 기능에 포함하지 않습니다. STT는 Pleos SDK 및 마이크 사용 조건이 확인된 후 추가할 계획입니다.

## 설치

Pleos Connect v2.1.0 / Android 14(API 34) ARM64 에뮬레이터에서 확인했습니다. 일반 휴대폰용 앱이 아닙니다.

```sh
adb install -r downloads/C02-release-candidate.apk
adb shell am start -n example.c02.note/example.c01.ProbeActivity
```

속도 권한 요청을 허용해야 기록할 수 있습니다. 데이터는 앱 내부에 저장되며 최근 세션이 이전 기록을 대체합니다. 앱 삭제 시 데이터도 삭제됩니다. 다른 서명으로 설치된 앱에는 업데이트 설치가 실패할 수 있으므로 기존 자료를 확인한 뒤 설치해야 합니다.

## 소스 빌드

Python 3, JDK 17 이상, Android SDK가 필요합니다. 확인한 도구는 Android Studio JBR 25, SDK platform android-37.0(선택 라이브러리 android.car.jar 포함), Build Tools 36.0.0입니다. 실행 대상은 API 34 이상입니다.

```sh
# macOS 기본 SDK/Android Studio 위치를 사용합니다.
# 다른 위치에서는 ANDROID_HOME과 PROBE_JAVA_HOME을 지정합니다.
python3 scripts/build-c02-local.py --output-dir research/c02-native-build/design-v6
```

출력: `research/c02-native-build/design-v6/C02-release-candidate.apk`.
스크립트는 javac/d8/aapt2/zipalign/apksigner를 사용합니다. Gradle 프로젝트 빌드나 lint 통과를 의미하지 않습니다. 빌드 시 `.local-signing/`에 개인용 키가 생성되며 저장소에는 업로드하지 않습니다. 재빌드 APK는 새 서명 때문에 제공 APK와 전체 파일 해시가 다릅니다.

## 로직 테스트

Node.js 18 이상에서 실행합니다.

```sh
node --test prototype/c02/core.test.cjs prototype/c02/consultation.test.cjs prototype/c02/timeline.test.cjs prototype/c02/import.test.cjs prototype/c02/input.test.cjs
```

45개 테스트를 통과했습니다. 배포 소스를 다시 빌드해 검증 APK의 `classes.dex`, 상담 카드 HTML, 글꼴, 매니페스트와 바이트 단위로 일치함을 확인했습니다. [재빌드 검사](evidence/source-rebuild.json)

## 실제 조작 검증 재실행

위 명령으로 앱을 먼저 빌드한 다음 실행합니다. 실행 중 앱의 선택·저장 상태가 변경되므로 검증용 에뮬레이터를 사용합니다. adb와 ffmpeg/ffprobe가 필요합니다.

```sh
python3 scripts/build-c02-demo-verification.py --design
python3 scripts/record-c02-design.py
```

## 자료 구분

`android/`는 네이티브 화면·기록 서비스, `prototype/c02/`는 앱 내 상담 카드 로직 및 보조 웹 UI입니다. 기존 `c01` 디렉터리 이름은 개발 이력이며 제공 APK의 패키지는 `example.c02.note`입니다. 보조 웹 화면을 실제 APK 시연으로 표시하지 않습니다.

검증은 에뮬레이터 자동 조작으로 진행했습니다. 차량 표본을 주입하지 않았고 증상 설명만 시연용으로 입력했습니다.

Asta Sans 글꼴은 [SIL Open Font License](assets/fonts/OFL.txt)를 따릅니다. [출처](assets/fonts/source.json)를 함께 제공합니다. 기획과 개발에 생성형 AI를 보조적으로 활용했습니다.
