# MCExtremo Boss Animations

MCExtremo usa GeckoLib para los jefes principales del Revive Trial y Event Trial.

## Rutas de assets

- Modelo: `src/main/resources/assets/mcextremo/geo/trial_boss.geo.json`
- Animaciones: `src/main/resources/assets/mcextremo/animations/trial_boss.animation.json`
- Textura: `src/main/resources/assets/mcextremo/textures/entity/trial_boss.png`
- Modelo arana guardian: `src/main/resources/assets/mcextremo/geo/trial_guardian_spider.geo.json`
- Animaciones arana guardian: `src/main/resources/assets/mcextremo/animations/trial_guardian_spider.animation.json`
- Textura arana guardian: `src/main/resources/assets/mcextremo/textures/entity/trial_guardian_spider.png`

Puedes reemplazar estos archivos desde Blockbench manteniendo el mismo nombre de archivo.

## Animaciones requeridas

El archivo `trial_boss.animation.json` debe declarar estas claves:

- `animation.trial_boss.idle`
- `animation.trial_boss.walk`
- `animation.trial_boss.run`
- `animation.trial_boss.spawn_intro`
- `animation.trial_boss.roar`
- `animation.trial_boss.basic_attack`
- `animation.trial_boss.special_attack`
- `animation.trial_boss.summon_minions`
- `animation.trial_boss.phase_transition`
- `animation.trial_boss.stunned`
- `animation.trial_boss.death`

Si una animacion todavia no esta terminada, deja una animacion placeholder con la misma clave para evitar errores en runtime.

## Sincronizacion

El servidor controla el estado real de animacion mediante `DataTracker`:

- `TrialBossEntity` replica `TrialBossState`.
- `TrialGuardianSpiderEntity` replica `TrialGuardianSpiderState`.

El cliente no debe decidir estados de boss al recibir packets. Los packets como `BOSS_ANIMATION` solo existen como compatibilidad/aviso visual; el renderer de GeckoLib lee el estado replicado por la entidad. Esto evita desyncs donde un cliente ve una animacion distinta al estado real del combate.

`TrialBossAnimations.fromState(CHASING)` apunta a `WALK` como fallback seguro. En runtime el controller de GeckoLib decide entre `WALK` y `RUN` usando velocidad/sprint del boss, por eso el enum no necesita dos estados separados para persecucion lenta y rapida.

La arana guardian del Velo usa estas claves:

- `animation.trial_guardian_spider.idle`
- `animation.trial_guardian_spider.walk`
- `animation.trial_guardian_spider.attack`
- `animation.trial_guardian_spider.summon`
- `animation.trial_guardian_spider.death`

## Sonidos

La implementacion actual usa sonidos vanilla como placeholders. Para agregar sonidos propios:

1. Coloca archivos `.ogg` en `src/main/resources/assets/mcextremo/sounds/`.
2. Crea o actualiza `src/main/resources/assets/mcextremo/sounds.json`.
3. Cambia el mapeo de sonidos en `TrialVisualClientController`.

Sonidos recomendados:

- `trial_start`
- `boss_spawn`
- `boss_roar`
- `boss_phase`
- `boss_attack`
- `boss_death`
- `horde_start`
- `trial_complete`
- `trial_failed`

## Config

La seccion `visuals` de la config controla:

- `enableBossIntroAnimations`
- `enableTrialScreenEffects`
- `enableHordeVisualEffects`
- `bossIntroDurationTicks`
- `phaseTransitionDurationTicks`
- `bossDeathDurationTicks`
- `maxVisualParticlesPerTick`

Si algun cliente tiene problemas de rendimiento, desactiva overlays o baja la intensidad visual desde la config.
