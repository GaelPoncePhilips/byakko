# Protocolo Byakko (App ⇄ Robot)

Es **HTTP**, igual que el simulador AutoUnificado: cada orden es un `GET` a una ruta.
Así el mismo sketch funciona en el simulador y en el robot, y se puede probar con el
navegador o con `curl`.

| Ítem | Valor |
|---|---|
| Red (por defecto) | El robot crea su red (modo AP): SSID `Byakko-Robot`, clave `byakko2026`, IP `192.168.4.1` |
| Otra red | Con `USAR_OTRA_RED = true` (y `otra_red` / `otra_pass`) se une a esa red. La IP se ve en el monitor serie y la app la puede **buscar**. Si falla, crea su red igual. |
| Puerto | 80 |
| Respuesta | `200 OK`, texto plano |

## Modos
| Ruta | Efecto | Respuesta |
|---|---|---|
| `/siguelinea` | Modo siguelínea | `modo siguelinea` |
| `/automatico` | Modo evasión de obstáculos | `modo automatico` |
| `/manual` | Modo manual | `modo manual` |
| `/stop` o `/reposo` | Frena todo, cancela los G-code y vuelve a manual | `stop` |
| `/vel?v=10..100` | Velocidad en % para **todos** los modos (línea, obstáculos, manual). Por defecto 60. Los G-code usan su propio `F`. | `velocidad 60` |

## Control manual (pasan solos a modo manual)
| Ruta | Efecto |
|---|---|
| `/adelante?vel=0..255` `/atras` `/izquierda` `/derecha` | Flechas (igual que el panel del simulador). Sigue hasta `/stop`. |
| `/?X=-127..127&Y=-127..127` | Joystick. `X` + = derecha, `Y` + = adelante. |

## Movimientos con encoders (tipo G-code)
`/g/<cmd>/<cmd>/...` — uno o varios comandos sin espacios. Se encolan (máx. 8) y se
ejecutan de a uno: cada rueda recorre su distancia medida con el encoder, las dos terminan juntas y frenan al acercarse al final.

| Comando | Movimiento |
|---|---|
| `G1D<mm>F<mm/s>` | Recta. `D` negativo = marcha atrás. |
| `G2R<mm>A<grados>F..` | Curva a la **derecha** de radio R (medido al centro del robot). |
| `G3R<mm>A<grados>F..` | Curva a la **izquierda**. |
| `G5A<grados>F..` | Rotar sobre su eje. `A` + = izquierda, − = derecha. |
| `G4P<ms>` | Pausa. |
| `M0` | Cancela todo. |

`F` es opcional (por defecto 200 mm/s). Ejemplo, un cuadrado de 30 cm:
```
/g/G1D300/G5A90/G1D300/G5A90/G1D300/G5A90/G1D300/G5A90
```
Respuesta: `movimientos: <lista>`. Un comando desconocido deja `E=ERROR_<cmd>` en el estado.

## Estado / telemetría
`/estado` responde:
```
TEL M=MANUAL F=234 L=1200 R=2000 IL=50 IR=920 VL=120 VR=118 PL=140 PR=135 Q=0 S=60 E=DONE_G1
```
| Clave | Significado |
|---|---|
| `M` | modo (`LINE` `AVOID` `MANUAL`) |
| `F` `L` `R` | ultrasonido frontal / izq / der en **mm** (2000 = nada en rango) |
| `IL` `IR` | IR: 0 = blanco … 1000 = negro |
| `VL` `VR` | velocidad medida de cada rueda, mm/s |
| `PL` `PR` | PWM aplicado (−255…255) |
| `Q` | 1 si está haciendo un movimiento, 0 si no |
| `S` | velocidad elegida con `/vel`, en % |
| `E` | último evento: `DONE_G1`, `TIMEOUT_G5`, `ERROR_<cmd>` |

## Seguridad
La app pide `/estado` cada 400 ms. Una vez que llegó el primer `/estado`, si el robot
pasa **2 s** sin recibir ningún pedido, frena. El panel del simulador
no usa `/estado`, así que con él esta protección no se activa.

## Nota sobre el simulador
El backend del simulador guarda sólo el **último** pedido, y el simulador lo lee cada 100 ms.
Por eso los G-code de varios pasos van en un solo pedido (`/g/.../...`), y la app no pide
`/estado` justo después de mandar una orden.
