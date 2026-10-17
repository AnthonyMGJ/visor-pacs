// HU06 / CA06.3 / RNF-02 / indicador I2: "serie de 180 cortes -> primera imagen en <= 3 s".
// Mide el recorrido del visor: lista de series -> lista de instancias -> primer corte (frame 1).
//
// Antes: python tools/generar_serie_tc.py y subir la carpeta docker/sample-dicom/serie-180 a Orthanc.
// Uso:
//   k6 run -e USER_NAME=medico.demo -e USER_PASS=... -e STUDY_UID=<StudyInstanceUID> pruebas/carga/primera-imagen.js
// Variables: BASE_URL (http://localhost:8080), VUS (1), ITERACIONES (20).
// Para medir con "red de 10 Mbps" (condición del criterio), limita el ancho de banda con una herramienta de red
// del navegador/sistema operativo y anótalo en el informe: k6 mide desde la máquina que lo ejecuta.
import http from 'k6/http';
import { check, fail } from 'k6';
import { Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const ESTUDIO = __ENV.STUDY_UID;
const primeraImagen = new Trend('tiempo_primera_imagen', true);

export const options = {
  vus: parseInt(__ENV.VUS || '1'),
  iterations: parseInt(__ENV.ITERACIONES || '20'),
  thresholds: {
    tiempo_primera_imagen: ['p(95)<3000'], // RNF-02
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  if (!ESTUDIO) fail('Falta -e STUDY_UID=<StudyInstanceUID de la serie de 180 cortes>');
  const res = http.post(`${BASE}/api/auth/login`,
    JSON.stringify({ username: __ENV.USER_NAME, password: __ENV.USER_PASS }),
    { headers: { 'Content-Type': 'application/json' } });
  if (res.status !== 200) fail(`Login fallo en setup: ${res.status}`);
  return { token: res.json('accessToken') };
}

const valor = (obj, tag) => (obj[tag] && obj[tag].Value ? obj[tag].Value[0] : undefined);

export default function (data) {
  const h = { Authorization: `Bearer ${data.token}`, Accept: 'application/dicom+json' };
  const inicio = Date.now();

  const series = http.get(`${BASE}/api/dicomweb/studies/${ESTUDIO}/series`, { headers: h });
  if (!check(series, { 'series 200': (r) => r.status === 200 })) return;
  const serieUid = valor(series.json()[0], '0020000E');

  const instancias = http.get(`${BASE}/api/dicomweb/studies/${ESTUDIO}/series/${serieUid}/instances`, { headers: h });
  if (!check(instancias, { 'instancias 200': (r) => r.status === 200 })) return;
  const lista = instancias.json().sort((a, b) => (valor(a, '00200013') || 0) - (valor(b, '00200013') || 0));
  const sopUid = valor(lista[0], '00080018');

  const frame = http.get(
    `${BASE}/api/dicomweb/studies/${ESTUDIO}/series/${serieUid}/instances/${sopUid}/frames/1`,
    { headers: { Authorization: `Bearer ${data.token}`, Accept: 'multipart/related; type="application/octet-stream"' } });
  check(frame, { 'primer corte 200': (r) => r.status === 200, 'trae píxeles': (r) => r.body && r.body.byteLength > 0 });

  primeraImagen.add(Date.now() - inicio);
}
