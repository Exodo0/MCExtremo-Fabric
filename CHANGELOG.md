# Changelog

## v1.1.33-beta.1

### Correcciones
- La Tormenta de Horda ahora respeta el limite global de zombies incluso con varios jugadores.
- Cancelar un evento mundial ya no entrega recompensas de finalizacion.
- Los cooldowns y la Tormenta de Horda se recuperan correctamente despues de reiniciar el servidor.
- Los efectos atmosfericos ahora se generan en la dimension real de cada jugador.
- Los puntos de control solo pueden generarse en el Overworld.
- Las villas interrumpidas por un reinicio reanudan su construccion automaticamente.
- El terreno de las villas se nivela, recibe cimientos y se despeja antes de construir.
- Los buffs de puntos se actualizan al capturar, perder o volver a entrar al servidor.
- Renovar una villa ya no acumula aldeanos persistentes duplicados.
- `misionesDiarias.resetAlCambioDia` ahora controla realmente el reinicio diario.
- `.codex/` queda excluido del repositorio para evitar agregar archivos extraidos por accidente.

## v1.1.32

### Mejoras
- Se mantuvo una implementacion propia para las villas de puntos de control, sin copiar codigo de mods externos ni adaptar mixins 1.20.1.
- Las villas ahora agregan portones fortificados en las cuatro entradas, techos mas completos, chimeneas, casa principal mas marcada y una zona verde interna.
- El estilo visual se acerca mas a una villa medieval fortificada sin depender de Integrated Villages, Create, Supplementaries o Moonlight.

## v1.1.31

### Mejoras
- Las villas de puntos de control ahora tienen un estilo mas medieval: edificios altos, techos escalonados de terracota, dark oak, taller, casa principal, faroles, bancas, jardineras y calles mas marcadas.
- El radio visual del asentamiento aumento para que se sienta mas como una villa fortificada.

### Correcciones
- `/mce puntos renovar <id>` ya no construye asentamientos en puntos sin dueno. Primero debe capturarse el punto.

## v1.1.30

### Mejoras
- Los comandos de `/mce` ahora autocompletan nombres de jugadores en argumentos administrativos.
- Los comandos de puntos ahora autocompletan IDs existentes, por ejemplo `/mce puntos renovar norte`.
- `/mce evento start` autocompleta tipos de evento mundial.
- `/mce pvp` autocompleta `on` y `off`.
- `/mce config get/set` autocompleta rutas de configuracion disponibles.

## v1.1.29

### Mejoras
- Los asentamientos de puntos de control ahora tienen mas contenido visual: caminos cardinales y diagonales, plaza central, mas casas, jardines, almacen, mercado, pozo, puestos de guardia, barriles, mesas de trabajo y decoracion.
- Nuevo comando admin: `/mce puntos renovar <id>` para reconstruir un asentamiento existente con el nuevo diseno.

### Correcciones
- La captura de puntos ahora se pausa si hay enemigos cerca. El jugador debe limpiar la zona antes de seguir avanzando el progreso.
- Nueva configuracion: `pausarCapturaConEnemigos` y `radioEnemigosCaptura`.

## v1.1.28

### Nuevo
- Los puntos de control ahora levantan un asentamiento al capturarse.
- La construccion del asentamiento es progresiva: murallas, caminos, casas, mercado, pozo, luces y aldeanos aparecen poco a poco con particulas y sonidos.
- Los puntos capturados ahora pueden activar eventos de defensa periodicos si el dueno esta conectado.
- Nuevo comando admin: `/mce puntos defensa <id>` para forzar una defensa de punto.
- Nuevas opciones de configuracion para defensas de puntos: activar/desactivar, frecuencia, duracion y cantidad base de mobs.

### Balance y experiencia
- Defender un punto exitosamente entrega fragmentos de mejora.
- Si un punto no se defiende durante el evento, pierde su dueno y vuelve a estar disponible.
- Los dueños offline no son castigados: el evento se pospone si el propietario no esta conectado.

## v1.1.27

### Sistemas de overworld
- Misiones diarias por jugador con progreso persistente y recompensas de fragmentos, experiencia y corazones.
- Eventos de mundo temporales: Eclipse Sangriento, Tormenta de Horda, Hora de Caza y Luna Corrupta.
- Puntos de control capturables con buffs pasivos segun la cantidad de puntos controlados.

### Bosses, trials y visuales
- Integracion de GeckoLib para bosses animados y placeholders reemplazables desde Blockbench.
- Estados sincronizados de boss desde servidor mediante DataTracker.
- Visual events centralizados para inicio de trial, hordas, cambios de fase, muerte de boss y victoria.
- Mejoras de cinematic networking y separacion cliente/servidor para evitar clases client-only en dedicated server.

### Correcciones recientes
- Limpieza de calaveras de Wither que quedaban flotando.
- Mobs mixtos en el trial normal.
- Mejoras de version handshake entre cliente y servidor.
- Fixes de bosses animados, estados duplicados y edge cases de version desconocida.
