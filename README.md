# Byakko — robot con Arduino UNO R4 WiFi + app Android

```
firmware/byakko/   Sketch de Arduino (un solo archivo: byakko.ino)
android/           Proyecto Android Studio (Kotlin + Jetpack Compose)
docs/PROTOCOLO.md  Protocolo de comunicación
docs/SUPUESTOS.md  Pines, supuestos y limitaciones
```

## Firmware
1. Arduino IDE → Gestor de placas → **Arduino UNO R4 Boards** (incluye `WiFiS3`).
2. Abrir `firmware/byakko/byakko.ino`, **borrar la línea `#define SIMULADOR`** (activa los encoders), placa *Arduino UNO R4 WiFi*, subir.
3. Monitor serie a 9600: muestra la red y la IP.

**Wi-Fi:** por defecto el robot crea su propia red `Byakko-Robot` (modo AP). Con `USAR_OTRA_RED = true` se une a otra red (casa/escuela); si no puede, crea la suya igual.

En el simulador AutoUnificado se pega tal cual (con `#define SIMULADOR`). Ahí no hay encoders y la velocidad se estima con la consigna.

Un solo `.ino` escrito en estilo Arduino simple: `const int` para pines, variables globales, funciones cortas y `if` con `indexOf` para los pedidos. Configuración al principio del archivo. El `loop()` reparte el tiempo con `millis()`: pedidos HTTP siempre, sonar cada 25 ms, modos cada 20 ms y PID de velocidad cada 100 ms.

- **Siguelínea**: error continuo `IR_der − IR_izq` (lecturas analógicas normalizadas) → PD → giro diferencial; frena en curvas.
- **Evasión**: cercanía `c = ((inicio − d)/(inicio − mín))^γ` por sensor (frontal 20→5 cm, laterales 15→4 cm). Giro = `Kf·c_front·lado_libre + Ks·(c_izq − c_der)`, filtrado; el avance baja continuamente con `c_front` hasta pasar a retroceso suave. Sin umbrales de “si d < X entonces…”.
- **Manual**: joystick → velocidades de rueda en lazo cerrado; G-code → `motion`.

## Dashboard de pruebas (PC)
Página para probar el robot desde la computadora, **por cable USB** o por Wi-Fi: cableado (invertir motores / cruzar encoders en vivo), motores con PWM directo, comparación de RPM entre motores (medición y barrido de PWM), modos, velocidad, flechas/WASD, G-code y gráfico de velocidad pedida vs. real.

1. Por USB: conectá el Arduino a la PC (no hace falta Wi-Fi). Por Wi-Fi: conectá la PC a la red `Byakko-Robot`.
2. Desde la carpeta del proyecto:
   ```bash
   python3 -m http.server 8000 --directory dashboard
   ```
3. Abrí `http://localhost:8000` en **Chrome, Edge o Chromium** (el USB usa Web Serial), elegí USB o Wi-Fi y tocá **Conectar**.

Por USB también se pueden escribir las rutas a mano en el monitor serie del IDE (9600 baudios), por ejemplo `/pwm?izq=150&der=0`.

Rutas que agrega el firmware para probar: `/pwm?izq=-255..255&der=-255..255` (PWM directo, sin PID) `/control?m=0|1|2` (PWM puro, PID con encoders o tabla de RPM medida) , `/config?inv_izq=0|1&inv_der=0|1&cruzar=0|1` (corrige el cableado sin recompilar) y `/evasion?inicio=&minimo=&kf=&kl=&vel=&giro=&freno=&retro=&seco=0|1` (ajusta la evasión en vivo; `seco=1` calcula sin mover los motores). `/estado` también informa `DL DR` (velocidad pedida), `TI TD` (ticks) y `CTRL`.

## App
1. Abrir `android/` en Android Studio (Ladybug o más nuevo); dejar que sincronice Gradle (genera el wrapper si falta).
2. Ejecutar en un teléfono con Android 10+.
3. Dos formas de conectarse:
   - **Conectar al robot** (botón principal): el teléfono se une a la red propia del robot (diálogo del sistema).
   - **¿El robot está en otra red Wi-Fi?**: con el teléfono y el robot en la misma red, **Buscar robot** prueba las direcciones de la red y lo encuentra solo. Si ya sabés la IP, cargala en *Editar* y tocá **Usar &lt;IP&gt;**.
4. Para manejar el **simulador**: en *Editar* poné la IP de la PC (ej. `192.168.1.15` o `192.168.1.15:5000`) y tocá **Usar &lt;IP&gt;** (celular en el mismo Wi-Fi que la PC).

APK de debug compilado: `android/app/build/outputs/apk/debug/app-debug.apk`.
Compilar por consola: `cd android && ./gradlew assembleDebug` (requiere JDK 17 y Android SDK).
