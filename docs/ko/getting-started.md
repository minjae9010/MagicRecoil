# 설치부터 첫 발사까지

[한국어 문서](README.md) · [English](../en/getting-started.md)

처음 설치하는 서버 운영자를 위한 순서입니다. 최소 반동을 먼저 확인한 뒤 기존 총기와 연결하면 설치 문제와 총기 설정 문제를 구분하기 쉽습니다.

<!-- toc:start -->
## 목차

- [1. 지원 환경 확인](#1-지원-환경-확인)
- [2. 플러그인과 최소 주문 설치](#2-플러그인과-최소-주문-설치)
- [3. 서버에서 최소 반동 확인](#3-서버에서-최소-반동-확인)
- [4. 원하는 수평 동작 선택](#4-원하는-수평-동작-선택)
- [5. 실제 총기의 발사 성공 분기에 연결](#5-실제-총기의-발사-성공-분기에-연결)
- [6. 변경 적용과 다음 확인](#6-변경-적용과-다음-확인)
<!-- toc:end -->

## 1. 지원 환경 확인

| 항목 | 대상 |
| --- | --- |
| 서버 | Paper 1.21.4 이상(호환 회전 API 필요) |
| Java | 21+ (26.x: 25) |
| MagicSpells | 4.0-Beta-17 이상 |
| MagicRecoil | 플러그인 JAR 한 개. sources JAR은 설치하지 않음 |

ProtocolLib은 필요 없습니다. [버전 호환 안내](compatibility.md)에서 이후 버전 허용 기준과 구버전 절대 회전 동작을 확인하세요. Folia는 지원하지 않으며 버전 변환은 별도 검증이 필요합니다. [지원 범위](../../README.ko.md#지원-환경)에서 세부 조건을 확인할 수 있습니다.

## 2. 플러그인과 최소 주문 설치

서버를 종료하고 [릴리스](https://github.com/minjae9010/MagicRecoil/releases)의 플러그인 JAR을 `plugins/`에 넣습니다. MagicSpells JAR과 같은 폴더이며 `plugins/MagicSpells/` 안에 애드온 JAR을 중복 설치하지 않습니다.

`plugins/MagicSpells/spells-recoil.yml`에 다음을 작성합니다.

```yaml
Test_Recoil:
  spell-class: "dev.magicrecoil.spells.RecoilSpell"
  recoil: 0.5
  pattern: vertical
  growth: 0.0
  recoil-steps: 2
```

`spell-class`에는 전체 클래스명이 필요합니다. 이 주문은 의도적으로 수직만 시험합니다. 아직 총기나 탄약 시스템은 연결하지 않습니다.

## 3. 서버에서 최소 반동 확인

서버를 켜고 콘솔에서 `Enabled rotation:`과 MagicSpells 로딩 완료를 확인합니다. 플레이어가 접속한 상태에서 **서버 콘솔**에 입력합니다.

```text
ms cast as PlayerName Test_Recoil
magicrecoil status
```

정면을 보고 마우스를 멈추면 위쪽으로 총 0.5°가 2틱에 나뉘어 적용됩니다. `PlayerName`은 실제 접속 이름으로 바꿉니다. 플레이어 명령은 MagicSpells 권한·주문 소유 조건을 충족해야 합니다.

## 4. 원하는 수평 동작 선택

| 목적 | 다음 설정 |
| --- | --- |
| 수직만 확인 | 현재 `pattern: vertical` 유지 |
| 첫 발부터 좌우 움직임 | `random` + 수평 범위·배율. [고정 수직/무작위 수평 예시](recoil-logic.md#첫-발부터-수평을-적용하고-수직은-일정하게-유지하는-예시) |
| 연사 후 좌우 흔들림 | `valorant`. 기본값에서는 5발째부터 수평 발생 |
| 직접 만든 순서 | `custom` + 패턴 점 목록 |

`horizontal`만 올린다고 모든 패턴에서 수평 반동이 생기지는 않습니다. [반동 계산 규칙](recoil-logic.md)을 먼저 확인하세요.

## 5. 실제 총기의 발사 성공 분기에 연결

기존 탄약·재장전 검사를 유지하고 **발사 주문 → 반동 주문**을 같은 틱에 연속 호출합니다. `require-success-of`는 실제 발사 주문 이름에 맞춥니다. 발사체를 PP에서 Beam으로 바꾸었다면 이 참조도 Beam 이름으로 바꿉니다.

재장전 시작과 사격 버프 종료에는 중지 옵션을 연결합니다. 최소 주문에 무작정 성공 확인만 추가하면 해당 발사 기록이 없어 실행되지 않습니다. 완성된 연결 예시는 [연동 가이드](integration.md)에 있습니다.

## 6. 변경 적용과 다음 확인

| 변경 | 적용 방법 |
| --- | --- |
| YAML 수치·패턴·주문 추가 | `/ms reload` 후 콘솔의 주문 오류 확인 |
| Java 코드·플러그인 JAR | 전체 서버 재시작 |
| 남은 반동만 정리 | `/magicrecoil reset PlayerName` |

원하는 반동이 나오면 탄약 없음·재장전·무기 교체·마우스 보정을 차례로 확인합니다. [테스트 체크리스트](testing.md)로 검수하고, 이상이 있으면 [증상별 문제 해결](troubleshooting.md)로 이동하세요.

---

[이전: 문서 안내](README.md) · [문서 안내](README.md) · [다음: 반동 로직](recoil-logic.md)
