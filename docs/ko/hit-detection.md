# 헤드샷·방향·거리 데미지와 적중 스펠

[한국어 목차](README.md) · [English](../en/hit-detection.md)

`dev.magicrecoil.spells.HitSpell`은 선택적으로 추가하는 공격 주문입니다. 기존 `RecoilSpell`과 `StopRecoilSpell`의 설정과 동작은 유지됩니다. 탄약·발사 조건·재장전은 기존 총기가 담당합니다. [전체 YAML 예제](../../examples/hit-integration.yml)를 실제 주문 이름에 맞춰 연결하세요.

<!-- toc:start -->
## 목차

- [즉시 명중 사격](#즉시-명중-사격)
- [기존 파티클 발사체에 연결](#기존-파티클-발사체에-연결)
- [기존 BeamSpell에 연결](#기존-beamspell에-연결)
- [방향과 헤드샷 규칙](#방향과-헤드샷-규칙)
- [데미지와 설정 범위](#데미지와-설정-범위)
- [적중 시 스펠 연계](#적중-시-스펠-연계)
- [리로드와 검증](#리로드와-검증)
<!-- toc:end -->

## 즉시 명중 사격

```yaml
MyGun_Hit:
  spell-class: dev.magicrecoil.spells.HitSpell
  helper-spell: true
  can-target: [players, nonplayers]
  range: 64
  damage: 6
  headshot-entities: [PLAYER, ZOMBIE, SKELETON]
  headshot-multiplier: 2
  direction-multipliers:
    back: 1.5
  distance-damage:
    - {distance: 0, multiplier: 1}
    - {distance: 15, multiplier: 1}
    - {distance: 50, multiplier: 0.5}
```

기존의 발사 성공 분기에서 `"MyGun_Hit(targeting=none)"` 다음에 반동 주문을 같은 틱에 호출합니다. 반동의 `require-success-of`는 `MyGun_Hit`으로 설정합니다. 시전자 눈 위치와 현재 방향으로 광선을 추적해 가장 가까운 블록 또는 유효 생명체에서 멈춥니다. 유체와 통과 가능한 블록은 무시하며, 자기 자신은 제외합니다. 빗나가거나 벽에 막혀도 정상 발사로 처리하므로 반동이 발생합니다. 유효하지 않은 시전자·서버 상태에서는 실패합니다.

## 기존 파티클 발사체에 연결

기존 `.instant.ParticleProjectileSpell`의 적중 콜백을 다음과 같이 교체합니다.

```yaml
# 기존 발사체 주문 안에 추가
spell-on-hit-entity: "MyGun_Hit(targeting=entity_from_location)"
```

`MyGun_Hit`은 위와 같은 `HitSpell` 설정입니다. `targeting=entity_from_location` 인자가 필요합니다. MagicSpells의 일반 entity 호출은 위치를 지우므로 이 인자를 생략하면 명중 처리가 실행되지 않습니다. 발사체와 HitSpell 양쪽의 `can-target`도 확인하세요.

콜백을 **직접 HitSpell에 연결**하고 지연·MultiSpell 래퍼를 사이에 넣지 마세요. 이벤트에서 발사체별 원점·이동 방향을 저장한 뒤 같은 틱의 콜백에서 한 번 사용합니다. 발사 후 사수가 이동해도 원점이 바뀌지 않습니다. 발사체의 비행·속도·탄퍼짐·수명·관통 설정은 기존 설정을 사용합니다. `range`는 즉시 사격에만 적용되고, 이 방식의 최대 거리는 기존 발사체의 `max-distance`가 결정합니다. 거리는 원점과 명중점 사이의 직선 거리이며 곡선 탄도의 누적 이동 길이가 아닙니다.

기존 데미지 콜백을 교체하여 피해가 이중으로 들어가지 않도록 합니다. 반동은 **발사체 발사 직후**에 실행하며 `require-success-of`도 기존 발사체 주문을 참조합니다. 명중 콜백에 반동을 연결하면 명중할 때까지 지연됩니다.

발사체의 실제 몸체 교차점을 복원하여 헤드샷을 판단합니다. 충돌 반경에만 걸리고 실제 탄도가 몸체와 교차하지 않으면 몸통으로 처리합니다. 다른 발사체 종류나 임의의 위치 호출은 지원하지 않으며, 발사 원점이 없는 호출은 데미지와 연계를 실행하지 않습니다.

## 기존 BeamSpell에 연결

기존 빔 주문에서 클래스와 엔티티 적중 콜백을 바꿉니다.

```yaml
MyGun_Beam:
  spell-class: dev.magicrecoil.spells.BeamHitSpell
  can-target: [players, nonplayers]
  relative-offset: 0, 1.62, 0
  max-distance: 64
  stop-on-hit-entity: false
  stop-on-hit-ground: true
  spell: "MyGun_Hit(targeting=entity_from_location)"
```

`BeamHitSpell`은 MagicSpells의 `BeamSpell`을 확장합니다. 빔의 `effects`, `spell-on-hit-ground`, `max-distance`, 대상 조건과 관통은 빔 주문이 처리합니다. `spell`은 직접 `HitSpell`을 가리키는 동기 콜백으로 설정합니다. 빔이 실제로 검사한 구간에서 몸체 교차점을 복원하며, 반경에만 걸린 대상은 몸통으로 판정합니다. 거리 배율에는 빔 원점부터 명중점까지의 직선 거리를 사용합니다. 반동의 `require-success-of`에는 `MyGun_Beam`을 지정합니다.

파이먼 전체 구성은 [빔 연동 YAML](../../examples/spells-paimon-beam.yml)을 참고하세요.

## 방향과 헤드샷 규칙

방향은 **피격자의 수평 시선 기준으로 공격이 들어온 쪽**입니다. 피격자의 위아래 시선 기울기는 수평 기준을 바꾸지 않습니다.

| 키 | 판정 |
| --- | --- |
| `front` / `back` | 피격자 앞 / 뒤에서 들어온 공격 |
| `left` / `right` | 피격자 왼쪽 / 오른쪽에서 들어온 공격 |
| `above` / `below` | 들어온 방향의 고도각이 `vertical-angle` 이상인 위 / 아래 공격 |

위·아래를 먼저 판정하고, 수평은 앞뒤·좌우 성분 중 큰 쪽을 선택합니다. 정확한 수평 대각선은 앞뒤가 우선합니다. 한 명중에는 방향 하나가 배정되며 헤드샷은 별도로 중첩될 수 있습니다.

머리 영역은 대상의 현재 충돌 상자 상단 `headshot-height` 비율로 시작합니다. 기본 0.25는 위쪽 25%이며, 웅크리기 등으로 상자가 변하면 함께 변합니다. **수영·활강 중인 플레이어는 영역 하한을 실제 눈높이까지 확장**하여 눈높이 명중도 헤드샷으로 처리합니다. 기본 0.6 높이 상자에서는 하한이 약 0.45에서 눈높이 약 0.40으로 내려갑니다. 설정한 영역이 이미 더 넓으면 그 영역을 유지합니다. 발사체 좌표의 미세한 반올림에는 0.000001블록의 경계 허용 오차를 적용합니다.

서 있는 플레이어·웅크린 플레이어·몹은 설정한 상단 비율을 사용합니다. 기본 대상은 `PLAYER`뿐이며 필요한 인간형 몹을 `headshot-entities`에 추가합니다. 목록을 비우면 헤드샷을 비활성화합니다. 이 기능은 서버 충돌 상자와 눈높이를 기준으로 한 근사 판정입니다. [PvP·자세 검증 결과](testing.md#플레이어-pvp와-수영활강-검증)를 참고하세요.

## 데미지와 설정 범위

```text
기본 적용량 = damage × 거리 배율 × 방향 배율 × 헤드샷 배율 × power
```

몸통의 헤드샷 배율은 1입니다. `power-affects-damage: false`이면 power 항도 1입니다. 거리 배율은 지점 사이를 선형 보간하고 첫 지점 이전·마지막 지점 이후는 양끝 배율로 고정합니다. 위 예제의 32.5블록은 거리 배율 0.75입니다. 배율을 증가시키는 곡선도 가능합니다. 거리 0부터 엄격한 오름차순을 권장하며 중복·역순·비정상 숫자는 로딩 오류입니다.

| 속성 | 기본값 | 범위와 적용 효과 |
| --- | --- | --- |
| `range` | 64 | 0.1..512블록. 즉시 사격으로 대상을 찾는 최대 거리입니다. 빔이나 파티클 발사체는 해당 주문의 `max-distance`를 사용합니다. |
| `damage` | 4 | 0..1,000,000 체력 단위. 배율과 방어 효과 적용 전의 피해입니다. 4는 하트 두 칸이며, 0이면 적중 효과만 실행할 수 있습니다. |
| `damage-type` | `minecraft:arrow` | 데이터팩을 포함한 Minecraft 레지스트리의 피해 유형 키 |
| `power-affects-damage` | `true` | 켜면 시전 시 전달받은 주문 위력(`power`)을 피해에 곱합니다. 끄면 위력이 달라져도 이 항의 배율은 1입니다. |
| `headshot-entities` | `[PLAYER]` | 헤드샷을 허용할 Bukkit 엔티티 이름 목록입니다. `[PLAYER, ZOMBIE]`면 둘 다 머리 판정을 받고, `[]`면 모든 명중을 몸통으로 처리합니다. |
| `headshot-height` | 0.25 | 0.01..1. 충돌 상자 위쪽에서 머리로 인정할 비율입니다. 0.25는 위쪽 25%이며 값을 키우면 영역이 아래로 넓어집니다. 수영·활강 중인 플레이어는 눈높이도 포함합니다. |
| `headshot-multiplier` | 2 | 0..100. 머리에 맞을 때만 곱하는 피해 배율입니다. 2면 같은 조건의 몸통 피해의 두 배입니다. |
| `vertical-angle` | 45 | 1..90°. 위·아래 공격으로 인정할 최소 각도입니다. 높일수록 더 가파른 공격만 위·아래로 분류합니다. |
| `direction-multipliers` | 각 방향 1 | 방향마다 0..100. 피격자를 기준으로 `front`, `back`, `left`, `right`, `above`, `below` 중 하나가 적용됩니다. 예를 들어 `back: 1.5`는 뒤에서 맞으면 피해가 1.5배입니다. |
| `distance-damage` | `[{distance: 0, multiplier: 1}]` | 1..128개 지점. 각 지점의 `distance`는 발사 원점부터의 거리(0..1,000,000블록), `multiplier`는 그 거리에서의 피해 배율(0..100)입니다. 거리가 증가하는 순서로 적으세요. |

위 예제에서 32.5블록 떨어진 대상의 뒤통수에 명중하고 주문 위력이 1이면, 방어 효과 적용 전 피해는 `6 × 0.75 × 1.5 × 2 = 13.5`입니다.

`SpellApplyDamageEvent`와 Paper의 피해 API에 설정한 피해 유형을 전달하고 시전자에게 공격 책임을 부여합니다. `shardra:shoot_dot` 같은 커스텀 유형은 서버 시작 전에 데이터팩을 설치해야 합니다. 등록되지 않은 키는 설정 오류를 기록하고 해당 스펠의 피해 처리를 중단합니다. 피해 유형 태그·방어구·저항·방패·다른 플러그인에 따라 실제 체력 감소량이 달라질 수 있습니다. `SpellTargetEvent`와 `can-target` 조건, 월드 PvP 설정도 적용됩니다.

## 적중 시 스펠 연계

`HitSpell` 아래에 사용할 줄만 골라 적으세요. 생략한 속성은 후속 주문을 실행하지 않습니다. 오른쪽 주문 이름은 실제로 정의한 주문 이름으로 바꾸세요.

```yaml
spell-on-hit: MyGun_HitEffect  # 유효한 대상에 명중할 때마다
spell-on-headshot: MyGun_HeadshotEffect  # 머리로 판정됐을 때
spell-on-bodyshot: MyGun_BodyEffect  # 몸통으로 판정됐을 때
spell-on-front: MyGun_FrontEffect  # 피격자의 앞쪽에서 맞았을 때
spell-on-back: MyGun_BackEffect  # 피격자의 뒤쪽에서 맞았을 때
spell-on-left: MyGun_LeftEffect  # 피격자의 왼쪽에서 맞았을 때
spell-on-right: MyGun_RightEffect  # 피격자의 오른쪽에서 맞았을 때
spell-on-above: MyGun_AboveEffect  # 위에서 내려오는 공격에 맞았을 때
spell-on-below: MyGun_BelowEffect  # 아래에서 올라오는 공격에 맞았을 때
```

한 명중에서 **공통 → 헤드샷 또는 몸통 → 해당 방향** 순서로 설정된 스펠을 각각 한 번 실행합니다. 피격자·명중 위치·기존 power를 전달합니다. 위치 타깃 스펠에는 피격자 발 위치 대신 명중 위치를 넘깁니다. 일반 entity 타깃 스펠은 MagicSpells의 규칙에 따라 위치 없이 대상을 받습니다. 후속 스펠의 power에는 명중 배율을 다시 곱하지 않습니다.

MagicSpells의 하위 주문 인자도 사용할 수 있습니다. 기본은 partial 호출이며 후속 스펠의 모든 비용·쿨다운 검사를 대신 보장하지 않습니다. 후속 스펠 자체의 타깃 조건·이벤트는 유지합니다.

```yaml
spell-on-headshot: "MyGun_HeadshotEffect(power=1.5)"
```

`damage: 0`이면 데미지 없이 판정과 연계만 실행합니다. 연계는 **유효한 충돌** 기준이므로 방패·무적·피해 이벤트 취소 등으로 실제 체력이 줄지 않아도 실행될 수 있습니다. 빗나감·벽 충돌·타깃 이벤트 취소에는 실행되지 않습니다. 없는 스펠이나 직접 자기 자신을 참조하면 해당 HitSpell을 비활성화하고 오류를 출력합니다. 동기 순환 호출도 차단합니다. 지연된 순환은 만들지 마세요.

## 리로드와 검증

YAML 변경은 `/ms reload`, 새 JAR은 서버 재시작이 필요합니다. 리로드 시 이전 적중 기록과 후속 스펠 참조를 정리합니다. 명중 기능을 쓰지 않는 기존 총기는 변경할 필요가 없습니다.

자동 테스트는 6방향·회전·머리 경계·자세 상자·거리 보간·배율 중첩·설정 오류·탄도 교차·발사 원점 보존·기록의 1회 소비를 확인합니다. 전체 테스트와 실제 서버 검증 결과는 [테스트 문서](testing.md#명중-기능-검증)를 참고하세요.
