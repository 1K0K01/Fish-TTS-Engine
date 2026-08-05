# 🐟 Fish TTS Engine

Fish Audio의 보이스를 **안드로이드 시스템 TTS 엔진**으로 사용할 수 있게 해주는 앱입니다.
Moon+ Reader 등 시스템 TTS를 사용하는 모든 이북 리더기에서 Fish Audio의 자연스러운 음성으로 책을 들을 수 있습니다.

## ✨ 주요 기능

- **시스템 TTS 엔진 등록** — 안드로이드 TTS 설정에서 기본 엔진으로 선택 가능
- **S2.1 Pro 모델 지원** — 기본값 `s2.1-pro-free`(무료), 설정에서 `s2.1-pro` 등으로 변경 가능
- **보이스 관리** — 웹에서 만든 보이스 모델 ID를 추가/삭제/기본 지정
  - 기본 포함: 차분한 내레이터, 가치우
- **잡음 없는 재생** — MP3 합성 후 안드로이드 MediaCodec 자동 디코딩
- **한국어 이북 최적화** — 문장 단위 청크 분할(기본 500자). 문단·페이지 등 리더기가 보내는 긴 텍스트도 이어서 재생
- **PCM 캐시** — 반복 문장 즉시 재생 (설정에서 해제 가능)
- **배속 연동** — 리더기의 속도 설정을 Fish Audio `prosody.speed`로 전달

## 📲 설치

1. [Releases](releases/tag/latest)에서 `FishTTS-Engine-latest.apk` 다운로드
2. 설치 후 앱 실행
3. [fish.audio](https://fish.audio)에서 발급받은 **API 키** 입력 후 저장
4. **안드로이드 TTS 설정 열기** → 기본 엔진을 **Fish Audio TTS**로 선택
5. 이북 리더기에서 읽어주기 기능 사용

## ⚙️ 설정 항목

| 항목 | 기본값 | 설명 |
|---|---|---|
| API Key | - | fish.audio 대시보드에서 발급 |
| Endpoint | `https://api.fish.audio/v1/tts` | API 주소 |
| Model | `s2.1-pro-free` | 유료는 `s2.1-pro` |
| Locale | `ko-KR` | 보이스 언어 |
| Chunk 길이 | 500 | API 요청 단위 (문장 경계 존중) |

## 🛠️ 빌드

- GitHub Actions가 push마다 자동 빌드 → Releases에 APK 자동 배포
- 로컬 빌드: Android Studio에서 열거나 `gradle assembleDebug`

## 📁 프로젝트 구조

```
app/src/main/java/com/example/fishtts/
├─ MainActivity.kt             # 설정 화면 (보이스 관리)
├─ SecurePrefs.kt              # API 키 암호화 저장
├─ FishApiClient.kt            # Fish Audio API 호출
├─ Mp3Decoder.kt               # MP3 → PCM 디코딩
├─ TextChunker.kt              # 문장 단위 분할
├─ VoiceProfile.kt             # 보이스 모델 정의
└─ engine/
   └─ FishTtsEngineService.kt  # 시스템 TTS 엔진 핵심
```

## ⚠️ 참고사항

- API 키는 기기 내부에 암호화 저장됩니다.
- 합성할 텍스트는 Fish Audio 서버로 전송됩니다.
- 무료 모델(`s2.1-pro-free`)은 Fair Use 정책이 적용됩니다.
- 관련 프로젝트: [fishreader](https://github.com/1K0K01/fishreader) — 같은 API를 쓰는 웹 이북 리더

## 🙏

- [Fish Audio](https://fish.audio) API 사용
