<div align="center">

# 🐟 Fish TTS Engine

**Fish Audio의 목소리를, 당신이 쓰는 이북 리더의 시스템 TTS로.**

Moon+ Reader를 비롯해 안드로이드 시스템 TTS를 지원하는 모든 리더 앱에서
Fish Audio의 자연스러운 음성으로 책을 들을 수 있게 해주는 안드로이드 앱입니다.

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](#-설치)
[![Build](https://img.shields.io/badge/Build-GitHub_Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)](../../actions)
[![Engine](https://img.shields.io/badge/TTS_Engine-System_Level-black?style=flat-square)](#-주요-기능)
[![Voice](https://img.shields.io/badge/Voice-Fish_Audio_S2.1_Pro-FF6B6B?style=flat-square)](https://fish.audio)

</div>

---

## 왜 필요한가

기본 안드로이드 TTS는 로봇 같은 발음으로 오랜 독서를 힘들게 만듭니다.
**Fish TTS Engine**은 별도의 리더 앱 개조 없이, 안드로이드 TTS 설정에서
**엔진 하나만 바꿔주면** 지금 쓰던 이북 리더 그대로 Fish Audio의 자연스러운
목소리로 낭독을 들려줍니다.

```
기존: 이북 리더 ─▶ 안드로이드 기본 TTS ─▶ 딱딱한 합성음
변경: 이북 리더 ─▶ Fish TTS Engine ─▶ Fish Audio API ─▶ 자연스러운 음성
```

---

## ✨ 주요 기능

| 기능 | 설명 |
|---|---|
| 🔌 **시스템 TTS 엔진 등록** | 안드로이드 TTS 설정에서 기본 엔진으로 바로 선택 가능 |
| 🎙️ **S2.1 Pro 모델 지원** | 기본값 `s2.1-pro-free`(무료), 설정에서 `s2.1-pro` 등으로 변경 |
| 👤 **보이스 관리** | 웹에서 만든 보이스 모델 ID를 추가·삭제·기본 지정 (기본 포함: 차분한 내레이터, 가치우) |
| 🔇 **잡음 없는 재생** | MP3 합성 후 안드로이드 MediaCodec으로 자동 디코딩 |
| 📖 **한국어 이북 최적화** | 문장 단위 청크 분할(기본 500자) — 문단·페이지 단위의 긴 텍스트도 끊김 없이 이어서 재생 |
| ⚡ **PCM 캐시** | 반복되는 문장은 즉시 재생 (설정에서 해제 가능) |
| 🎚️ **배속 연동** | 리더기의 속도 설정을 Fish Audio `prosody.speed`로 그대로 전달 |

---

## 📲 설치

1. **[Releases](../../releases/latest)**에서 `FishTTS-Engine-latest.apk` 다운로드
2. 설치 후 앱 실행
3. [fish.audio](https://fish.audio)에서 발급받은 **API 키** 입력 후 저장
4. 안드로이드 **설정 → 접근성/텍스트 음성 변환** → 기본 엔진을 **Fish Audio TTS**로 선택
5. 이북 리더기에서 읽어주기(TTS) 기능 실행

> 설치부터 첫 낭독까지, 별도의 리더 앱 설정 없이 5단계면 끝납니다.

---

## ⚙️ 설정 항목

| 항목 | 기본값 | 설명 |
|---|---|---|
| API Key | – | fish.audio 대시보드에서 발급 |
| Endpoint | `https://api.fish.audio/v1/tts` | API 주소 |
| Model | `s2.1-pro-free` | 유료 전환 시 `s2.1-pro` |
| Locale | `ko-KR` | 보이스 언어 |
| Chunk 길이 | 500자 | API 요청 단위 (문장 경계 존중) |

---

## 🛠️ 빌드

- GitHub Actions가 push마다 자동 빌드 → Releases에 APK 자동 배포
- 로컬 빌드: Android Studio에서 열거나 `gradle assembleDebug`

---

## 📁 프로젝트 구조

```
app/src/main/java/com/example/fishtts/
├─ MainActivity.kt             # 설정 화면 (보이스 관리)
├─ SecurePrefs.kt              # API 키 암호화 저장
├─ FishApiClient.kt            # Fish Audio API 호출
├─ Mp3Decoder.kt                # MP3 → PCM 디코딩
├─ TextChunker.kt               # 문장 단위 분할
├─ VoiceProfile.kt              # 보이스 모델 정의
└─ engine/
   └─ FishTtsEngineService.kt  # 시스템 TTS 엔진 핵심
```

---

## ⚠️ 참고사항

- API 키는 기기 내부에 **암호화 저장**됩니다.
- 합성할 텍스트는 **Fish Audio 서버로 전송**됩니다.
- 무료 모델(`s2.1-pro-free`)은 **Fair Use 정책**이 적용됩니다.

---

## 🙏 Credits

- [Fish Audio](https://fish.audio) — 음성 합성 API 제공

<div align="center">

<sub>Made for readers who want their ebooks to sound less like a machine.</sub>

</div>
