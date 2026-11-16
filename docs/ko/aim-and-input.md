# 조준과 입력 감지의 한계

[English](../en/aim-and-input.md) · [문서 안내](README.md)

**Paper 1.21.4 이상 / 호환되는 MagicSpells 4.0-Beta-17 이상**용 `dev.magicrecoil.spells.AimSpell`, `dev.magicrecoil.spells.StopAimSpell`을 추가합니다. 조준은 선택 기능입니다. AimSpell을 사용하지 않으면 기존 발사, 명령어, 반동 설정, 성공 검증은 기존 동작을 유지합니다. 새 런타임 의존성은 없습니다. 현재 권장 조작은 우클릭 연사·좌클릭 조준이며 이전 바인딩도 사용할 수 있습니다.

서버별 지원과 기능 조건은 [버전 호환 안내](compatibility.md)를 확인하세요. 기본 BUFFERED_CLICK과 Slowness 줌은 USE_EFFECTS 없이 동작하며, 선택적 NATIVE_USE는 해당 Paper API가 있을 때만 활성화됩니다.

<!-- toc:start -->
## 목차

- [지원 범위](#지원-범위)
- [바인딩과 전체 설정](#바인딩과-전체-설정)
- [무기 소유와 속도 복원](#무기-소유와-속도-복원)
- [기존 Buff 총기와 반동 연동](#기존-buff-총기와-반동-연동)
- [우클릭 자동 사격](#우클릭-자동-사격)
- [CrackShot식 Slowness 확대](#crackshot식-slowness-확대)
- [선택 FOV 확대](#선택-fov-확대)
- [좌클릭과 탄퍼짐의 한계](#좌클릭과-탄퍼짐의-한계)
- [검증](#검증)
<!-- toc:end -->

## 지원 범위

| 기능 | 결과 |
| --- | --- |
| 좌클릭 조준 | MagicSpells의 기본 아이템 바인딩으로 성공한 시전마다 토글 |
| 비토글 조준 | `toggle: false`는 ON 상태를 유지하며 반복 호출해도 중첩하지 않음 |
| 이동속도 감소 | `MOVEMENT_SPEED`에 키가 있는 transient modifier 하나 추가, 해제 시 자기 modifier만 제거 |
| 반동 감소 | 플레이어와 반동 주문별로 새 발사에 배율 적용, 기존 패턴·growth 보존 |
| 좌클릭 HOLD 자동 사격 | 제외: 일반 공격 버튼의 해제 순간을 알리는 공통 프로토콜 신호가 없음 |
| 선택 FOV 확대 | CrackShot식 Slowness 조준 또는 선택 abilities 패킷 효과. 실제 망원경 아님 |
| 우클릭 연사 | 기본은 입력으로 보충하는 5틱 발사 버퍼. 기본 사용·타임아웃도 선택 가능 |
| 자동 탄퍼짐 감소 | 제외: 이 애드온에서 범용적인 플레이어별 spread 재정의 방법이 검증되지 않음 |

## 바인딩과 전체 설정

[파이먼 병합 예제](../../examples/aim-paimon.yml)를 참고하세요. 기존 magic item `파이먼`과 RecoilSpell `Weapon_파이먼_반동`이 있어야 합니다. 일반 아이템으로 시험하려면:

```yaml
Gun_Aim:
  spell-class: "dev.magicrecoil.spells.AimSpell"
  always-granted: true
  left-click-cast-item: stick
  ignore-global-cooldown: false
  cooldown: 0.25
  recoil-spells: [Gun_Recoil]
  movement-speed-multiplier: 0.65
  recoil-multiplier: 0.5

Gun_StopAim:
  spell-class: "dev.magicrecoil.spells.StopAimSpell"
  helper-spell: true
```

입력 바인딩과 spellbook·권한 검사, `hardCast`, 비용, 쿨다운, modifiers는 MagicSpells의 기본 시전 경로를 사용합니다. AimSpell은 별도 클릭 리스너를 추가하지 않습니다. AutoFireSpell은 자체 use-item 입력을 등록하고 기존 발사 처리 주문 전체를 호출하며 발사체·데미지를 직접 구현하지 않습니다. 주 손의 총기에만 바인딩하세요. Beta 18의 아이템 바인딩 리스너에는 손 구분 필터가 없으므로 같은 바인딩 아이템을 보조 손에 함께 들지 말고, `ignore-global-cooldown: false`를 유지하여 같은 클릭의 중복 이벤트를 억제하세요. 이전 우클릭 조준 구성은 상호작용 반복으로 다시 토글할 수 있습니다. 우클릭 연사와 함께 사용할 때는 좌클릭 조준으로 배정하세요. **시전별 토글이며 마우스 누름·해제 감지가 아닙니다.** 빠른 클릭은 MagicSpells 쿨다운에 막힐 수 있습니다. 실제 사용할 아이템·상호작용 블록·양손 조합을 게임에서 확인하세요.

| AimSpell 설정 | 기본값 | 허용값과 동작 |
| --- | --- | --- |
| `enabled` | `true` | Boolean. `false`면 활성화하지 않음. 리로드 시 기존 조준부터 정리 |
| `toggle` | `true` | Boolean. `false`면 반복 시전해도 OFF하지 않는 ON 동작, 별도 종료 필요 |
| `cancel-on-item-swap` | `true` | Boolean. 핫바 변경 시 종료. `false`면 상태를 보존하되 효과 일시 중지 |
| `cancel-on-hand-swap` | `true` | Boolean. 성공한 F키 교체 시 종료. `false`면 원래 아이템이 돌아올 때까지 효과 중지 |
| `cancel-on-death` | `true` | `true`만 허용. 사망 정리는 필수 |
| `cancel-on-logout` | `true` | `true`만 허용. 로그아웃 정리는 필수 |
| `movement-speed-multiplier` | `0.65`, SLOWNESS 모드는 `1` | 유한 YAML 숫자 `[0.01, 1]`. `1`은 속도 modifier를 추가하지 않음 |
| `recoil-multiplier` | `0.5`, SLOWNESS 모드는 `1` | 유한 YAML 숫자 `[0, 1]`. `0`은 새 발사의 회전을 전달하지 않음 |
| `recoil-spells` | 활성화 시 필수 | 이 총기의 RecoilSpell 내부 이름 1–128개. 대소문자 구분 없음 |
| `stop-on-spells` | `[]` | 주문 이름 최대 128개. 해당 주문의 실패를 포함한 시전 시도 시 조준 종료 |
| `stop-on-buff-end` | `[]` | Buff 이름 최대 128개. 해당 플레이어의 BuffEndEvent에서 종료 |
| `zoom.enabled` | `false` | Boolean. 선택 FOV 효과 활성화 |
| `zoom.mode` | 미지정 | 확대 활성화 시 `SLOWNESS` 또는 `FOV_EFFECT` 명시 필수 |
| `zoom.slowness-amplifier` | `2` | SLOWNESS 전용 정수 `[0, 5]`. 2는 Slowness III이며 광학 3배 아님 |
| `zoom.fov-factor` | `0.65` | 유한 숫자 `[0.55, 1]`. 다른 FOV 영향이 없고 지상·FOV 효과 100%일 때 목표 시야 계수 |
| `zoom.level` | 미지원 | 정확한 광학 배율 계약이 아니므로 fov-factor 사용 |
| `trigger` | 미지원 | 조준은 MagicSpells의 `left-click-cast-item` 사용 |

Boolean·숫자는 문자열이 아닌 YAML 리터럴입니다. 그 외 MagicSpells 옵션은 MagicSpells가 처리합니다. StopAimSpell에는 애드온 전용 옵션이 없습니다. 일시 중지 상태를 포함한 해당 플레이어의 조준을 해제하고, 반동과 발사 Buff는 변경하지 않습니다. 기존 StopRecoilSpell과 `/magicrecoil reset`은 기존처럼 반동만 정리합니다.

## 무기 소유와 속도 복원

활성화할 때 **원래 핫바 슬롯과 주 손 ItemStack 복제본**을 기록합니다. `ItemStack.isSimilar`로 비교하므로 수량만 바뀌면 유지합니다. 로어·내구도·커스텀 데이터 등 메타데이터가 바뀌면 효과를 일시 중지합니다. 같은 슬롯의 완전히 동일한 스택끼리는 구분할 수 없습니다. 물리적 개체 식별자가 아닌 아이템 서명 비교입니다.

취소 옵션이 `false`면 다른 슬롯·아이템으로 변경해도 조준 상태는 남지만 그 아이템에는 속도·반동 감소를 적용하지 않습니다. 일치하는 아이템이 원래 슬롯으로 돌아오면 효과가 복귀합니다. 다른 AimSpell을 시전하면 기존 상태를 교체하며 중첩하지 않습니다. 바뀐 무기에 다시 연결하려면 토글을 껐다 켜거나 StopAimSpell 후 활성화하세요. 인벤토리 편집과 다른 플러그인의 아이템 변경은 공용 동기 작업이 매 틱 확인합니다. 슬롯·F키 이벤트에서는 속도를 즉시 제거하고, 그 외 아이템 변경은 최대 한 틱 지연될 수 있습니다. 반동은 시전마다 소유를 확인합니다. 인벤토리 클릭 자체만으로는 조준을 취소하지 않습니다.

Paper `addTransientModifier`로 `magicrecoil:aim_speed`, 값 `배율 - 1`, 연산 `MULTIPLY_SCALAR_1`을 추가합니다. 속성 기본값, 장비 modifier, 포션, 다른 플러그인의 modifier를 덮어쓰거나 과거 값으로 복구하지 않습니다. 조준 전 속성값 `0.1`은 `0.065`, 외부 효과로 `0.12`가 되면 조준 중 `0.078`, 해제 후 `0.12`가 됩니다. 속성 상한·하한과 게임 이동 상태 때문에 실제 초당 이동 거리·수영·비행 속도의 정확한 배율을 보장하지는 않습니다. 다른 플러그인은 이 전용 키를 재사용하면 안 됩니다. 외부에서 modifier를 모두 제거하면 활성 조준의 modifier는 다음 틱에 다시 추가됩니다.

사망, 로그아웃, 드롭, 텔레포트, 월드 변경, MagicSpells 리로드·비활성화, AimSpell unload, 애드온 종료 시 정리합니다. transient modifier는 플레이어 NBT에 저장되지 않으며 접속 시 남은 전용 키도 제거합니다. 상태가 없으면 작업을 종료합니다. Bukkit·MagicSpells API를 비동기 호출하지 않습니다.

## 기존 Buff 총기와 반동 연동

기존 `Weapon_파이먼_Buff → Weapon_파이먼_Left_Ticks → Weapon_파이먼_Beam → Weapon_파이먼_반동` 흐름을 유지하세요. `Paimon` 탄약 차감, 재장전 modifiers, 효과, 성공 분기를 보존합니다. 새 조작에서는 기존 발사 Buff의 마우스 바인딩을 제거하고 AutoFireSpell의 `use-item`을 등록하며 조준은 좌클릭에 배정합니다. 기존 Buff 정의는 보존하되 두 발사 루프를 동시에 실행하면 안 됩니다. [우클릭 파이먼 병합 예제](../../examples/right-click-paimon.yml)를 참고하세요. 이전 우클릭 조준 예제는 [aim-paimon-legacy.yml](../../examples/aim-paimon-legacy.yml)에 보존했습니다. 조준을 끄려면 `enabled: false` 후 `/ms reload`, 자동 발사를 끄려면 AutoFireSpell을 제거하거나 비활성화한 뒤 이전 바인딩을 복구합니다.

`recoil-spells`에 나열한 반동 주문에만 배율을 적용합니다. 패턴 샘플링, `vertical`, `horizontal`, `growth`, 난수 순서, 초기화 시간, steps, `require-success-of` 순서는 유지합니다. 한 발의 완성된 회전량 양 축에 배율을 곱한 뒤 기존 버퍼에 넣습니다. 패턴 진행과 누적 상승량 상한 계산은 원래 패턴을 기준으로 하므로 `0.5` 조준을 유지하면 총 상승량도 절반입니다. 연사 도중 토글해도 패턴 단계는 유지합니다. 이미 큐에 들어간 반동은 발사 당시 배율을 유지하며 auto-return은 실제 전달한 회전량을 사용합니다. 공유 RecoilSettings나 BeamSpell 값을 변경하지 않습니다.

실제 재장전 시작 주문을 `stop-on-spells`에 넣으세요. 시전 이벤트가 없는 direct 호출 구성은 기존 StopRecoilSpell과 함께 재장전 목록의 맨 앞에서 StopAimSpell을 호출하세요. 조준 종료 자체는 사격 Buff를 끄지 않습니다. 기존 재장전·탄약 조건이 발사를 계속 차단해야 합니다.

Beta 18 [`Subspell`](https://github.com/TheComputerGeek2/MagicSpells/blob/4.0-Beta-18/core/src/main/java/com/nisovin/magicspells/Subspell.java)의 기본 PARTIAL 호출은 이벤트·modifiers를 처리하지만 NORMAL 상태, 비용·쿨다운 0으로 시작합니다. DIRECT는 해당 이벤트 없이 `spell.cast(data)`를 호출합니다. 일반 비타겟 하위 호출의 HARD/FULL은 [`Spell.hardCast`](https://github.com/TheComputerGeek2/MagicSpells/blob/4.0-Beta-18/core/src/main/java/com/nisovin/magicspells/Spell.java)와 정상 사전·사후 처리 경로를 사용합니다. AutoFireSpell은 일반 발사 처리 주문을 hardCast하여 권한·modifiers·비용·쿨다운을 유지합니다. 양수인 최상위 cast-time은 예약 전에 차단하고 DIRECT/PARTIAL로 검사를 우회하지 않습니다. Left_Ticks를 우회하여 Beam만 호출하지 마세요. PassiveSpell은 이벤트 기반 구성으로, 자동으로 모든 비용을 보존하는 재사용 발사 진입점이 되지 않습니다.

## 우클릭 자동 사격

`dev.magicrecoil.spells.AutoFireSpell`의 `use-item`에 MagicSpells magic-item 이름이나 아이템 문자열을 지정합니다. `right-click-cast-item`을 함께 지정하면 안 됩니다. MagicSpells의 해당 바인딩은 기본 아이템 사용을 취소하기 때문입니다. 새 입력 세션마다 AutoFireSpell의 기본 MagicSpells 시전 검사를 거친 뒤, 일반 동기 `fire-spell`을 `interval-ticks` 간격으로 hardCast합니다. 발사체·데미지를 직접 구현하지 않습니다.

| 설정 | 기본값 | 허용값·동작 |
| --- | --- | --- |
| `enabled` | `true` | YAML Boolean. false 후 리로드하면 바인딩 제거 |
| `use-item` | 필수 | magic-item 이름 또는 아이템 문자열. MagicSpells cast-item 비교 옵션 적용 |
| `fire-spell` | 필수 | 일반 동기 발사 처리 주문. PassiveSpell·AutoFireSpell은 대상 지정 거부 |
| `interval-ticks` | `2` | 정수 `[1, 1200]`. 호출 대상의 실제 쿨다운도 적용 |
| `input-mode` | `BUFFERED_CLICK` | `BUFFERED_CLICK`, `NATIVE_USE`, `REPEATED_CLICK` |
| `release-timeout-ticks` | `6` | REPEATED_CLICK 전용. 정수 `[5, 40]` |
| `stop-on-spells` | `[]` | 재장전·종료 주문 최대 128개. 시도 시 연사를 중지하고 입력을 놓을 때까지 차단 |
| `stop-on-buff-end` | `[]` | BuffEnd 참조 최대 128개 |

`BUFFERED_CLICK`가 기본 CrackShot식 모드입니다. 주 손 우클릭마다 앞으로 5틱 안의 부족한 발사 기회를 보충하며 대기는 최대 5개입니다. 중복 이벤트가 버스트를 쌓거나 한 틱에 여러 번 발사하게 만들지 않습니다. 예약된 기회에도 interval-ticks와 전체 발사 주문의 검사를 적용합니다. 추가 입력이 없으면 5틱 안에 버퍼가 소진되므로 20 TPS에서 약 0.25초의 잔여 처리가 생길 수 있으며 즉각적인 버튼 해제 감지는 아닙니다. 재장전·아이템/양손 변경과 생명주기 훅은 세션을 즉시 중지합니다. 아이템의 재질·모델·로어·속성·커스텀 데이터는 수정하지 않으며 이 모드에는 재질 허용 목록이 없습니다. 기존 메타데이터와 일치하는 고유 magic-item 이름으로 바인딩하세요.

선택 `NATIVE_USE`는 막대기, 블레이즈 막대, 나무·돌·철·금·다이아몬드·네더라이트 괭이를 지원합니다. 재질·모델·이름·로어·속성은 유지하고 Consumable/UseEffects 컴포넌트만 임시 추가합니다. 사용 애니메이션·입자·소리·추가 감속 없이 실제 주 손 사용 상태를 매 틱 확인하고, Paper PlayerStopUsingItemEvent로 해제 신호를 받으면 연사를 멈춥니다. 반복 클릭을 기다리지 않습니다. 기존의 커스텀 Consumable/UseEffects가 있는 아이템은 덮어쓰지 않고 거부합니다. 슬롯·인벤토리 처리, 사망·드롭·로그아웃·리로드·종료 시 준비 정보를 제거하며 재접속 시에도 복구합니다. 한 번의 기본 사용 길이는 1시간이고 실제 소비는 취소하여 총기를 잃지 않도록 합니다. 다른 플러그인이 아이템을 로드되지 않은 상자 등으로 옮기면 전역 검색·복구는 불가능하므로 애드온 제거 전 해당 표시 아이템을 플레이어에게 돌려 정리하세요.

`REPEATED_CLICK`는 아이템 컴포넌트를 수정하지 않으며 다른 재질에도 사용할 수 있습니다. 등록한 총기의 기본 우클릭 동작을 막고 주 손의 반복 상호작용을 확인합니다. 기본 6틱 동안 다음 이벤트가 없으면 연사를 종료합니다. 20 TPS에서 약 0.30초의 **해제 추정 방식**이며 정확한 버튼 해제 감지가 아닙니다. 검사는 서버 틱마다 한 번 실행합니다. 더 빠르게 검사해도 클라이언트가 입력을 보내는 주기는 줄지 않으며 패킷 공백으로 세션이 끊길 수 있습니다. 이후 새 클릭은 새 세션을 시작할 수 있습니다. 일반 재질 전체를 실수로 총기로 바꾸지 않도록 이름이 고유한 magic-item을 권장합니다.

모든 발사는 공용 동기 작업 하나에서 실행합니다. 아이템·양손 변경, 사망, 로그아웃, 텔레포트, 리로드·종료 시 중단합니다. 재장전 중지 훅은 발사 버퍼가 소진되거나 기본 사용이 해제되거나 반복 입력 타임아웃이 끝날 때까지 차단합니다. 이벤트가 없는 direct 재장전에는 `dev.magicrecoil.spells.StopAutoFireSpell`을 맨 앞에서 호출하고, 필요하면 StopAimSpell·StopRecoilSpell도 함께 사용하세요. 호출 대상이 자체적으로 지연·버스트 하위 주문을 예약하면 입력 종료 후에도 그 작업은 계속될 수 있으므로 발사 처리는 동기 경로로 구성해야 합니다. 이미 발사된 발사체는 기존 동작을 유지합니다.

파이먼의 기존 성공 처리에 있는 modifiers, Paimon 차감, Beam·반동 호출, 소리·입자, 쿨다운을 일반 `.MultiSpell` 발사 처리로 분리하세요. 기존 `buffactive` 검사는 이전 Passive 래퍼에 남기고, 구 방식에서는 해당 래퍼가 공용 발사 처리를 hardCast하도록 연결할 수 있습니다. 기존 Buff 연사와 AutoFire를 동시에 켜면 안 됩니다. interval-ticks를 짧게 해도 대상 주문의 쿨다운·비용·탄약·재장전 검사는 우회하지 않습니다. 0초 발사 간격은 지원하지 않습니다.

## CrackShot식 Slowness 확대

기본 파이먼 예제는 좌클릭 `zoom.mode: SLOWNESS`, `zoom.slowness-amplifier: 2`를 켭니다. `recoil-multiplier: 1.0`, `movement-speed-multiplier: 1.0`으로 기존 반동을 유지하고 별도 ADS 속성 감속은 추가하지 않습니다. Slowness 자체는 CrackShot처럼 실제 이동속도와 클라이언트 시야를 줄입니다. 실제 망원경은 아니며 클라이언트 FOV 효과 설정에 따라 보이는 확대가 달라집니다.

조준 중 10틱 길이의 Slowness를 갱신합니다. 조준 종료 시 현재 적용한 효과를 제거하고, 이전 활성 Slowness는 경과한 틱을 뺀 남은 시간으로 복원하며 무한 지속도 보존합니다. 기존 효과가 더 강하거나 같거나 숨은 효과 체인이 있으면 그대로 둡니다. 외부 플러그인의 교체·제거는 우선하며 해당 조준 세션의 추가 갱신을 중지합니다. 다른 포션 종류는 건드리지 않고 취소된 포션 이벤트도 존중합니다. 슬롯·F·사망·로그아웃·리로드·정상 종료에서 정리하며, 갱신이 끊긴 뒤 애드온 감속이 남더라도 최대 10서버 틱입니다. 강제 종료 후 백업된 이전 효과의 복구까지 보장하는 것은 아닙니다.

## 선택 FOV 확대

클라이언트 설정에 영향을 받는 확대가 필요할 때만 `zoom.enabled: true`, `zoom.mode: FOV_EFFECT`, `zoom.fov-factor`를 지정하세요. Paper 1.21.11 어댑터는 현재 실제 abilities를 복제하고 기본 게임의 FOV 계산에 쓰이는 송신 패킷의 walking-speed 값만 조정합니다. 서버의 walk speed·속성 기본값을 바꾸거나 Slowness·포션 효과를 건드리지 않습니다. 비행·크리에이티브 플래그도 복사합니다. 활성화 중 매 틱 갱신하며 해제 시 다른 플러그인이 바꾼 값을 포함한 현재 실제 abilities로 복원합니다.

클라이언트 FOV 효과 **100%**, 지상 상태의 이동 관련 기본 식은 `(movementAttribute / packetWalkingSpeed + 1) / 2`입니다. 현재 이동 속성과 목표 시야 계수로 양수·유한 패킷 값을 계산합니다. FOV 효과 **0%**에서는 확대가 보이지 않습니다. 비행·달리기·클라이언트 모드와 다른 FOV 영향에 따라 최종 화면이 달라집니다. 실제 망원경 확대·오버레이나 정확한 광학 2배 줌을 보장하지 않습니다. ProtocolLib을 추가하지 않으며 다른 플러그인의 abilities 패킷과 경쟁할 수 있습니다. 실제 렌더링·안티치트 환경은 게임 내 검증이 필요합니다.

## 좌클릭과 탄퍼짐의 한계

일반적인 좌클릭의 정확한 HOLD 연사는 계속 제외합니다. Paper Input에는 공격 버튼 상태가 없고 블록 파괴·아이템 사용 해제는 공통 공격 해제 신호가 아닙니다. 우클릭의 실제 사용 상태에는 활용 가능한 해제 신호가 있고, 버퍼와 타임아웃 방식은 별도로 표시한 반복 입력 모드입니다. CrackShot의 발사 작업 버퍼와 Slowness 조준을 포함한 [기존 총기 구현 비교](input-comparison.md)를 참고하세요.

BeamSpell의 비공개 ConfigData spread는 총기 작성자가 동적으로 구성할 수 있지만 이 애드온은 범용적인 플레이어별 재정의를 추가하거나 공유 spread 필드를 고치지 않습니다. 자동 탄퍼짐 감소는 제외합니다.

## 검증

[ADS 검증 결과](testing.md#ads-확장-검증)와 [실제 서버 테스트 구성](../../tests/runtime/README.md#ads-integration-fixture)을 참고하세요. 단위 테스트, 실제 Paper에 합성 프로토콜 입력을 보낸 검사, 사람이 마우스·화면으로 확인하는 검사, 운영 총기 검증을 구분하여 보고합니다.
