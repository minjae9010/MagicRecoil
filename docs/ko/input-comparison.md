# 기존 총기 플러그인 비교

[English](../en/input-comparison.md) · [입력과 설정](aim-and-input.md)

사용자의 기억처럼 서버 전용 총기 플러그인들은 우클릭 유지 연사를 구현합니다. 반복 상호작용 이벤트를 버튼 유지의 근거로 사용하는 방식이 있으며, 이는 정확한 마우스 해제 신호를 받는 것과 구별해야 합니다. 2026-11-15에 아래 소스를 확인했으며 고정 커밋 링크는 검사한 코드의 기준입니다. 모든 배포 버전이 같다고 주장하지 않습니다.

| 플러그인 | 확인한 구현 | 의미 |
| --- | --- | --- |
| WeaponMechanics | `rightClicked()`에서 시간을 기록하고 `isRightClicking()`이 기본 215ms, ping 215ms 초과 시 `ping + 15ms`를 허용합니다. 방어 중인지도 별도로 검사합니다. | 반복 이벤트와 해제 유예 시간을 이용한 실용적 연사 |
| QualityArmory | `GunUtil.basicShoot()`의 자동 발사 작업이 마지막 RMB 이벤트 후 310ms를 넘으면 종료합니다. | 이벤트가 끊긴 시간을 기준으로 버튼 해제 추정 |
| CrackShot | 공식 문서가 우클릭 자동 발사를 권장하고 좌클릭 유지와 우클릭 반복을 구분합니다. | 우클릭 연사·좌클릭 조준은 기존 조작 방식입니다. 공식 0.98.13 바이트코드에서 짧은 발사 작업 버퍼도 확인했습니다 |

출처: [WeaponMechanics PlayerWrapper](https://github.com/WeaponMechanics/WeaponMechanics/blob/f9d2c4296e6b8d252ca00657861a7784dbd538f7/weaponmechanics-core/src/main/java/me/deecaad/weaponmechanics/wrappers/PlayerWrapper.java), [QualityArmory GunUtil](https://github.com/Lorenzo0111/QualityArmory/blob/2ba1ef96bc1f6b166a4eea676bf78f4bc51ea153/src/main/java/me/zombie_striker/qg/guns/utils/GunUtil.java), [CrackShot 공식 가이드](https://github.com/Shampaggon/CrackShot/wiki/The-Complete-Guide-to-CrackShot#right_click_to_shoot).

<!-- toc:start -->
## 목차

- [CrackShot 배포본 확인](#crackshot-배포본-확인)
- [조준 확대](#조준-확대)
- [MagicRecoil 적용 방식](#magicrecoil-적용-방식)
<!-- toc:end -->

## CrackShot 배포본 확인

공식 [CrackShot 0.98.13 배포 페이지](https://dev.bukkit.org/projects/crackshot/files/6635698)의 MD5 `2246894bb6a4aa1a212b25ba31c4d1bc`와 내려받은 JAR이 일치했습니다. `javap -c -p`로 `CSDirector.OnPlayerInteract`, 발사 메서드, `CSDirector$4.run`을 확인했습니다. 자동 사격은 1틱 간격의 처리 기회 5개를 설정하고 `rpm_shots`로 대기 중인 작업을 관리하며 다음 입력에서 부족한 작업을 보충합니다. 실행 시 대기 수를 줄이고 설정된 발사율을 검사합니다. 공통 해제 패킷이 아닌 유한한 미래 작업 버퍼입니다. 발사율에 관계없이 클릭마다 무조건 5발을 쏜다는 뜻은 아니며 슬롯·탄약·생명주기 검사로 더 일찍 멈출 수도 있습니다.

동일 버전의 조준은 `ironsights` 메타데이터를 토글하고 Slowness를 적용하며 기존 효과를 보존·복원합니다. 실제 망원경 사용 상태를 요구하지 않습니다. 이는 정적 바이트코드 검사이며 CrackShot을 Paper 1.21.11에서 실행한 결과가 아닙니다. 배포 JAR과 바이트코드 출력은 무시되는 로컬 조사 자료로만 보관하고 MagicRecoil에 재배포하지 않습니다.

## 조준 확대

WeaponMechanics의 [OutAbilitiesListener](https://github.com/WeaponMechanics/WeaponMechanics/blob/f9d2c4296e6b8d252ca00657861a7784dbd538f7/weaponmechanics-core/src/main/java/me/deecaad/weaponmechanics/packetlisteners/OutAbilitiesListener.java)는 abilities 패킷의 FOV modifier를 조정하고, [ScopeHandler](https://github.com/WeaponMechanics/WeaponMechanics/blob/f9d2c4296e6b8d252ca00657861a7784dbd538f7/weaponmechanics-core/src/main/java/me/deecaad/weaponmechanics/weapon/scope/ScopeHandler.java)는 조준 전환 시 abilities 업데이트를 보냅니다. 총기를 망원경 아이템으로 바꿀 필요가 없습니다. [공식 조준 문서](https://cjcrafter.gitbook.io/weaponmechanics/weapon-modules/scope)의 호박 오버레이는 별도의 시각 효과이며 확대 자체와 구분합니다.

## MagicRecoil 적용 방식

- `NATIVE_USE`: 지원하는 막대기·블레이즈 막대·괭이에 사용 컴포넌트를 임시로 붙이고 실제 Paper 사용 해제 이벤트에서 종료합니다. 재질, 모델 등 기존 정보를 유지하며 기본 모드에는 이벤트 타임아웃이 없습니다.
- `REPEATED_CLICK`: 반복 우클릭 이벤트, 매 서버 틱 검사, 명시적인 `release-timeout-ticks`를 사용하는 호환 모드입니다. 기본 6틱은 20 TPS에서 약 300ms이며 아이템 컴포넌트를 바꾸지 않습니다. 해제 추정 모드로 표시합니다.
- 좌클릭 AimSpell로 조준을 토글합니다. 선택 설정 `zoom.mode: FOV_EFFECT`는 복제한 abilities 패킷을 보내고 종료 시 현재 실제 abilities로 복원합니다. 클라이언트 FOV 효과 설정에 따라 확대 강도가 달라지며 0%에서는 확대되지 않습니다. 실제 망원경이나 정확한 광학 배율 보장이 아닙니다.

기존 총기 플러그인의 소스를 포함하거나 런타임 의존성으로 추가하지 않습니다. Paper·MagicSpells API의 동기 애드온 구조를 유지합니다. [실제 서버 검증](testing.md#우클릭-연사와-fov-검증)을 참고하세요.
