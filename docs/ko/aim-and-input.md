# 조준과 입력 감지의 한계

[English](../en/aim-and-input.md) · [문서 안내](README.md)

**Paper 1.21.11 / 공식 MagicSpells 4.0-Beta-18**용 `dev.magicrecoil.spells.AimSpell`, `dev.magicrecoil.spells.StopAimSpell`을 추가합니다. 조준은 선택 기능입니다. AimSpell을 사용하지 않으면 기존 발사, 명령어, 반동 설정, 성공 검증은 기존 동작을 유지합니다. 새 런타임 의존성은 없습니다.

<!-- toc:start -->
## 목차

- [지원 범위](#지원-범위)
- [바인딩과 전체 설정](#바인딩과-전체-설정)
- [무기 소유와 속도 복원](#무기-소유와-속도-복원)
- [기존 Buff 총기와 반동 연동](#기존-buff-총기와-반동-연동)
- [HOLD·확대·자동 탄퍼짐을 제외한 이유](#hold확대자동-탄퍼짐을-제외한-이유)
- [검증](#검증)
<!-- toc:end -->

## 지원 범위

| 기능 | 결과 |
| --- | --- |
| 우클릭 조준 | MagicSpells의 기본 아이템 바인딩으로 성공한 시전마다 토글 |
| 비토글 조준 | `toggle: false`는 ON 상태를 유지하며 반복 호출해도 중첩하지 않음 |
| 이동속도 감소 | `MOVEMENT_SPEED`에 키가 있는 transient modifier 하나 추가, 해제 시 자기 modifier만 제거 |
| 반동 감소 | 플레이어와 반동 주문별로 새 발사에 배율 적용, 기존 패턴·growth 보존 |
| 좌클릭 HOLD 자동 사격 | 제외: 일반 공격 버튼의 해제 순간을 알리는 공통 프로토콜 신호가 없음 |
| 화면 확대 | 제외: 임의 총기에서 정확한 FOV 배율을 보장하는 방법이 검증되지 않음 |
| 자동 탄퍼짐 감소 | 제외: 이 애드온에서 범용적인 플레이어별 spread 재정의 방법이 검증되지 않음 |

## 바인딩과 전체 설정

[파이먼 병합 예제](../../examples/aim-paimon.yml)를 참고하세요. 기존 magic item `파이먼`과 RecoilSpell `Weapon_파이먼_반동`이 있어야 합니다. 일반 아이템으로 시험하려면:

```yaml
Gun_Aim:
  spell-class: "dev.magicrecoil.spells.AimSpell"
  always-granted: true
  right-click-cast-item: stick
  ignore-global-cooldown: false
  cooldown: 0.25
  recoil-spells: [Gun_Recoil]
  movement-speed-multiplier: 0.65
  recoil-multiplier: 0.5

Gun_StopAim:
  spell-class: "dev.magicrecoil.spells.StopAimSpell"
  helper-spell: true
```

입력 바인딩과 spellbook·권한 검사, `hardCast`, 비용, 쿨다운, modifiers는 MagicSpells의 기본 시전 경로를 사용합니다. 애드온은 클릭 리스너를 추가하거나 발사체 주문을 직접 호출하지 않습니다. 주 손의 총기에만 바인딩하세요. Beta 18의 우클릭 아이템 리스너에는 손 구분 필터가 없으므로 같은 바인딩 아이템을 보조 손에 함께 들지 말고, `ignore-global-cooldown: false`를 유지하여 같은 클릭의 중복 이벤트를 억제하세요. 우클릭을 계속 누르면 기본 게임이 상호작용을 반복하여 나중에 다시 토글할 수 있습니다. **시전별 토글이며 마우스 누름·해제 감지가 아닙니다.** 빠른 클릭은 MagicSpells 쿨다운에 막힐 수 있습니다. 실제 사용할 아이템·상호작용 블록·양손 조합을 게임에서 확인하세요.

| AimSpell 설정 | 기본값 | 허용값과 동작 |
| --- | --- | --- |
| `enabled` | `true` | Boolean. `false`면 활성화하지 않음. 리로드 시 기존 조준부터 정리 |
| `toggle` | `true` | Boolean. `false`면 반복 시전해도 OFF하지 않는 ON 동작, 별도 종료 필요 |
| `cancel-on-item-swap` | `true` | Boolean. 핫바 변경 시 종료. `false`면 상태를 보존하되 효과 일시 중지 |
| `cancel-on-hand-swap` | `true` | Boolean. 성공한 F키 교체 시 종료. `false`면 원래 아이템이 돌아올 때까지 효과 중지 |
| `cancel-on-death` | `true` | `true`만 허용. 사망 정리는 필수 |
| `cancel-on-logout` | `true` | `true`만 허용. 로그아웃 정리는 필수 |
| `movement-speed-multiplier` | `0.65` | 유한 YAML 숫자 `[0.01, 1]`. `1`은 속도 modifier를 추가하지 않음 |
| `recoil-multiplier` | `0.5` | 유한 YAML 숫자 `[0, 1]`. `0`은 새 발사의 회전을 전달하지 않음 |
| `recoil-spells` | 활성화 시 필수 | 이 총기의 RecoilSpell 내부 이름 1–128개. 대소문자 구분 없음 |
| `stop-on-spells` | `[]` | 주문 이름 최대 128개. 해당 주문의 실패를 포함한 시전 시도 시 조준 종료 |
| `stop-on-buff-end` | `[]` | Buff 이름 최대 128개. 해당 플레이어의 BuffEndEvent에서 종료 |
| `zoom.enabled` | `false` | `false`만 지원 |
| `zoom.level` | 미지원 | zoom이 꺼져 있어도 값을 지정하면 설정 거부 |
| `trigger` | 미지원 | MagicSpells의 `right-click-cast-item` 사용 |

Boolean·숫자는 문자열이 아닌 YAML 리터럴입니다. 그 외 MagicSpells 옵션은 MagicSpells가 처리합니다. StopAimSpell에는 애드온 전용 옵션이 없습니다. 일시 중지 상태를 포함한 해당 플레이어의 조준을 해제하고, 반동과 발사 Buff는 변경하지 않습니다. 기존 StopRecoilSpell과 `/magicrecoil reset`은 기존처럼 반동만 정리합니다.

## 무기 소유와 속도 복원

활성화할 때 **원래 핫바 슬롯과 주 손 ItemStack 복제본**을 기록합니다. `ItemStack.isSimilar`로 비교하므로 수량만 바뀌면 유지합니다. 로어·내구도·커스텀 데이터 등 메타데이터가 바뀌면 효과를 일시 중지합니다. 같은 슬롯의 완전히 동일한 스택끼리는 구분할 수 없습니다. 물리적 개체 식별자가 아닌 아이템 서명 비교입니다.

취소 옵션이 `false`면 다른 슬롯·아이템으로 변경해도 조준 상태는 남지만 그 아이템에는 속도·반동 감소를 적용하지 않습니다. 일치하는 아이템이 원래 슬롯으로 돌아오면 효과가 복귀합니다. 다른 AimSpell을 시전하면 기존 상태를 교체하며 중첩하지 않습니다. 바뀐 무기에 다시 연결하려면 토글을 껐다 켜거나 StopAimSpell 후 활성화하세요. 인벤토리 편집과 다른 플러그인의 아이템 변경은 공용 동기 작업이 매 틱 확인합니다. 슬롯·F키 이벤트에서는 속도를 즉시 제거하고, 그 외 아이템 변경은 최대 한 틱 지연될 수 있습니다. 반동은 시전마다 소유를 확인합니다. 인벤토리 클릭 자체만으로는 조준을 취소하지 않습니다.

Paper `addTransientModifier`로 `magicrecoil:aim_speed`, 값 `배율 - 1`, 연산 `MULTIPLY_SCALAR_1`을 추가합니다. 속성 기본값, 장비 modifier, 포션, 다른 플러그인의 modifier를 덮어쓰거나 과거 값으로 복구하지 않습니다. 조준 전 속성값 `0.1`은 `0.065`, 외부 효과로 `0.12`가 되면 조준 중 `0.078`, 해제 후 `0.12`가 됩니다. 속성 상한·하한과 게임 이동 상태 때문에 실제 초당 이동 거리·수영·비행 속도의 정확한 배율을 보장하지는 않습니다. 다른 플러그인은 이 전용 키를 재사용하면 안 됩니다. 외부에서 modifier를 모두 제거하면 활성 조준의 modifier는 다음 틱에 다시 추가됩니다.

사망, 로그아웃, 드롭, 텔레포트, 월드 변경, MagicSpells 리로드·비활성화, AimSpell unload, 애드온 종료 시 정리합니다. transient modifier는 플레이어 NBT에 저장되지 않으며 접속 시 남은 전용 키도 제거합니다. 상태가 없으면 작업을 종료합니다. Bukkit·MagicSpells API를 비동기 호출하지 않습니다.

## 기존 Buff 총기와 반동 연동

기존 `Weapon_파이먼_Buff → Weapon_파이먼_Left_Ticks → Weapon_파이먼_Beam → Weapon_파이먼_반동` 흐름을 유지하세요. `Paimon` 탄약 차감, 재장전 modifiers, 효과, 성공 분기를 보존합니다. **발사 Buff와 조준을 같은 우클릭 아이템에 동시에 바인딩하면 안 됩니다.** 조준을 사용하려면 우클릭을 ADS에 배정하고 좌클릭의 기존 Buff On/Off 발사는 유지하세요. 비활성화하려면 조준 바인딩을 제거하거나 `enabled: false` 후 `/ms reload`를 실행하고, 필요하면 기존 우클릭 Buff 바인딩을 복구합니다.

`recoil-spells`에 나열한 반동 주문에만 배율을 적용합니다. 패턴 샘플링, `vertical`, `horizontal`, `growth`, 난수 순서, 초기화 시간, steps, `require-success-of` 순서는 유지합니다. 한 발의 완성된 회전량 양 축에 배율을 곱한 뒤 기존 버퍼에 넣습니다. 패턴 진행과 누적 상승량 상한 계산은 원래 패턴을 기준으로 하므로 `0.5` 조준을 유지하면 총 상승량도 절반입니다. 연사 도중 토글해도 패턴 단계는 유지합니다. 이미 큐에 들어간 반동은 발사 당시 배율을 유지하며 auto-return은 실제 전달한 회전량을 사용합니다. 공유 RecoilSettings나 BeamSpell 값을 변경하지 않습니다.

실제 재장전 시작 주문을 `stop-on-spells`에 넣으세요. 시전 이벤트가 없는 direct 호출 구성은 기존 StopRecoilSpell과 함께 재장전 목록의 맨 앞에서 StopAimSpell을 호출하세요. 조준 종료 자체는 사격 Buff를 끄지 않습니다. 기존 재장전·탄약 조건이 발사를 계속 차단해야 합니다.

Beta 18 [`Subspell`](https://github.com/TheComputerGeek2/MagicSpells/blob/4.0-Beta-18/core/src/main/java/com/nisovin/magicspells/Subspell.java)의 기본 PARTIAL 호출은 이벤트·modifiers를 처리하지만 NORMAL 상태, 비용·쿨다운 0으로 시작합니다. DIRECT는 해당 이벤트 없이 `spell.cast(data)`를 호출합니다. 일반 비타겟 하위 호출의 HARD/FULL은 [`Spell.hardCast`](https://github.com/TheComputerGeek2/MagicSpells/blob/4.0-Beta-18/core/src/main/java/com/nisovin/magicspells/Spell.java)와 정상 사전·사후 처리 경로를 사용합니다. 애드온에는 새 자동 발사 호출기가 없으며 기존 호출 의미도 바꾸지 않습니다. Left_Ticks를 우회하여 Beam만 호출하지 마세요. PassiveSpell은 이벤트 기반 구성으로, 자동으로 모든 비용을 보존하는 재사용 발사 진입점이 되지 않습니다.

## HOLD·확대·자동 탄퍼짐을 제외한 이유

Paper [`Input`](https://jd.papermc.io/paper/1.21.11/org/bukkit/Input.html)은 이동·점프·웅크리기·달리기만 제공하며 공격 버튼 상태는 없습니다. 기본 swing·attack 패킷은 동작을, 블록 파괴 start/abort/stop은 특정 블록에 관한 행동을 나타냅니다. `RELEASE_USE_ITEM`은 아이템 사용 해제입니다. 공중이나 엔티티를 향한 좌클릭의 공통 해제 신호는 제공하지 않습니다. PlayerInteractEvent 반복이나 swing 타임아웃으로 마우스 유지를 증명할 수 없으며, ProtocolLib도 클라이언트가 보내지 않은 정보를 얻을 수는 없습니다. 따라서 AutoFireSpell, 발사 간격 옵션, 해제 추측 타임아웃 모드를 만들지 않고 기존 Buff On/Off를 유지합니다. 기존 반복 주문은 서버 틱으로 실행되며 20 TPS의 1틱은 약 0.05초이고 0초 발사를 보장하지 않습니다.

대상 Paper API에는 일반적인 플레이어 FOV 배율 설정기가 없습니다. 이동속도 효과는 FOV Effects 등 클라이언트 설정에 따라 시야에 영향을 줄 수 있지만 `zoom.level: 2.0`이나 망원경 확대가 아닙니다. abilities 패킷의 walk-speed 위조는 클라이언트 이동 예측과 다른 속도 업데이트에 영향을 주므로 제외했습니다. 실제 망원경은 해당 아이템의 사용 상태가 필요하고, 장비·사용 메타데이터 위조는 총기의 동작을 바꾸므로 검증된 임의 총기 확대 방식이 아닙니다. Paper [`startUsingItem`](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/LivingEntity.html#startUsingItem(org.bukkit.inventory.EquipmentSlot))도 실험 API이며 플레이어·아이템별 효과를 보장하지 않습니다. ProtocolLib 의존성이나 FOV 패킷 어댑터를 추가하지 않습니다.

Beta 18 [`BeamSpell`](https://github.com/TheComputerGeek2/MagicSpells/blob/4.0-Beta-18/core/src/main/java/com/nisovin/magicspells/spells/instant/BeamSpell.java)은 비공개 `ConfigData`의 spread 값을 시전별로 평가합니다. 총기 제작자가 기존 MagicSpells 동적 설정을 구성할 수는 있지만 이 애드온에서 ADS 소유 범위를 보장하는 범용적인 재정의 경로는 검증하지 못했습니다. 자동 탄퍼짐 감소는 제외하며 공유 spread 필드나 변수를 수정하지 않습니다.

## 검증

[ADS 검증 결과](testing.md#ads-확장-검증)와 [실제 서버 테스트 구성](../../tests/runtime/README.md#ads-integration-fixture)을 참고하세요. 단위 테스트, 실제 Paper에 합성 프로토콜 입력을 보낸 검사, 사람이 마우스·화면으로 확인하는 검사, 운영 총기 검증을 구분하여 보고합니다.
