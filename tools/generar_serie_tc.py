"""
Genera una serie de TC SINTÉTICA (por defecto 180 cortes de 512x512) para medir el rendimiento del visor:
HU06/CA06.3 (primera imagen en <= 3 s con 180 cortes), HU03/CA03 (scroll estable) y la línea base I2/I3.
No es un estudio clínico ni contiene datos de pacientes reales.

Uso:
    pip install pydicom numpy
    python tools/generar_serie_tc.py                 # 180 cortes 512x512 -> docker/sample-dicom/serie-180/
    python tools/generar_serie_tc.py --cortes 60 --tam 256

La carpeta de salida queda fuera de Git (la regla *.dcm del .gitignore solo admite "sinteticos/").
Súbela con:  ./docker/subir-radiografias.sh docker/sample-dicom/serie-180   (o el .ps1 con -Carpeta)
"""
import argparse
import datetime
from pathlib import Path

import numpy as np
from pydicom.dataset import FileDataset, FileMetaDataset
from pydicom.uid import ExplicitVRLittleEndian, generate_uid

CT_STORAGE = "1.2.840.10008.5.1.4.1.1.2"
PACIENTE = {"nombre": "PRUEBA^PACIENTE^CIENTOCHENTA", "id": "PRUEBA-0180", "nac": "19750310", "sexo": "F"}


def elipse(n, cy, cx, ry, rx, borde=0.05):
    y, x = np.mgrid[0:n, 0:n]
    d = np.sqrt(((y - cy * n) / (ry * n)) ** 2 + ((x - cx * n) / (rx * n)) ** 2)
    return 1 / (1 + np.exp((d - 1) / borde))


def corte(n, t, rng):
    """t en [0, 1]: posición a lo largo del eje Z. Los pulmones se ensanchan hacia el centro de la serie."""
    cuerpo = elipse(n, .5, .5, .40, .46)
    radio = 0.10 + 0.14 * np.sin(np.pi * t)
    pulmones = elipse(n, .48, .33, radio * 1.6, radio) + elipse(n, .48, .67, radio * 1.6, radio)
    columna = elipse(n, .72, .5, .05, .05, 0.12)
    # Unidades Hounsfield aproximadas: aire -1000, pulmón -800, tejido blando 40, hueso 600
    hu = -1000 + 1040 * cuerpo - 840 * np.clip(pulmones, 0, 1) + 560 * columna
    hu += rng.normal(0, 8, hu.shape)
    return np.clip(hu + 1024, 0, 4095).astype(np.uint16)  # RescaleIntercept = -1024


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cortes", type=int, default=180)
    ap.add_argument("--tam", type=int, default=512, help="lado de cada corte en píxeles")
    ap.add_argument("--salida", type=Path,
                    default=Path(__file__).resolve().parent.parent / "docker" / "sample-dicom" / "serie-180")
    args = ap.parse_args()
    args.salida.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(2026)
    estudio_uid, serie_uid, frame_uid = generate_uid(), generate_uid(), generate_uid()
    fecha = datetime.date(2026, 9, 1).strftime("%Y%m%d")
    grosor, pixel = 1.25, 0.7

    for i in range(args.cortes):
        px = corte(args.tam, i / max(1, args.cortes - 1), rng)
        meta = FileMetaDataset()
        meta.MediaStorageSOPClassUID = CT_STORAGE
        meta.MediaStorageSOPInstanceUID = generate_uid()
        meta.TransferSyntaxUID = ExplicitVRLittleEndian
        ds = FileDataset(f"corte_{i + 1:03d}.dcm", {}, file_meta=meta, preamble=b"\0" * 128)
        ds.SOPClassUID, ds.SOPInstanceUID = CT_STORAGE, meta.MediaStorageSOPInstanceUID
        ds.PatientName, ds.PatientID = PACIENTE["nombre"], PACIENTE["id"]
        ds.PatientBirthDate, ds.PatientSex = PACIENTE["nac"], PACIENTE["sexo"]
        ds.PatientIdentityRemoved = "YES"
        ds.StudyInstanceUID, ds.SeriesInstanceUID, ds.FrameOfReferenceUID = estudio_uid, serie_uid, frame_uid
        ds.StudyDate, ds.StudyTime = fecha, "090000"
        ds.StudyDescription, ds.AccessionNumber, ds.StudyID = "TC TORAX 180 CORTES (SINTETICO)", "SINT-180", "SINT-180"
        ds.Modality, ds.BodyPartExamined = "CT", "CHEST"
        ds.SeriesDescription, ds.SeriesNumber, ds.InstanceNumber = "AXIAL 1.25 MM", "1", str(i + 1)
        ds.InstitutionName = "CONSULTORIO DE PRUEBA (SINTETICO)"
        ds.Manufacturer = "SINTETICO"
        ds.ImageComments = "Serie sintetica para pruebas de rendimiento. No es un estudio clinico."
        ds.SamplesPerPixel, ds.PhotometricInterpretation = 1, "MONOCHROME2"
        ds.Rows = ds.Columns = args.tam
        ds.BitsAllocated, ds.BitsStored, ds.HighBit, ds.PixelRepresentation = 16, 12, 11, 0
        ds.RescaleIntercept, ds.RescaleSlope = "-1024", "1"
        ds.WindowCenter, ds.WindowWidth = "-600", "1500"
        ds.PixelSpacing = [pixel, pixel]
        ds.SliceThickness, ds.SpacingBetweenSlices = grosor, grosor
        ds.ImageOrientationPatient = [1, 0, 0, 0, 1, 0]
        ds.ImagePositionPatient = [-pixel * args.tam / 2, -pixel * args.tam / 2, round(i * grosor, 3)]
        ds.SliceLocation = round(i * grosor, 3)
        ds.PixelData = px.tobytes()
        ds["PixelData"].VR = "OW"
        ds.save_as(args.salida / ds.filename, enforce_file_format=True)
    print(f"OK: {args.cortes} cortes de {args.tam}x{args.tam} en {args.salida}")
    print(f"StudyInstanceUID = {estudio_uid}\nSeriesInstanceUID = {serie_uid}\nPatientID = {PACIENTE['id']}")


if __name__ == "__main__":
    main()
