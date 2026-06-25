# MCExtremo Fabric

Mod para Minecraft/Fabric 1.20.4 pensado para un servidor privado dificil:
vidas limitadas, PvP programado, hordas, recompensas, arbol de habilidades,
zombies inteligentes y prueba de revive en el End.

## Instalacion

- Servidor: el mod debe estar instalado en el servidor Fabric/Arclight 1.20.4.
- Cliente: obligatorio. Los jugadores sin MCExtremo instalado en el cliente son
  expulsados al entrar porque el servidor requiere el canal de red del mod.
- Singleplayer: bloqueado desde el cliente. MCExtremo esta pensado solo para
  servidor dedicado con el mod instalado en servidor y cliente.

## Comandos rapidos

Alias limpio para jugadores y admins:

- `/mce lives [player]`: muestra vidas.
- `/mce status [player]`: muestra vidas, dia, PvP, trial y amenaza.
- `/mce days`: muestra fase zombie, multiplicador y siguiente desbloqueo.
- `/mce skills`: abre el arbol de habilidades.
- `/mce skills info`: lista habilidades desbloqueadas.
- `/mce upgrade`: mejora la armadura en mano con fragmentos.
- `/mce hearts [player]`: muestra corazones extra.
- `/mce revive <player>`: revive y desbanea.
- `/mce setlives <player> <amount>`: establece vidas.
- `/mce addlives <player> <amount>`: agrega vidas.
- `/mce dead`: lista eliminados.
- `/mce pvp <on|off>`: fuerza PvP.
- `/mce reload`: recarga config.

Herramientas avanzadas y debug tambien quedan bajo `/mce`, incluyendo `config`,
`revivearena`, `trial`, `event`, `skipdays`, `reducedays`, `spawnzombie` y
`zombieinfo`. El comando historico `/mcextremo` ya no se registra.

## Sistemas principales

- Vidas limitadas: al llegar a 0, el jugador entra a una prueba de revive si esta
  activada. Si falla todos los intentos, queda eliminado y baneado.
- Trial de revive: isla del End generada por jugador, kit temporal, inventario
  original oculto y restaurado, 4 oleadas normales y jefe final.
- Hordas: se activan cuando zombies detectan al jugador y escalan por dia.
- Recompensas: las hordas pueden dar Fragmentos de Horda y Corazones de Vida.
- Mejoras: los fragmentos suben Proteccion primero y luego Irrompibilidad.
- Skill tree: ramas de Vida, Fuerza, Resistencia, Agilidad, Tenacidad, Cazador
  y Supervivencia.

## Progresion zombie

- Dias `0-3`: inicio, zombies casi vanilla.
- Dias `4-10`: caceria, puertas y mini hordas.
- Dias `11-25`: coordinacion entre zombies.
- Dias `26-45`: asedio, rompen bloques blandos.
- Dias `46-70`: constructores, hacen escaleras para subir.
- Dias `71-99`: pesadilla, todo activo con mas presion.
- Dias `100+`: extremo, todo activo en maxima dificultad.

## Balance recomendado

La dificultad esta pensada para crecer por presion, no solo por vida extra. Los
zombies ganan velocidad, rango, coordinacion, hordas y opciones de persecucion.

Si el servidor tiene lag o se siente injusto:

- Baja `zombies.horda.maxGlobal`.
- Sube `zombies.horda.cooldownSegundos`.
- Baja los maximos de hordas grandes, masivas y extremas.
- Sube `zombies.construirBloques.diaInicio`.
- Deja `zombies.romperBloques.romperSoloBloquesBlandos=true`.

Si los jugadores se vuelven demasiado fuertes:

- Baja `zombies.horda.corazonChanceMasiva` y `corazonChanceExtrema`.
- Sube `mejoras.materialesPorNivel`.
- Baja `corazones.maxCorazonesPorJugador`.
- Ajusta costos del `skillTree`.

## Notas tecnicas

- El trial persiste el inventario original para sobrevivir reinicios durante la prueba.
- Los datos se escriben primero a `data.json.tmp` y luego se reemplazan
  atomicamente para reducir riesgo de corrupcion por crash durante guardado.
- El scoreboard evita recrear el objetivo completo cada segundo para reducir flicker.
- La IA constructora usa cooldowns de repath para evitar trabajo excesivo por tick.
- Para Arclight 1.20.4, prueben siempre con sus plugins reales de proteccion/claims.
