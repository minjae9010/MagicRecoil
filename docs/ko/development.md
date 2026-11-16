# 개발 가이드

[한국어 목차](README.md) · [English](../en/development.md)

빌드·코드 수정·버전 대응·문서 관리를 위한 개발 안내입니다. 사용만 하려면 [빠른 시작](getting-started.md)을, 내부 설계는 [구조](architecture.md)를 확인하세요.

<!-- toc:start -->
## 목차

- [빌드와 의존성](#빌드와-의존성)
- [소스 구성](#소스-구성)
- [차기 Minecraft 버전 대응](#차기-minecraft-버전-대응)
- [CI와 배포](#ci와-배포)
- [문서와 Wiki 관리](#문서와-wiki-관리)
- [ADS Java 변경 목록](#ads-java-변경-목록)
- [사격과 FOV Java 변경](#사격과-fov-java-변경)
<!-- toc:end -->

## 빌드와 의존성

JDK 21과 저장소에 포함된 Gradle 9.3.0 Wrapper를 사용합니다. `./gradlew clean build`는 Paper 1.21.4 API와 공식 MagicSpells Beta 17 JAR을 사용합니다. MagicSpells 다운로드 및 Gradle 배포본은 SHA-256으로 검증합니다. Paper API는 버전별 Maven snapshot이므로 완전히 고정된 오프라인 빌드를 주장하지는 않습니다.

Paper와 MagicSpells는 배포 JAR에 묶지 않습니다. 테스트는 JUnit, Bukkit YAML 기능, 스케줄러 격리용 Mockito를 사용합니다. `./gradlew test`는 설정, 패턴, 버퍼, 세션, 성공 기록, 스케줄러를 검사합니다. 100,000발 연사 시뮬레이션과 모의 플레이어 100명이 작업 하나를 공유하는 검증은 알고리즘 테스트이며 실제 네트워크 부하 시험이 아닙니다.

JAR 내부 순서는 고정하고 타임스탬프는 제거합니다. `-sources.jar`는 Java 소스이며 서버에 설치하지 않습니다. 플러그인 manifest의 `paperweight-mappings-namespace: mojang`이 Paper 런타임 이름을 지정합니다. 이 대상에서 reobfJar는 필요하지 않습니다.

## 소스 구성

`src/main/java/dev/magicrecoil/` 아래의 역할은 다음과 같습니다.

| 경로 | 역할 |
| --- | --- |
| `MagicRecoilPlugin.java` | 시작, 로더, 이벤트, 정리, 상태 명령어 |
| `spells/RecoilSpell.java` | Beta 17 이상 주문 생성자와 시전 진입점 |
| `spells/StopRecoilSpell.java` | 재장전·교체 시 명시적 초기화 |
| `recoil/RecoilSettings.java` | 불변 설정과 엄격한 검증 |
| `recoil/RecoilPattern.java` | 패턴과 발당 강도 계산 |
| `recoil/ImpulseBuffer.java` | 크기가 고정된 틱 버퍼 |
| `recoil/RecoilSession.java` | 연사, 초기화 간격, 자동 복귀 |
| `recoil/RecoilManager.java` | UUID 세션과 공용 작업 |
| `integration/SuccessGate.java` | 같은 틱의 단일 소비 성공 기록 |
| `packet/RotationAdapter.java` | 버전 경계 |
| `packet/PaperRotationAdapter.java` | 시작 시 상대·절대 회전 메서드 탐지와 호출 |
| `compat/` | 최소 버전과 MagicSpells 바이너리 API 검사 |

Java 연동은 플러그인 활성화를 확인하고 메인 스레드에서 `MagicRecoilPlugin.instance().reset(player.getUniqueId())`를 호출할 수 있습니다. YAML만으로 연결할 때는 StopRecoilSpell을 사용하세요.

## 차기 Minecraft 버전 대응

현재 어댑터는 시작 시 지원되는 Mojang 매핑 회전 메서드를 탐지해 저장합니다. API가 같은 이후 버전에는 버전 문자열을 추가할 필요가 없습니다. MagicSpells도 최소 버전과 필요한 바이너리 API를 검사합니다. [버전 호환 안내](compatibility.md)에 빌드 옵션과 검증 범위를 정리했습니다.

상위 API가 바뀌면 회전 전용 패킷과 서버 조준 상태 갱신을 조사한 뒤 어댑터를 확장하고 전송·서버·수동 검증을 반복하세요. 위치 텔레포트로 대체하지 않으며 핵심 반동 로직에는 NMS 클래스를 넣지 않습니다.

## CI와 배포

GitHub Actions는 Beta 17 기준을 Linux/Windows에서, Beta 18/Paper 1.21.11 및 Beta 19/Java 25 API를 Linux에서 빌드·테스트합니다. 배포 클래스는 Java 21 형식을 유지합니다. 릴리스에는 플러그인 JAR, 소스 JAR, SHA-256 체크섬과 아직 수행하지 못한 수동 시험 항목을 포함합니다.

서버 월드, 자격 증명, 외부에서 받은 의존성 JAR, `.work/`는 커밋하지 않습니다. 동작 변경에는 필요한 검증을 추가하고 영어·한국어 문서를 함께 갱신하세요. 서버 패킷만으로 프레임 단위 부드러움을 보장한다고 설명하면 안 됩니다.

## 문서와 Wiki 관리

저장소의 README와 `docs/ko/`, `docs/en/`를 원본으로 편집합니다. 각 페이지의 목적 설명과 이전·다음 링크를 유지하고, 두 언어를 함께 갱신합니다. `toc:start`와 `toc:end` 사이 목차는 자동 생성하므로 직접 고치지 않습니다.

```sh
python scripts/docs.py
python scripts/docs.py --check
```

첫 명령은 목차를 다시 만들고, 두 번째는 목차·로컬 링크·제목 앵커를 변경 없이 검사합니다. Python 표준 라이브러리만 사용합니다.

Wiki는 GitHub의 별도 Git 저장소입니다. 초기 페이지가 없으면 GitHub Wiki 화면에서 Home을 한 번 생성합니다. 아래 작업은 준비한 문서를 Wiki에 게시하므로 diff를 확인한 뒤 실행합니다. 저장소 문서 링크가 유효하도록 원본 문서 변경도 함께 게시하세요.

```sh
git clone https://github.com/minjae9010/MagicRecoil.wiki.git .work/wiki
python scripts/docs.py --export-wiki .work/wiki
git -C .work/wiki diff --check
git -C .work/wiki add -- "*.md"
git -C .work/wiki commit -m "Update bilingual documentation"
git -C .work/wiki push origin HEAD
```

이미 받은 Wiki에서는 clone 대신 `git -C .work/wiki pull --ff-only`를 먼저 실행합니다. 내보내기는 한국어·영어 페이지와 Home·사이드바·푸터를 생성하고 상대 문서 링크를 Wiki 링크로 바꿉니다. 원본에 없는 임의의 Wiki 페이지는 삭제하지 않습니다. 인증 정보와 `.work/`는 커밋하지 않습니다.

---

[이전: 구조](architecture.md) · [문서 안내](README.md)

## ADS Java 변경 목록

`src/main/java/dev/magicrecoil/` 아래에 `aiming/AimSettings.java`, `aiming/AimManager.java`, `spells/AimSpell.java`, `spells/StopAimSpell.java`를 추가했습니다. 기존 파일은 `MagicRecoilPlugin.java`, `spells/RecoilSpell.java`, `recoil/RecoilManager.java`, `recoil/RecoilSession.java`를 수정했습니다. 회전 어댑터, 반동 패턴·설정 구현, 성공 검증, 기존 중지 주문은 유지합니다. 별도 런타임 진단 플러그인에는 ADS 상태 조회를, JUnit에는 설정·상태 관리·조준 반동 검사를 추가합니다.

## 사격과 FOV Java 변경

`src/main/java/dev/magicrecoil/`에 `firing/AutoFireSettings.java`, `firing/AutoFireManager.java`, `firing/UseItemSupport.java`, `spells/AutoFireSpell.java`, `spells/StopAutoFireSpell.java`, `aiming/ZoomAdapter.java`, `packet/PaperZoomAdapter.java`를 추가했습니다. `MagicRecoilPlugin`, `AimSettings`, `AimManager`가 선택 기능을 등록·정리합니다. 이번 추가 작업에서 회전 어댑터·반동 패턴·성공 검증은 변경하지 않았습니다. [실제 검증 범위](testing.md#우클릭-연사와-fov-검증)를 참고하세요.

CrackShot 조작 추가 작업에서 `aiming/SlownessZoom.java`를 추가하고 AutoFireSettings/Manager, AimSettings/Manager, 파이먼 예제와 실제 서버 구성을 수정했습니다. 기존 반동·발사체·탄약 구현은 변경하지 않았습니다. 큐 테스트는 중복 이벤트·보충·5틱 소진·아이템 변경·재장전 차단을 확인합니다.
