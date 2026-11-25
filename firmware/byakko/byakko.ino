#include <WiFiS3.h>

/*
  BYAKKO - Robot con Arduino UNO R4 WiFi
  Modos: manual (flechas, joystick y G-code), automatico (esquiva obstaculos)
  y siguelinea. Se controla con pedidos HTTP desde la app o el simulador.

  En el simulador no hay encoders: dejar "#define SIMULADOR".
  Para el robot real BORRAR esa linea.
*/
#define SIMULADOR

// ---------- Wi-Fi ----------
// Por defecto el robot crea su propia red (modo AP).
// Para unirlo a otra red: poner USAR_OTRA_RED en true y completar los datos.
const bool USAR_OTRA_RED = false;
char otra_red[] = "NombreDeLaRed";
char otra_pass[] = "ClaveDeLaRed";

char ssid[] = "Byakko-Robot";
char pass[] = "byakko2026";

// ---------- Pines ----------
const int IN1 = 5;    // motor izquierdo adelante
const int IN2 = 6;    // motor izquierdo atras
const int IN3 = 10;   // motor derecho adelante
const int IN4 = 11;   // motor derecho atras

const int ENCODER_DER = 2;
const int ENCODER_IZQ = 3;

const int TRIG_F = 9;
const int ECHO_F = 8;
const int TRIG_I = 12;
const int ECHO_I = 13;
const int TRIG_D = 7;
const int ECHO_D = 4;

const int IR_IZQ = A0;
const int IR_DER = A1;

const int LED_T = A3;
const int LED_IZQ = A4;
const int LED_DER = A5;

// ---------- Datos del robot (medir y ajustar) ----------
const float DIAMETRO_RUEDA = 65.0;                 // mm (medido)
const float RANURAS_ENCODER = 20.0;                // ranuras del disco
const float MM_POR_TICK = 3.14159 * DIAMETRO_RUEDA / RANURAS_ENCODER;  // ~10.2 mm por tick
const float ANCHO_ROBOT = 130.0;                   // mm entre ruedas

// ---------- Ajustes ----------
const int PWM_MINIMO = 60;      // PWM con el que la rueda empieza a girar
const float VEL_MAXIMA = 400;   // mm/s con PWM 255
const float KP = 0.4;
const float KI = 0.8;

// Tabla medida con el dashboard (barrido, ruedas en el aire): PWM -> RPM de cada motor
const int TABLA_N = 14;
const int TABLA_PWM[TABLA_N]     = { 60,  75,  90, 105, 120, 135, 150, 165, 180, 195, 210, 225, 240, 255};
const int TABLA_RPM_IZQ[TABLA_N] = { 86, 101, 121, 139, 155, 170, 189, 203, 223, 240, 251, 266, 274, 280};
const int TABLA_RPM_DER[TABLA_N] = { 99, 116, 134, 152, 169, 188, 207, 227, 246, 267, 287, 304, 315, 323};

const int IR_BLANCO = 100;      // lectura del IR sobre blanco
const int IR_NEGRO = 900;       // lectura del IR sobre negro

// Siguelinea (se pueden cambiar en vivo con /linea?kp=1.2&kd=0.08&vel=180&perdida=0.15)
float lin_kp = 1.2;          // giro por error de posicion
float lin_kd = 0.08;         // giro por velocidad del error (frena la oscilacion)
float lin_vel = 180;         // mm/s en recta
float lin_freno = 0.6;       // cuanto frena en las curvas (0 = nada, 1 = se detiene)
float lin_perdida = 0.15;    // por debajo de esto en los dos IR, la linea se perdio
float lin_error_anterior = 0;
int lin_ultimo_lado = 0;     // -1 = la linea se vio por ultima vez a la izquierda, +1 = derecha
float lin_pico = 0;          // lectura maxima del sensor de ese lado mientras veia la linea
bool lin_buscando = false;   // true = la linea se perdio y se esta girando para encontrarla
float lin_lleno = 0.8;       // si el sensor llego a esto, la linea paso de lleno por debajo
float lin_busca_giro = 1.0;  // giro mientras busca la linea (0..1)
float lin_busca_avance = 0.2; // avance mientras busca, en fraccion de lin_vel
unsigned long tLinea = 0;

// Evasion de obstaculos (se pueden cambiar en vivo desde el dashboard con /evasion?...)
float eva_inicio = 20;       // cm: a esta distancia empieza a esquivar suave
float eva_minimo = 5;        // cm: a esta distancia esquiva al maximo
float eva_k_frente = 1.0;    // cuanto gira por el obstaculo de adelante
float eva_k_lado = 0.7;      // cuanto lo empujan las paredes de los costados
float eva_vel = 200;         // mm/s cuando no hay nada cerca
float eva_giro = 220;        // mm/s de diferencia entre ruedas al girar al maximo
float eva_freno = 1.6;       // cuanto frena con el obstaculo de adelante (mas de 1 = retrocede)
float eva_retroceso = 100;   // mm/s maximo hacia atras
bool eva_en_seco = false;    // true = calcula todo pero no mueve los motores (para probar en la mesa)

// Lo que calculo la evasion la ultima vez (se muestra en el dashboard)
float eva_c_frente = 0;
float eva_c_izq = 0;
float eva_c_der = 0;
float eva_salida_giro = 0;
float eva_salida_avance = 0;

// ---------- Variables ----------
enum Modos {
  MANUAL,
  AUTOMATICO,
  SIGUELINEA
};
int modo = MANUAL;

int velocidad = 60;   // % de velocidad para todos los modos (la cambia /vel?v=)

// Encoders
volatile long ticks_der = 0;
volatile long ticks_izq = 0;
volatile int sentido_der = 1;
volatile int sentido_izq = 1;
long ticks_der_antes = 0;
long ticks_izq_antes = 0;
float ticks_falsos_der = 0;   // solo para el simulador
float ticks_falsos_izq = 0;

// Control de velocidad (mm/s)
float vel_deseada_izq = 0;
float vel_deseada_der = 0;
float vel_real_izq = 0;
float vel_real_der = 0;
float suma_error_izq = 0;
float suma_error_der = 0;
int pwm_izq = 0;
int pwm_der = 0;

// Sensores
long sensor_frontal = 200;
long sensor_izquierdo = 200;
long sensor_derecho = 200;
int sensor_a_leer = 0;
float linea_izq = 0;   // 0 = blanco, 1 = negro
float linea_der = 0;

// Manual
float joy_x = 0;   // -1 a 1
float joy_y = 0;

// Movimientos G-code
bool moviendo = false;
String movimiento_actual = "";
String movimientos_pendientes = "";
float meta_izq = 0;      // mm que tiene que hacer cada rueda (con signo)
float meta_der = 0;
long inicio_izq = 0;
long inicio_der = 0;
float vel_movimiento = 200;
unsigned long tiempo_inicio_mov = 0;
unsigned long tiempo_limite_mov = 0;
String ultimo_evento = "-";

// Tiempos
unsigned long tControl = 0;
unsigned long tPID = 0;
unsigned long tSensor = 0;
unsigned long tUltimoPedido = 0;
bool app_conectada = false;

// Pruebas desde el dashboard
bool prueba_motores = false;   // true = PWM directo, sin PID (/pwm?izq=..&der=..)
int pwm_prueba_izq = 0;
int pwm_prueba_der = 0;
// Como se calcula el PWM de cada rueda (/control?m=0, 1 o 2):
//   PWM_PURO: una recta entre PWM_MINIMO y 255, sin mirar los encoders
//   PID:      la recta + correccion con los encoders
//   TABLA:    el PWM sale de la tabla medida (RPM de cada motor), sin encoders
enum Controles {
  PWM_PURO,
  PID,
  TABLA
};
int control = PID;

// Cableado: si una rueda gira al reves o los encoders estan cruzados,
// se corrige aca (o probando con el dashboard: /config?inv_izq=1&inv_der=0&cruzar=1)
bool invertir_izq = false;     // true = el motor izquierdo gira al reves
bool invertir_der = false;
bool cruzar_encoders = false;  // true = el encoder del pin 2 es el de la rueda izquierda

WiFiServer server(80);

// ================= ENCODERS =================
// ticks_der cuenta el pin 2 y ticks_izq el pin 3
void tickDerecha() {
  if (cruzar_encoders) {
    ticks_der = ticks_der + sentido_izq;
  } else {
    ticks_der = ticks_der + sentido_der;
  }
}

void tickIzquierda() {
  if (cruzar_encoders) {
    ticks_izq = ticks_izq + sentido_der;
  } else {
    ticks_izq = ticks_izq + sentido_izq;
  }
}

long leerTicksDer() {
#ifdef SIMULADOR
  return round(ticks_falsos_der);
#else
  noInterrupts();
  long t = ticks_der;
  if (cruzar_encoders) {
    t = ticks_izq;
  }
  interrupts();
  return t;
#endif
}

long leerTicksIzq() {
#ifdef SIMULADOR
  return round(ticks_falsos_izq);
#else
  noInterrupts();
  long t = ticks_izq;
  if (cruzar_encoders) {
    t = ticks_der;
  }
  interrupts();
  return t;
#endif
}

// ================= MOTORES =================
void motor(int pwm, int pinA, int pinB) {
  if (pwm >= 0) {
    analogWrite(pinA, pwm);
    analogWrite(pinB, 0);
  } else {
    analogWrite(pinA, 0);
    analogWrite(pinB, -pwm);
  }
}

void detener() {
  vel_deseada_izq = 0;
  vel_deseada_der = 0;
  joy_x = 0;
  joy_y = 0;
  moviendo = false;
  movimientos_pendientes = "";
  prueba_motores = false;
  pwm_prueba_izq = 0;
  pwm_prueba_der = 0;
  suma_error_izq = 0;
  suma_error_der = 0;
  analogWrite(IN1, 0);
  analogWrite(IN2, 0);
  analogWrite(IN3, 0);
  analogWrite(IN4, 0);
}

// Aplica el PWM a los dos motores teniendo en cuenta si estan invertidos
void motores(int izq, int der) {
  if (invertir_izq) {
    izq = -izq;
  }
  if (invertir_der) {
    der = -der;
  }
  motor(izq, IN1, IN2);
  motor(der, IN3, IN4);
}

// Pide velocidad a cada rueda en mm/s (el PID se encarga del PWM)
void ruedas(float izq, float der) {
  vel_deseada_izq = constrain(izq, -VEL_MAXIMA, VEL_MAXIMA);
  vel_deseada_der = constrain(der, -VEL_MAXIMA, VEL_MAXIMA);
}

// avance y giro en mm/s. giro positivo = a la derecha. Aplica la velocidad elegida.
void mover(float avance, float giro) {
  avance = avance * velocidad / 100.0;
  giro = giro * velocidad / 100.0;
  ruedas(avance + giro, avance - giro);
}

// Busca en la tabla que PWM hace falta para esas RPM (interpolando entre filas)
int pwmDeTabla(float rpm, bool es_izquierda) {
  int i = 0;
  while (i < TABLA_N) {
    int rpm_fila = TABLA_RPM_DER[i];
    if (es_izquierda) {
      rpm_fila = TABLA_RPM_IZQ[i];
    }
    if (rpm <= rpm_fila) {
      if (i == 0) {
        // Por debajo de la primera fila: proporcional hasta 0
        return TABLA_PWM[0] * rpm / rpm_fila;
      }
      int rpm_antes = TABLA_RPM_DER[i - 1];
      if (es_izquierda) {
        rpm_antes = TABLA_RPM_IZQ[i - 1];
      }
      float fraccion = (rpm - rpm_antes) / (rpm_fila - rpm_antes + 0.0);
      return TABLA_PWM[i - 1] + fraccion * (TABLA_PWM[i] - TABLA_PWM[i - 1]);
    }
    i = i + 1;
  }
  return 255;  // pide mas de lo que da el motor
}

// Calcula el PWM de una rueda segun el modo de control elegido
int calcularPWM(float deseada, float error, float suma_error, bool es_izquierda) {
  if (abs(deseada) < 5) {
    return 0;
  }
  float base = 0;
  if (control == TABLA) {
    float rpm = abs(deseada) * 60.0 / (3.14159 * DIAMETRO_RUEDA);   // mm/s -> RPM de la rueda
    base = pwmDeTabla(rpm, es_izquierda);
  } else {
    base = PWM_MINIMO + (255 - PWM_MINIMO) * abs(deseada) / VEL_MAXIMA;
  }
  if (deseada < 0) {
    base = -base;
  }
  if (control != PID) {
    return constrain(base, -255, 255);
  }
  float pwm = base + KP * error + KI * suma_error;
  return constrain(pwm, -255, 255);
}

// Cada 100 ms: mide la velocidad con los encoders y corrige el PWM
void controlVelocidad() {
  float dt = 0.1;

#ifdef SIMULADOR
  ticks_falsos_izq = ticks_falsos_izq + vel_deseada_izq * dt / MM_POR_TICK;
  ticks_falsos_der = ticks_falsos_der + vel_deseada_der * dt / MM_POR_TICK;
#endif

  long ahora_izq = leerTicksIzq();
  long ahora_der = leerTicksDer();
  vel_real_izq = (ahora_izq - ticks_izq_antes) * MM_POR_TICK / dt;
  vel_real_der = (ahora_der - ticks_der_antes) * MM_POR_TICK / dt;
  ticks_izq_antes = ahora_izq;
  ticks_der_antes = ahora_der;

  float error_izq = vel_deseada_izq - vel_real_izq;
  float error_der = vel_deseada_der - vel_real_der;
  suma_error_izq = constrain(suma_error_izq + error_izq * dt, -150, 150);
  suma_error_der = constrain(suma_error_der + error_der * dt, -150, 150);
  if (abs(vel_deseada_izq) < 5) {
    suma_error_izq = 0;
  }
  if (abs(vel_deseada_der) < 5) {
    suma_error_der = 0;
  }

  // En siguelinea el PWM lo pone motoresSinPID() cada 10 ms; aca solo se mide
  if (modo == SIGUELINEA && !prueba_motores) {
    suma_error_izq = 0;
    suma_error_der = 0;
    return;
  }

  pwm_izq = calcularPWM(vel_deseada_izq, error_izq, suma_error_izq, true);
  pwm_der = calcularPWM(vel_deseada_der, error_der, suma_error_der, false);

  // Prueba de motores: PWM directo, sin PID
  if (prueba_motores) {
    pwm_izq = pwm_prueba_izq;
    pwm_der = pwm_prueba_der;
    if (pwm_izq > 0) {
      sentido_izq = 1;
    }
    if (pwm_izq < 0) {
      sentido_izq = -1;
    }
    if (pwm_der > 0) {
      sentido_der = 1;
    }
    if (pwm_der < 0) {
      sentido_der = -1;
    }
    motores(pwm_izq, pwm_der);
    return;
  }

  // El encoder no sabe para que lado gira: lo sacamos de lo que pedimos
  if (vel_deseada_izq > 0) {
    sentido_izq = 1;
  }
  if (vel_deseada_izq < 0) {
    sentido_izq = -1;
  }
  if (vel_deseada_der > 0) {
    sentido_der = 1;
  }
  if (vel_deseada_der < 0) {
    sentido_der = -1;
  }

  motores(pwm_izq, pwm_der);
}

// ================= SENSORES =================
long leerDistancia(int trigPin, int echoPin) {
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);

  long duracion = pulseIn(echoPin, HIGH, 8000);   // hasta ~1,3 m (bloquea menos)
  if (duracion == 0) {
    return 200;
  }
  long distancia = duracion * 0.034 / 2;
  return distancia;
}

// Lee un ultrasonido por vez para que no se mezclen los ecos
void leerUnSensor() {
  if (sensor_a_leer == 0) {
    sensor_frontal = leerDistancia(TRIG_F, ECHO_F);
  }
  if (sensor_a_leer == 1) {
    sensor_izquierdo = leerDistancia(TRIG_I, ECHO_I);
  }
  if (sensor_a_leer == 2) {
    sensor_derecho = leerDistancia(TRIG_D, ECHO_D);
  }
  sensor_a_leer = sensor_a_leer + 1;
  if (sensor_a_leer > 2) {
    sensor_a_leer = 0;
  }
}

// Devuelve 0 sobre blanco, 1 sobre negro y valores intermedios en el borde
float leerLinea(int pin) {
  float valor = (analogRead(pin) - IR_BLANCO) / (IR_NEGRO - IR_BLANCO + 0.0);
  return constrain(valor, 0.0, 1.0);
}

// ================= MODOS =================
// PWM directo (la parte "sin encoders" del control elegido), sin esperar al PID de 100 ms,
// que es lento para seguir la linea
void motoresSinPID() {
  pwm_izq = calcularPWM(vel_deseada_izq, 0, 0, true);
  pwm_der = calcularPWM(vel_deseada_der, 0, 0, false);
  if (vel_deseada_izq > 0) sentido_izq = 1;
  if (vel_deseada_izq < 0) sentido_izq = -1;
  if (vel_deseada_der > 0) sentido_der = 1;
  if (vel_deseada_der < 0) sentido_der = -1;
  motores(pwm_izq, pwm_der);
}

void siguelinea() {
  unsigned long ahora = millis();
  float dt = (ahora - tLinea) / 1000.0;
  if (tLinea == 0 || dt <= 0 || dt > 0.1) {
    dt = 0.01;   // primera vez o despues de una pausa larga
  }
  tLinea = ahora;

  linea_izq = leerLinea(IR_IZQ);
  linea_der = leerLinea(IR_DER);

  // Si el sensor derecho ve la linea, el error es positivo y gira a la derecha
  float error = linea_der - linea_izq;

  // Los IR van a los costados de la linea: "los dos en blanco" es lo normal en una recta.
  // La linea solo se perdio si antes paso DE LLENO por debajo de un sensor (se fue para afuera).
  // Si apenas lo rozo y volvio al centro, el pico quedo bajo y se sigue derecho.
  bool blanco = linea_izq < lin_perdida && linea_der < lin_perdida;
  if (!blanco) {
    lin_buscando = false;
    if (abs(error) > 0.2) {
      int lado = (error > 0) ? 1 : -1;
      float lectura = (lado > 0) ? linea_der : linea_izq;
      if (lado != lin_ultimo_lado) {
        lin_pico = 0;
      }
      lin_ultimo_lado = lado;
      lin_pico = max(lin_pico, lectura);
    }
  } else if (!lin_buscando) {
    lin_buscando = lin_pico >= lin_lleno && lin_ultimo_lado != 0;
    lin_pico = 0;
  }

  if (lin_buscando) {
    // Los dos ven blanco: en vez de seguir derecho, girar fuerte hacia donde estaba la linea
    lin_error_anterior = lin_ultimo_lado;
    mover(lin_vel * lin_busca_avance, lin_ultimo_lado * lin_busca_giro * lin_vel);
  } else {
    // PD: la derivada frena el giro antes de pasarse al otro lado
    float derivada = (error - lin_error_anterior) / dt;
    lin_error_anterior = error;
    float giro = constrain(lin_kp * error + lin_kd * derivada, -1.0, 1.0);
    // En las curvas va mas despacio
    float avance = lin_vel * (1 - lin_freno * abs(giro));
    mover(avance, giro * lin_vel);
  }
  motoresSinPID();
}

// 0 = lejos, 1 = pegado. Va creciendo de a poco, sin umbrales fijos
float cercania(float distancia) {
  float c = (eva_inicio - distancia) / (eva_inicio - eva_minimo);
  return constrain(c, 0.0, 1.0);
}

void automatico() {
  float frente = cercania(sensor_frontal);
  float izquierda = cercania(sensor_izquierdo);
  float derecha = cercania(sensor_derecho);

  // Para esquivar lo de adelante, gira hacia donde hay mas lugar
  int lado = 1;
  if (sensor_izquierdo > sensor_derecho) {
    lado = -1;
  }

  // Cuanto mas cerca, mas fuerte gira. Las paredes de los costados lo empujan al centro
  float giro = eva_k_frente * frente * lado + eva_k_lado * (izquierda - derecha);
  giro = constrain(giro, -1.0, 1.0);

  // Con el obstaculo cerca frena, y si esta muy cerca retrocede despacio
  float avance = eva_vel * (1 - eva_freno * frente);
  if (avance < -eva_retroceso) {
    avance = -eva_retroceso;
  }

  // Guardar para mostrarlo en el dashboard
  eva_c_frente = frente;
  eva_c_izq = izquierda;
  eva_c_der = derecha;
  eva_salida_giro = giro;
  eva_salida_avance = avance;

  if (eva_en_seco) {
    ruedas(0, 0);
  } else {
    mover(avance, giro * eva_giro);
  }
}

void manual() {
  if (moviendo) {
    actualizarMovimiento();
  } else {
    mover(joy_y * 300, joy_x * 200);
  }
}

void cambiarModo(int nuevo) {
  detener();
  modo = nuevo;
  lin_error_anterior = 0;
  lin_ultimo_lado = 0;
  lin_pico = 0;
  lin_buscando = false;
  tLinea = 0;
}

// ================= MOVIMIENTOS (G-code) =================
// Lee el numero que sigue a una letra. Ej: en "G1D300F200", la 'D' da 300
float leerValor(String cmd, char letra, float si_no_esta) {
  int pos = cmd.indexOf(letra, 2);
  if (pos == -1) {
    return si_no_esta;
  }
  int fin = pos + 1;
  while (fin < cmd.length()) {
    char c = cmd.charAt(fin);
    if ((c >= '0' && c <= '9') || c == '-' || c == '.') {
      fin = fin + 1;
    } else {
      break;
    }
  }
  return cmd.substring(pos + 1, fin).toFloat();
}

// Arranca un comando: G1 recta, G2 curva der, G3 curva izq, G5 rotar, G4 pausa
void empezarMovimiento(String cmd) {
  cmd.toUpperCase();
  float angulo = leerValor(cmd, 'A', 0) * 3.14159 / 180.0;
  float radio = abs(leerValor(cmd, 'R', 0));
  float mitad = ANCHO_ROBOT / 2;
  vel_movimiento = constrain(leerValor(cmd, 'F', 200), 20, VEL_MAXIMA);
  meta_izq = 0;
  meta_der = 0;

  if (cmd.startsWith("G1")) {
    meta_izq = leerValor(cmd, 'D', 0);
    meta_der = meta_izq;
  } else if (cmd.startsWith("G2")) {
    meta_izq = (radio + mitad) * abs(angulo);   // la rueda de afuera hace mas camino
    meta_der = (radio - mitad) * abs(angulo);
  } else if (cmd.startsWith("G3")) {
    meta_izq = (radio - mitad) * abs(angulo);
    meta_der = (radio + mitad) * abs(angulo);
  } else if (cmd.startsWith("G5")) {
    meta_izq = -mitad * angulo;                 // angulo positivo = a la izquierda
    meta_der = mitad * angulo;
  } else if (cmd.startsWith("G4")) {
    // pausa: no se mueve
  } else {
    ultimo_evento = "ERROR_" + cmd;
    return;
  }

  movimiento_actual = cmd;
  inicio_izq = leerTicksIzq();
  inicio_der = leerTicksDer();
  tiempo_inicio_mov = millis();
  if (cmd.startsWith("G4")) {
    tiempo_limite_mov = leerValor(cmd, 'P', 0);
  } else {
    float largo = max(abs(meta_izq), abs(meta_der));
    tiempo_limite_mov = 3000 * largo / vel_movimiento + 2000;   // por si se traba una rueda
  }
  moviendo = true;
}

// Saca el proximo comando de la lista "G1D300/G5A90/..."
void siguienteMovimiento() {
  if (movimientos_pendientes.length() == 0) {
    return;
  }
  String cmd = movimientos_pendientes;
  int barra = movimientos_pendientes.indexOf('/');
  if (barra != -1) {
    cmd = movimientos_pendientes.substring(0, barra);
    movimientos_pendientes = movimientos_pendientes.substring(barra + 1);
  } else {
    movimientos_pendientes = "";
  }
  empezarMovimiento(cmd);
}

void terminarMovimiento(String evento) {
  moviendo = false;
  ruedas(0, 0);
  ultimo_evento = evento + "_" + movimiento_actual.substring(0, 2);
  siguienteMovimiento();
}

void actualizarMovimiento() {
  unsigned long pasado = millis() - tiempo_inicio_mov;

  if (movimiento_actual.startsWith("G4")) {
    ruedas(0, 0);
    if (pasado >= tiempo_limite_mov) {
      terminarMovimiento("DONE");
    }
    return;
  }
  if (pasado > tiempo_limite_mov) {
    terminarMovimiento("TIMEOUT");
    return;
  }

  // Cuanto hizo cada rueda
  float hecho_izq = abs((leerTicksIzq() - inicio_izq) * MM_POR_TICK);
  float hecho_der = abs((leerTicksDer() - inicio_der) * MM_POR_TICK);
  bool listo_izq = hecho_izq >= abs(meta_izq) - MM_POR_TICK / 2;
  bool listo_der = hecho_der >= abs(meta_der) - MM_POR_TICK / 2;

  if (listo_izq && listo_der) {
    terminarMovimiento("DONE");
    return;
  }

  // Va frenando a medida que se acerca al final
  float falta = max(abs(meta_izq) - hecho_izq, abs(meta_der) - hecho_der);
  float v = constrain(50 + 2 * falta, 50, vel_movimiento);

  // Las dos ruedas terminan juntas: la que tiene menos camino va mas lenta
  float largo = max(abs(meta_izq), abs(meta_der));
  float v_izq = v * meta_izq / largo;
  float v_der = v * meta_der / largo;
  if (listo_izq) {
    v_izq = 0;
  }
  if (listo_der) {
    v_der = 0;
  }
  ruedas(v_izq, v_der);
}

// ================= COMUNICACION =================
// Arma toda la respuesta en un solo texto y la manda de una vez.
// En el R4 cada print es un comando al chip Wi-Fi: mandar todo junto es mucho mas rapido.
void responder(WiFiClient client, String mensaje) {
  String cuerpo = mensaje + "\r\n";
  String r = "HTTP/1.1 200 OK\r\n";
  r = r + "Content-Type: text/plain\r\n";
  r = r + "Access-Control-Allow-Origin: *\r\n";
  r = r + "Content-Length: " + String(cuerpo.length()) + "\r\n";
  r = r + "Connection: keep-alive\r\n";
  r = r + "\r\n";
  r = r + cuerpo;
  client.print(r);
}

// Lee un numero de la URL. Ej: en "/?X=50&Y=20", "Y=" da 20
float leerNumero(String request, String nombre, float si_no_esta) {
  int pos = request.indexOf(nombre);
  if (pos == -1) {
    return si_no_esta;
  }
  String valor = request.substring(pos + nombre.length());
  int fin = valor.indexOf('&');
  if (fin == -1) {
    fin = valor.indexOf(' ');
  }
  if (fin != -1) {
    valor = valor.substring(0, fin);
  }
  return valor.toFloat();
}

String nombreModo() {
  if (modo == AUTOMATICO) {
    return "AVOID";
  }
  if (modo == SIGUELINEA) {
    return "LINE";
  }
  return "MANUAL";
}

String estado() {
  String t = "TEL M=" + nombreModo();
  t = t + " F=" + String(sensor_frontal * 10);    // en mm
  t = t + " L=" + String(sensor_izquierdo * 10);
  t = t + " R=" + String(sensor_derecho * 10);
  t = t + " IL=" + String((int)(linea_izq * 1000));
  t = t + " IR=" + String((int)(linea_der * 1000));
  t = t + " VL=" + String((int)vel_real_izq);
  t = t + " VR=" + String((int)vel_real_der);
  t = t + " PL=" + String(pwm_izq);
  t = t + " PR=" + String(pwm_der);
  if (moviendo) {
    t = t + " Q=1";
  } else {
    t = t + " Q=0";
  }
  t = t + " S=" + String(velocidad);
  t = t + " DL=" + String((int)vel_deseada_izq);   // velocidad pedida
  t = t + " DR=" + String((int)vel_deseada_der);
  t = t + " TI=" + String(leerTicksIzq());          // ticks de los encoders
  t = t + " TD=" + String(leerTicksDer());
  if (control == PWM_PURO) {
    t = t + " CTRL=PWM";
  }
  if (control == PID) {
    t = t + " CTRL=PID";
  }
  if (control == TABLA) {
    t = t + " CTRL=TABLA";
  }
  t = t + " INVI=" + String(invertir_izq);
  t = t + " INVD=" + String(invertir_der);
  t = t + " CRZ=" + String(cruzar_encoders);
  t = t + " E=" + ultimo_evento;
  // Evasion: cercanias (0 a 100), giro (-100 a 100, + = derecha) y avance (mm/s)
  t = t + " CF=" + String((int)(eva_c_frente * 100));
  t = t + " CI=" + String((int)(eva_c_izq * 100));
  t = t + " CD=" + String((int)(eva_c_der * 100));
  t = t + " GI=" + String((int)(eva_salida_giro * 100));
  t = t + " AV=" + String((int)eva_salida_avance);
  t = t + " SECO=" + String(eva_en_seco);
  return t;
}

// Pedidos por Wi-Fi (app, dashboard o simulador)
void atenderCliente() {
  WiFiClient client = server.available();
  if (!client) {
    return;
  }
#ifndef SIMULADOR
  client.setTimeout(5);   // no trabar el loop esperando un pedido incompleto
#endif
  String request = client.readStringUntil('\n');
  if (!request.startsWith("GET")) {
    return;   // restos de un pedido anterior: se ignoran
  }
  // Leer el resto de los encabezados hasta la linea vacia
  while (client.available()) {
    String linea = client.readStringUntil('\n');
    if (linea.length() <= 1) {
      break;
    }
  }
  tUltimoPedido = millis();
  responder(client, procesarPedido(request));
  // No se cierra la conexion: la app y el dashboard la reusan para el proximo pedido
  // (abrir y cerrar una conexion en cada pedido es lo que mas tarda)
}

// Los mismos comandos por el cable USB (monitor serie). Ej: escribir  /pwm?izq=150&der=0
void atenderSerial() {
  if (Serial.available() == 0) {
    return;
  }
  String linea = Serial.readStringUntil('\n');
  linea.trim();
  if (linea.length() == 0) {
    return;
  }
  tUltimoPedido = millis();
  Serial.println(procesarPedido("GET " + linea + " HTTP/1.1"));
}

// Hace lo que pide la ruta y devuelve el texto de respuesta
String procesarPedido(String request) {
  if (request.indexOf("/estado") != -1) {
    app_conectada = true;
    return estado();
  }

  // Cualquier orden de movimiento termina la prueba de motores
  bool es_ajuste = request.indexOf("/vel?") != -1 || request.indexOf("/control?") != -1;
  es_ajuste = es_ajuste || request.indexOf("/config?") != -1 || request.indexOf("/ping") != -1;
  es_ajuste = es_ajuste || request.indexOf("/evasion") != -1 || request.indexOf("/linea?") != -1;
  if (request.indexOf("/pwm?") == -1 && !es_ajuste) {
    prueba_motores = false;
  }

  if (request.indexOf("/pwm?") != -1) {
    // Prueba: /pwm?izq=150&der=-150 (de -255 a 255)
    modo = MANUAL;
    moviendo = false;
    joy_x = 0;
    joy_y = 0;
    ruedas(0, 0);
    prueba_motores = true;
    pwm_prueba_izq = constrain(leerNumero(request, "izq=", 0), -255, 255);
    pwm_prueba_der = constrain(leerNumero(request, "der=", 0), -255, 255);
    return "pwm " + String(pwm_prueba_izq) + " " + String(pwm_prueba_der);
  }
  else if (request.indexOf("/config?") != -1) {
    // Ej: /config?inv_izq=1&inv_der=0&cruzar=1  (lo que no se manda no cambia)
    invertir_izq = leerNumero(request, "inv_izq=", invertir_izq) != 0;
    invertir_der = leerNumero(request, "inv_der=", invertir_der) != 0;
    cruzar_encoders = leerNumero(request, "cruzar=", cruzar_encoders) != 0;
    return "config inv_izq=" + String(invertir_izq) + " inv_der=" + String(invertir_der) + " cruzar=" + String(cruzar_encoders);
  }
  else if (request.indexOf("/ping") != -1) {
    return "pong";
  }
  else if (request.indexOf("/evasion") != -1) {
    // Ej: /evasion?inicio=25&minimo=6&kf=1.2&kl=0.5&vel=180&giro=200&freno=1.5&retro=80&seco=1
    // Lo que no se manda queda igual. Sin parametros solo devuelve los valores.
    eva_inicio = leerNumero(request, "inicio=", eva_inicio);
    eva_minimo = leerNumero(request, "minimo=", eva_minimo);
    if (eva_minimo >= eva_inicio) {
      eva_minimo = eva_inicio - 1;
    }
    eva_k_frente = leerNumero(request, "kf=", eva_k_frente);
    eva_k_lado = leerNumero(request, "kl=", eva_k_lado);
    eva_vel = leerNumero(request, "vel=", eva_vel);
    eva_giro = leerNumero(request, "giro=", eva_giro);
    eva_freno = leerNumero(request, "freno=", eva_freno);
    eva_retroceso = leerNumero(request, "retro=", eva_retroceso);
    eva_en_seco = leerNumero(request, "seco=", eva_en_seco) != 0;
    String r = "evasion inicio=" + String(eva_inicio) + " minimo=" + String(eva_minimo);
    r = r + " kf=" + String(eva_k_frente) + " kl=" + String(eva_k_lado);
    r = r + " vel=" + String(eva_vel) + " giro=" + String(eva_giro);
    r = r + " freno=" + String(eva_freno) + " retro=" + String(eva_retroceso);
    r = r + " seco=" + String(eva_en_seco);
    return r;
  }
  else if (request.indexOf("/linea") != -1 && request.indexOf("/siguelinea") == -1) {
    // Ej: /linea?kp=1.2&kd=0.08&vel=180&freno=0.6&perdida=0.15
    lin_kp = leerNumero(request, "kp=", lin_kp);
    lin_kd = leerNumero(request, "kd=", lin_kd);
    lin_vel = constrain(leerNumero(request, "vel=", lin_vel), 0, VEL_MAXIMA);
    lin_freno = constrain(leerNumero(request, "freno=", lin_freno), 0, 1);
    lin_perdida = constrain(leerNumero(request, "perdida=", lin_perdida), 0, 1);
    lin_lleno = leerNumero(request, "lleno=", lin_lleno);
    lin_busca_giro = constrain(leerNumero(request, "bgiro=", lin_busca_giro), 0, 1);
    lin_busca_avance = constrain(leerNumero(request, "bavance=", lin_busca_avance), 0, 1);
    String r = "linea kp=" + String(lin_kp) + " kd=" + String(lin_kd);
    r = r + " vel=" + String(lin_vel) + " freno=" + String(lin_freno);
    r = r + " perdida=" + String(lin_perdida) + " lleno=" + String(lin_lleno);
    r = r + " bgiro=" + String(lin_busca_giro) + " bavance=" + String(lin_busca_avance);
    return r;
  }
  else if (request.indexOf("/control?m=") != -1) {
    // 0 = PWM puro, 1 = PID, 2 = tabla
    control = constrain(leerNumero(request, "m=", PID), 0, 2);
    suma_error_izq = 0;
    suma_error_der = 0;
    return "control " + String(control);
  }
  else if (request.indexOf("/vel?v=") != -1) {
    velocidad = constrain(leerNumero(request, "v=", 60), 10, 100);
    return "velocidad " + String(velocidad);
  }
  else if (request.indexOf("/manual") != -1) {
    cambiarModo(MANUAL);
    return "modo manual";
  }
  else if (request.indexOf("/automatico") != -1) {
    cambiarModo(AUTOMATICO);
    return "modo automatico";
  }
  else if (request.indexOf("/siguelinea") != -1) {
    cambiarModo(SIGUELINEA);
    return "modo siguelinea";
  }
  else if (request.indexOf("/stop") != -1 || request.indexOf("/reposo") != -1) {
    cambiarModo(MANUAL);
    return "stop";
  }
  else if (request.indexOf("/g/") != -1) {
    // Lista de movimientos: /g/G1D300/G5A90
    cambiarModo(MANUAL);
    int desde = request.indexOf("/g/") + 3;
    int hasta = request.indexOf(' ', desde);
    String lista = request.substring(desde, hasta);
    movimientos_pendientes = lista;
    siguienteMovimiento();
    return "movimientos: " + lista;
  }
  else if (request.indexOf("/?X=") != -1) {
    // Joystick: X e Y van de -127 a 127
    modo = MANUAL;
    moviendo = false;
    joy_x = constrain(leerNumero(request, "X=", 0) / 127.0, -1.0, 1.0);
    joy_y = constrain(leerNumero(request, "Y=", 0) / 127.0, -1.0, 1.0);
    return "joystick";
  }
  else if (request.indexOf("/adelante") != -1) {
    modo = MANUAL;
    moviendo = false;
    joy_x = 0;
    joy_y = 1;
    return "adelante";
  }
  else if (request.indexOf("/atras") != -1) {
    modo = MANUAL;
    moviendo = false;
    joy_x = 0;
    joy_y = -1;
    return "atras";
  }
  else if (request.indexOf("/izquierda") != -1) {
    modo = MANUAL;
    moviendo = false;
    joy_x = -1;
    joy_y = 0;
    return "izquierda";
  }
  else if (request.indexOf("/derecha") != -1) {
    modo = MANUAL;
    moviendo = false;
    joy_x = 1;
    joy_y = 0;
    return "derecha";
  }
  else {
    return "comando no reconocido";
  }

}

void conectarWiFi() {
  if (USAR_OTRA_RED) {
    Serial.print("Conectando a ");
    Serial.println(otra_red);
    int estadoWiFi = WiFi.begin(otra_red, otra_pass);
    if (estadoWiFi == WL_CONNECTED) {
      delay(2000);
      Serial.print("IP del Arduino: ");
      Serial.println(WiFi.localIP());
      return;
    }
    Serial.println("No se pudo conectar, se crea la red propia");
  }

  Serial.print("Creando Access Point... ");
  WiFi.config(IPAddress(192, 168, 4, 1));
  int estadoAP = WiFi.beginAP(ssid, pass);
  if (estadoAP != WL_AP_LISTENING) {
    Serial.println("ERROR al crear Access Point");
    while (true);
  }
  delay(2000);
  Serial.println("OK");
  Serial.print("Nombre WiFi: ");
  Serial.println(ssid);
  Serial.print("IP del Arduino: ");
  Serial.println(WiFi.localIP());
}

void luces() {
  if (app_conectada) {
    digitalWrite(LED_T, HIGH);
  } else {
    digitalWrite(LED_T, LOW);
  }
  if (vel_deseada_izq > vel_deseada_der + 30) {
    digitalWrite(LED_DER, HIGH);
  } else {
    digitalWrite(LED_DER, LOW);
  }
  if (vel_deseada_der > vel_deseada_izq + 30) {
    digitalWrite(LED_IZQ, HIGH);
  } else {
    digitalWrite(LED_IZQ, LOW);
  }
}

// ================= SETUP Y LOOP =================
void setup() {
  Serial.begin(9600);
  delay(1000);

  pinMode(IN1, OUTPUT);
  pinMode(IN2, OUTPUT);
  pinMode(IN3, OUTPUT);
  pinMode(IN4, OUTPUT);

  pinMode(TRIG_F, OUTPUT);
  pinMode(ECHO_F, INPUT);
  pinMode(TRIG_I, OUTPUT);
  pinMode(ECHO_I, INPUT);
  pinMode(TRIG_D, OUTPUT);
  pinMode(ECHO_D, INPUT);

  pinMode(LED_T, OUTPUT);
  pinMode(LED_IZQ, OUTPUT);
  pinMode(LED_DER, OUTPUT);

  pinMode(ENCODER_DER, INPUT);
  pinMode(ENCODER_IZQ, INPUT);
#ifndef SIMULADOR
  attachInterrupt(digitalPinToInterrupt(ENCODER_DER), tickDerecha, RISING);
  attachInterrupt(digitalPinToInterrupt(ENCODER_IZQ), tickIzquierda, RISING);
#endif

  detener();
  conectarWiFi();
  server.begin();
  Serial.println("Servidor web iniciado");
}

void loop() {
  atenderCliente();
  atenderSerial();

  // Si la app se desconecta, el robot se frena
  if (app_conectada && millis() - tUltimoPedido > 2000) {
    app_conectada = false;
    cambiarModo(MANUAL);
  }

  // Ultrasonidos: uno cada 30 ms (en siguelinea no se usan y pulseIn bloquea hasta 8 ms)
  if (modo != SIGUELINEA && millis() - tSensor >= 30) {
    tSensor = millis();
    leerUnSensor();
  }

  // Siguelinea: cada 10 ms (a alta velocidad cada ms cuenta)
  if (modo == SIGUELINEA && millis() - tLinea >= 10) {
    siguelinea();
  }

  // Modos: cada 20 ms
  if (millis() - tControl >= 20) {
    tControl = millis();
    if (modo == AUTOMATICO) {
      automatico();
    } else if (modo == SIGUELINEA) {
      // se corre arriba, cada 10 ms
    } else {Su
      manual();
    }
    luces();
  }

  // PID de las ruedas: cada 100 ms
  if (millis() - tPID >= 100) {
    tPID = millis();
    controlVelocidad();
  }
}
