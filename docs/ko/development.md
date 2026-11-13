# 개발 가이드

[한국어 목차](README.md) · [English](../en/development.md)

## 빌드와 의존성

JDK 21과 저장소에 포함된 Gradle 9.3.0 Wrapper를 사용합니다. `./gradlew clean build`는 실제 Paper 1.21.11 개발 번들과 공식 MagicSpells Beta 18 JAR을 사용합니다. MagicSpells 다운로드 및 Gradle 배포본은 SHA-256으로 검증합니다. Paper 개발 번들은 버전별 Maven snapshot이므로 완전히 고정된 오프라인 빌드를 주장하지는 않습니다.

Paper와 MagicSpells는 배포 JAR에 묶지 않습니다. 테스트는 JUnit, Bukkit YAML 기능, 스케줄러 격리용 Mockito를 사용합니다. `./gradlew test`는 설정, 패턴, 버퍼, 세션, 성공 기록, 스케줄러를 검사합니다. 100,000발 연사 시뮬레이션과 모의 플레이어 100명이 작업 하나를 공유하는 검증은 알고리즘 테스트이며 실제 네트워크 부하 시험이 아닙니다.

JAR 내부 순서는 고정하고 타임스탬프는 제거합니다. `-sources.jar`는 Java 소스이며 서버에 설치하지 않습니다. 플러그인 manifest의 `paperweight-mappings-namespace: mojang`이 Paper 런타임 이름을 지정합니다. 이 대상에서 reobfJar는 필요하지 않습니다.

## 소스 구성

`src/main/java/dev/magicrecoil/` 아래의 역할은 다음과 같습니다.

| 경로 | 역할 |
| --- | --- |
| `MagicRecoilPlugin.java` | 시작, 로더, 이벤트, 정리, 상태 명령어 |
| `spells/RecoilSpell.java` | Beta 18 주문 생성자와 시전 진입점 |
| `spells/StopRecoilSpell.java` | 재장전·교체 시 명시적 초기화 |
| `recoil/RecoilSettings.java` | 불변 설정과 엄격한 검증 |
| `recoil/RecoilPattern.java` | 패턴과 발당 강도 계산 |
| `recoil/ImpulseBuffer.java` | 크기가 고정된 틱 버퍼 |
| `recoil/RecoilSession.java` | 연사, 초기화 간격, 자동 복귀 |
| `recoil/RecoilManager.java` | UUID 세션과 공용 작업 |
| `integration/SuccessGate.java` | 같은 틱의 단일 소비 성공 기록 |
| `packet/RotationAdapter.java` | 버전 경계 |
| `packet/v1_21_11/` | 현재 NMS 구현 |

Java 연동은 플러그인 활성화를 확인하고 메인 스레드에서 `MagicRecoilPlugin.instance().reset(player.getUniqueId())`를 호출할 수 있습니다. YAML만으로 연결할 때는 StopRecoilSpell을 사용하세요.

## 차기 Minecraft 버전 대응

1. 대상 Paper 개발 번들과 필요한 Java 버전을 확인하고 새 어댑터 패키지를 만듭니다.
2. 회전 패킷 필드, relative 플래그, 서버 상태 변경 및 클라이언트 동작을 다시 조사합니다.
3. 대상 MagicSpells의 클래스 로더, 생성자, 시전 이벤트, 리로드 순서를 확인합니다.
4. 명시적인 버전 선택을 추가합니다. 모르는 버전에서 위치 텔레포트로 자동 대체하지 않습니다.
5. [테스트 절차](testing.md)의 서버·클라이언트 검증을 통과한 뒤 지원 표를 바꿉니다.

## CI와 배포

GitHub Actions는 push와 pull request마다 Java 21, Linux/Windows에서 clean build를 수행하고 JAR과 소스 JAR을 아티팩트로 올립니다. 릴리스에는 플러그인 JAR, 소스 JAR, SHA-256 체크섬과 아직 수행하지 못한 수동 시험 항목을 포함합니다.

서버 월드, 자격 증명, 외부에서 받은 의존성 JAR, `.work/`는 커밋하지 않습니다. 동작 변경에는 필요한 검증을 추가하고 영어·한국어 문서를 함께 갱신하세요. 서버 패킷만으로 프레임 단위 부드러움을 보장한다고 설명하면 안 됩니다.
