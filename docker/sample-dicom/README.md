# Radiografías de prueba

- `sinteticos/`: 4 imágenes DICOM **sintéticas** generadas con `tools/generar_dicom_sinteticos.py`
  (tórax PA y lateral, mano AP, rodilla AP). No son estudios clínicos. Sirven para probar carga, zoom, paneo y contraste.
- Para pruebas más realistas, descargar radiografías **anonimizadas** de una fuente pública y **revisar su licencia**.
  Colocarlas en esta carpeta (los `.dcm` fuera de `sinteticos/` están ignorados por Git).
- **Nunca** subir al repositorio estudios de pacientes reales.
