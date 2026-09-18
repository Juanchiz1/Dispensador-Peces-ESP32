/*
  Dispensador automático de alimento para peces - ESP32
  ------------------------------------------------------
  Actuadores: servo SG90 (tornillo dosificador) + motor vibrador (anti-atasco)
  Sensores:   DHT22 (humedad/temperatura del alimento) + VL53L0X (nivel de tolva)
  RTC:        DS3231 (horarios de alimentación)
  Panel de control: servidor web embebido, servido por el propio ESP32

  Antes de subir: revisa GUIA_ARMADO_DISPENSADOR.md, sección 5, para instalar
  las librerías necesarias, y ajusta las credenciales de WiFi más abajo.
*/

#include <WiFi.h>
#include <WebServer.h>
#include <Wire.h>
#include <RTClib.h>
#include <DHT.h>
#include <Adafruit_VL53L0X.h>
#include <ESP32Servo.h>
#include <ArduinoJson.h>
#include <Preferences.h>

// ---------------- CONFIGURACIÓN DE RED ----------------
const char* WIFI_SSID     = "TECNO40";
const char* WIFI_PASSWORD = "Julio4080";

// ---------------- PINES ----------------
#define PIN_DHT22          4
#define PIN_SERVO          13
#define PIN_MOTOR_VIBRADOR 27
#define PIN_SDA            21
#define PIN_SCL            22
#define DHT_TYPE           DHT22

// ---------------- UMBRALES (calibrar con el hardware real) ----------------
float    UMBRAL_HUMEDAD_PORCENTAJE   = 65.0;
uint16_t DISTANCIA_TOLVA_LLENA_MM    = 30;
uint16_t DISTANCIA_TOLVA_VACIA_MM    = 140;

// ---------------- PARÁMETROS DEL ACTUADOR (tornillo corto, ver guía punto 0.3) ----------------
const int SERVO_ANGULO_REPOSO   = 0;
const int SERVO_ANGULO_DISPENSA = 150;
const int CICLOS_POR_PORCION    = 3;    // vaivenes del servo por cada porción
const int MS_VIBRADOR_POR_PORCION = 600;

#define MAX_HORARIOS 10

struct HorarioAlimentacion {
  uint8_t hora;
  uint8_t minuto;
  uint8_t porciones;
  bool disparadoHoy;
};

HorarioAlimentacion horarios[MAX_HORARIOS];
int numHorarios = 0;

DHT dht(PIN_DHT22, DHT_TYPE);
RTC_DS3231 rtc;
Adafruit_VL53L0X lox = Adafruit_VL53L0X();
Servo servoTornillo;
WebServer server(80);
Preferences prefs;

float humedadActual = 0;
float temperaturaActual = 0;
uint16_t distanciaActualMM = 0;
int nivelTolvaPorcentaje = 0;
bool tolvaVaciaAlerta = false;
bool humedadAltaAlerta = false;
String ultimaAlimentacion = "Nunca";
unsigned long ultimoDHT = 0;
int lastMinuteChecked = -1;

const char PAGINA_HTML[] PROGMEM = R"HTMLPAGE(
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Dispensador de alimento para peces</title>
<style>
  body { font-family: Arial, sans-serif; background:#0f1a24; color:#e6ecf1; margin:0; padding:16px; }
  h1 { font-size:20px; margin:0 0 16px; }
  .card { background:#17232f; border-radius:12px; padding:16px; margin-bottom:16px; }
  .grid { display:grid; grid-template-columns:1fr 1fr; gap:12px; }
  .stat { background:#1f2f3d; border-radius:8px; padding:12px; }
  .stat .valor { font-size:22px; font-weight:bold; }
  .stat .etiqueta { font-size:12px; color:#9db1c2; }
  .alerta { color:#ff9d6a; font-weight:bold; }
  button { background:#2f8f6f; color:white; border:none; border-radius:8px; padding:10px 16px; font-size:14px; cursor:pointer; }
  button.secundario { background:#3a4a58; }
  input { background:#1f2f3d; border:1px solid #33465a; color:#e6ecf1; border-radius:6px; padding:6px; }
  .fila-horario { display:flex; gap:8px; align-items:center; margin-bottom:8px; }
  .fila-horario input[type=time] { flex:1; }
  .fila-horario input[type=number] { width:60px; }
</style>
</head>
<body>
<h1>Dispensador de alimento para peces</h1>

<div class="card">
  <div class="grid" id="estado">
    <div class="stat"><div class="valor" id="v-humedad">--</div><div class="etiqueta">Humedad del alimento (%)</div></div>
    <div class="stat"><div class="valor" id="v-temp">--</div><div class="etiqueta">Temperatura (°C)</div></div>
    <div class="stat"><div class="valor" id="v-nivel">--</div><div class="etiqueta">Nivel de la tolva (%)</div></div>
    <div class="stat"><div class="valor" id="v-hora">--</div><div class="etiqueta">Hora del sistema</div></div>
  </div>
  <p id="alertas"></p>
  <p>Última alimentación: <span id="v-ultima">--</span></p>
</div>

<div class="card">
  <p>Alimentar ahora:</p>
  <input type="number" id="porcionesManual" value="1" min="1" max="10" style="width:60px">
  <button onclick="alimentarAhora()">Alimentar ahora</button>
</div>

<div class="card">
  <p>Horarios de alimentación</p>
  <div id="listaHorarios"></div>
  <button class="secundario" onclick="agregarFila()">Agregar horario</button>
  <button onclick="guardarHorarios()">Guardar horarios</button>
</div>

<script>
async function actualizarEstado(){
  try {
    const r = await fetch('/api/status');
    const d = await r.json();
    document.getElementById('v-humedad').textContent = d.humedad.toFixed(1);
    document.getElementById('v-temp').textContent = d.temperatura.toFixed(1);
    document.getElementById('v-nivel').textContent = d.nivel_tolva_pct;
    document.getElementById('v-hora').textContent = d.hora_actual;
    document.getElementById('v-ultima').textContent = d.ultima_alimentacion;
    let alertas = [];
    if (d.tolva_vacia) alertas.push('Tolva casi vacía');
    if (d.humedad_alta) alertas.push('Humedad alta en el alimento');
    document.getElementById('alertas').innerHTML = alertas.length
      ? '<span class="alerta">' + alertas.join(' · ') + '</span>' : '';
  } catch(e) { console.log('error de estado', e); }
}

async function alimentarAhora(){
  const porciones = document.getElementById('porcionesManual').value || 1;
  await fetch('/api/feed?porciones=' + porciones, { method: 'POST' });
  setTimeout(actualizarEstado, 500);
}

function filaHorarioHTML(hora, minuto, porciones){
  const hh = String(hora).padStart(2,'0');
  const mm = String(minuto).padStart(2,'0');
  return '<div class="fila-horario">' +
    '<input type="time" value="' + hh + ':' + mm + '">' +
    '<input type="number" value="' + porciones + '" min="1" max="10">' +
    '<button class="secundario" onclick="this.parentElement.remove()">Quitar</button></div>';
}

function agregarFila(){
  document.getElementById('listaHorarios').insertAdjacentHTML('beforeend', filaHorarioHTML(8,0,1));
}

async function cargarHorarios(){
  const r = await fetch('/api/schedule');
  const lista = await r.json();
  const cont = document.getElementById('listaHorarios');
  cont.innerHTML = '';
  lista.forEach(h => cont.insertAdjacentHTML('beforeend', filaHorarioHTML(h.hora, h.minuto, h.porciones)));
}

async function guardarHorarios(){
  const filas = document.querySelectorAll('#listaHorarios .fila-horario');
  const datos = Array.from(filas).map(f => {
    const inputs = f.querySelectorAll('input');
    const [hh, mm] = inputs[0].value.split(':');
    return { hora: parseInt(hh), minuto: parseInt(mm), porciones: parseInt(inputs[1].value) };
  });
  await fetch('/api/schedule', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(datos)
  });
  alert('Horarios guardados');
}

cargarHorarios();
actualizarEstado();
setInterval(actualizarEstado, 4000);
</script>
</body>
</html>
)HTMLPAGE";

void conectarWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Conectando a WiFi");
  int intentos = 0;
  while (WiFi.status() != WL_CONNECTED && intentos < 40) {
    delay(500);
    Serial.print(".");
    intentos++;
  }
  Serial.println();
  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("Conectado. Panel de control: http://");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("No se pudo conectar. Revisa WIFI_SSID y WIFI_PASSWORD.");
  }
}

void cargarHorarios() {
  prefs.begin("feeder", true);
  String json = prefs.getString("horarios", "");
  prefs.end();

  numHorarios = 0;
  if (json.length() > 0) {
    StaticJsonDocument<1024> doc;
    if (deserializeJson(doc, json) == DeserializationError::Ok) {
      for (JsonObject o : doc.as<JsonArray>()) {
        if (numHorarios >= MAX_HORARIOS) break;
        horarios[numHorarios].hora = o["hora"];
        horarios[numHorarios].minuto = o["minuto"];
        horarios[numHorarios].porciones = o["porciones"] | 1;
        horarios[numHorarios].disparadoHoy = false;
        numHorarios++;
      }
    }
  }

  if (numHorarios == 0) {
    horarios[0] = {8, 0, 1, false};
    horarios[1] = {13, 0, 1, false};
    horarios[2] = {18, 0, 1, false};
    numHorarios = 3;
  }
}

void guardarHorarios() {
  StaticJsonDocument<1024> doc;
  JsonArray arr = doc.to<JsonArray>();
  for (int i = 0; i < numHorarios; i++) {
    JsonObject o = arr.createNestedObject();
    o["hora"] = horarios[i].hora;
    o["minuto"] = horarios[i].minuto;
    o["porciones"] = horarios[i].porciones;
  }
  String salida;
  serializeJson(doc, salida);
  prefs.begin("feeder", false);
  prefs.putString("horarios", salida);
  prefs.end();
}

void leerSensores() {
  if (millis() - ultimoDHT > 2500) {
    ultimoDHT = millis();
    float h = dht.readHumidity();
    float t = dht.readTemperature();
    if (!isnan(h)) humedadActual = h;
    if (!isnan(t)) temperaturaActual = t;
    humedadAltaAlerta = (humedadActual >= UMBRAL_HUMEDAD_PORCENTAJE);
  }

  VL53L0X_RangingMeasurementData_t medida;
  lox.rangingTest(&medida, false);
  if (medida.RangeStatus != 4) {
    distanciaActualMM = medida.RangeMilliMeter;
    long rango = (long)DISTANCIA_TOLVA_VACIA_MM - (long)DISTANCIA_TOLVA_LLENA_MM;
    long posicion = (long)DISTANCIA_TOLVA_VACIA_MM - (long)distanciaActualMM;
    if (rango <= 0) rango = 1;
    nivelTolvaPorcentaje = constrain((int)((posicion * 100L) / rango), 0, 100);
    tolvaVaciaAlerta = (distanciaActualMM >= DISTANCIA_TOLVA_VACIA_MM);
  }
}

void alimentar(int porciones) {
  Serial.printf("Alimentando: %d porcion(es)\n", porciones);
  for (int p = 0; p < porciones; p++) {
    for (int c = 0; c < CICLOS_POR_PORCION; c++) {
      servoTornillo.write(SERVO_ANGULO_DISPENSA);
      delay(300);
      servoTornillo.write(SERVO_ANGULO_REPOSO);
      delay(300);
    }
    digitalWrite(PIN_MOTOR_VIBRADOR, HIGH);
    delay(MS_VIBRADOR_POR_PORCION);
    digitalWrite(PIN_MOTOR_VIBRADOR, LOW);
    delay(200);
  }
  DateTime ahora = rtc.now();
  char buf[20];
  sprintf(buf, "%02d/%02d %02d:%02d", ahora.day(), ahora.month(), ahora.hour(), ahora.minute());
  ultimaAlimentacion = String(buf);
}

void revisarHorarios() {
  DateTime ahora = rtc.now();
  if (ahora.minute() == lastMinuteChecked) return;
  lastMinuteChecked = ahora.minute();

  if (ahora.hour() == 0 && ahora.minute() == 0) {
    for (int i = 0; i < numHorarios; i++) horarios[i].disparadoHoy = false;
  }

  for (int i = 0; i < numHorarios; i++) {
    if (!horarios[i].disparadoHoy && horarios[i].hora == ahora.hour() && horarios[i].minuto == ahora.minute()) {
      if (tolvaVaciaAlerta) {
        Serial.println("Alimentación programada omitida: tolva vacía");
      } else {
        alimentar(horarios[i].porciones);
      }
      horarios[i].disparadoHoy = true;
    }
  }
}

void handleRoot() {
  server.send(200, "text/html", PAGINA_HTML);
}

void handleStatus() {
  StaticJsonDocument<512> doc;
  doc["humedad"] = humedadActual;
  doc["temperatura"] = temperaturaActual;
  doc["distancia_mm"] = distanciaActualMM;
  doc["nivel_tolva_pct"] = nivelTolvaPorcentaje;
  doc["tolva_vacia"] = tolvaVaciaAlerta;
  doc["humedad_alta"] = humedadAltaAlerta;
  doc["ultima_alimentacion"] = ultimaAlimentacion;
  DateTime ahora = rtc.now();
  char buf[6];
  sprintf(buf, "%02d:%02d", ahora.hour(), ahora.minute());
  doc["hora_actual"] = buf;
  doc["wifi_rssi"] = WiFi.RSSI();
  String salida;
  serializeJson(doc, salida);
  server.send(200, "application/json", salida);
}

void handleFeedNow() {
  int porciones = 1;
  if (server.hasArg("porciones")) porciones = server.arg("porciones").toInt();
  if (porciones < 1) porciones = 1;
  alimentar(porciones);
  server.send(200, "application/json", "{\"ok\":true}");
}

void handleGetSchedule() {
  StaticJsonDocument<1024> doc;
  JsonArray arr = doc.to<JsonArray>();
  for (int i = 0; i < numHorarios; i++) {
    JsonObject o = arr.createNestedObject();
    o["hora"] = horarios[i].hora;
    o["minuto"] = horarios[i].minuto;
    o["porciones"] = horarios[i].porciones;
  }
  String salida;
  serializeJson(doc, salida);
  server.send(200, "application/json", salida);
}

void handleSetSchedule() {
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"error\":\"sin datos\"}");
    return;
  }
  StaticJsonDocument<1024> doc;
  if (deserializeJson(doc, server.arg("plain")) != DeserializationError::Ok) {
    server.send(400, "application/json", "{\"error\":\"json invalido\"}");
    return;
  }
  numHorarios = 0;
  for (JsonObject o : doc.as<JsonArray>()) {
    if (numHorarios >= MAX_HORARIOS) break;
    horarios[numHorarios].hora = o["hora"];
    horarios[numHorarios].minuto = o["minuto"];
    horarios[numHorarios].porciones = o["porciones"] | 1;
    horarios[numHorarios].disparadoHoy = false;
    numHorarios++;
  }
  guardarHorarios();
  server.send(200, "application/json", "{\"ok\":true}");
}

void setupServidorWeb() {
  server.on("/", HTTP_GET, handleRoot);
  server.on("/api/status", HTTP_GET, handleStatus);
  server.on("/api/feed", HTTP_POST, handleFeedNow);
  server.on("/api/schedule", HTTP_GET, handleGetSchedule);
  server.on("/api/schedule", HTTP_POST, handleSetSchedule);
  server.begin();
}

void setup() {
  Serial.begin(115200);

  pinMode(PIN_MOTOR_VIBRADOR, OUTPUT);
  digitalWrite(PIN_MOTOR_VIBRADOR, LOW);

  ESP32PWM::allocateTimer(0);
  servoTornillo.setPeriodHertz(50);
  servoTornillo.attach(PIN_SERVO, 500, 2400);
  servoTornillo.write(SERVO_ANGULO_REPOSO);

  Wire.begin(PIN_SDA, PIN_SCL);
  dht.begin();

  if (!rtc.begin()) {
    Serial.println("ERROR: no se detectó el DS3231. Revisa el cableado I2C.");
  } else if (rtc.lostPower()) {
    rtc.adjust(DateTime(F(__DATE__), F(__TIME__)));
  }

  if (!lox.begin()) {
    Serial.println("ERROR: no se detectó el VL53L0X. Revisa el cableado I2C.");
  }

  cargarHorarios();
  conectarWiFi();
  setupServidorWeb();

  Serial.println("Sistema listo.");
}

void loop() {
  server.handleClient();
  leerSensores();
  revisarHorarios();
}
