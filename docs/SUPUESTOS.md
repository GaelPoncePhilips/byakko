# Hardware, supuestos y datos faltantes

## Hardware usado (de los esquemas provistos)
- Arduino UNO R4 WiFi (el diagrama muestra un UNO; la consigna pide R4 WiFi — mismo formato y pines).
- Puente H L298N, 2 motores DC 3–6 V con reductora (tipo TT), 2 portapilas 4×AA y un interruptor.
- 2 encoders ópticos de ranura (LM393, **un solo canal**) con disco de 20 ranuras.
- 3 HC-SR04, 2 sensores IR analógicos de siguelínea, 3 LEDs.

## Pines
| Función | Pin | Fuente |
|---|---|---|
| IN1/IN2 motor izq (adelante/atrás) | 5 / 6 | config del simulador y .ino |
| IN3/IN4 motor der | 10 / 11 | ídem |
| Encoder der / izq | 2 / 3 (interrupciones) | .ino 6ta actividad |
| Ultrasonido frontal trig/echo | 9 / 8 | ídem |
| Ultrasonido izquierdo trig/echo | 12 / 13 | config del simulador |
| Ultrasonido derecho trig/echo | 7 / 4 | config del simulador |
| IR izq / der | A0 / A1 | config del simulador |
| LED estado / giro izq / giro der | A3 / A4 / A5 | .ino 6ta actividad |

⚠️ **Discrepancia**: el `.ino` de la 6ta actividad nombra *izquierdo* a 7/4 y *derecho* a 12/13; la configuración del simulador (“mismos pines que tu cableado real”) dice lo contrario. Se usó la del simulador. Si el robot esquiva hacia el lado equivocado, intercambiá `TRIG_I/ECHO_I` con `TRIG_D/ECHO_D` en `byakko.ino`.

## Supuestos (medir y ajustar al principio de `byakko.ino`)
| Dato | Valor supuesto | Cómo medirlo |
|---|---|---|
| ENA/ENB del L298N | jumpers puestos | el esquema no los cablea al Arduino |
| Diámetro de rueda | **65 mm (medido)** | — |
| Trocha (entre ruedas) | 130 mm | regla; o `G5 A360` y ajustar hasta que dé una vuelta exacta |
| PWM mínimo de arranque | 60 | subir PWM hasta que la rueda gire sola en el piso |
| Velocidad máx. | 400 mm/s | telemetría `VL/VR` con PWM 255 |
| Sentido de motores | sin invertir | si una rueda gira al revés, intercambiar sus pines A/B |
| IR blanco/negro | 100 / 900 | medido (dato provisto) |
| Disposición IR | ambos sensores a los lados de la línea (clásico) | — |

## Limitaciones conocidas
- **Resolución**: 20 ticks/vuelta (flanco RISING) ≈ 10 mm por tick con rueda de 65 mm. La precisión de distancia es del orden de ±10 mm y la de rotación de algunos grados.
- **Encoders de un canal**: no detectan sentido. El signo se toma de la consigna, igual que en la 6ta actividad. Empujar el robot a mano, o una inversión brusca, puede contar ticks con el signo equivocado.
- Los HC-SR04 se disparan de a uno (round-robin) para evitar interferencias cruzadas: cada sensor se actualiza cada ~75 ms y cada medición bloquea como máximo 12 ms.
- En el R4 WiFi, el Wi-Fi corre en un ESP32-S3 controlado por comandos AT: cada llamada de red demora milisegundos, por eso la red se consulta cada 10 ms y la telemetría va a 5 Hz.
- Siguelínea con 2 sensores: si la línea pasa por completo al otro lado de ambos sensores en una curva muy cerrada, el robot no puede saberlo (ambos ven blanco = recto). Se mitiga bajando `LINEV`.
