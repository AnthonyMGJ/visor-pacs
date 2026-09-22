"""
Genera radiografías DICOM SINTÉTICAS para pruebas técnicas del Visor PACS.
No son estudios clínicos ni contienen datos de pacientes reales.
Uso:  pip install pydicom numpy   y luego   python tools/generar_dicom_sinteticos.py
"""
import datetime
from pathlib import Path

import numpy as np
from pydicom.dataset import FileDataset, FileMetaDataset
from pydicom.uid import ExplicitVRLittleEndian, generate_uid

SALIDA = Path(__file__).resolve().parent.parent / "docker" / "sample-dicom" / "sinteticos"
rng = np.random.default_rng(2026)


def elipse(h, w, cy, cx, ry, rx, borde=0.06):
    y, x = np.mgrid[0:h, 0:w]
    d = np.sqrt(((y - cy * h) / (ry * h)) ** 2 + ((x - cx * w) / (rx * w)) ** 2)
    return 1 / (1 + np.exp((d - 1) / borde))


def segmento(h, w, p0, p1, ancho):
    y, x = np.mgrid[0:h, 0:w]
    (y0, x0), (y1, x1) = (p0[0] * h, p0[1] * w), (p1[0] * h, p1[1] * w)
    dy, dx = y1 - y0, x1 - x0
    t = np.clip(((y - y0) * dy + (x - x0) * dx) / (dy * dy + dx * dx), 0, 1)
    d = np.hypot(y - (y0 + t * dy), x - (x0 + t * dx))
    return 1 / (1 + np.exp((d - ancho * w) / (0.004 * w)))


def a_pixeles(img):
    img = np.clip(img + rng.normal(0, 0.012, img.shape), 0, 1)
    return (img * 3800).astype(np.uint16)  # 12 bits almacenados


def torax_pa(h=1024, w=1024):
    cuerpo = elipse(h, w, .5, .5, .49, .43)
    pulmones = elipse(h, w, .45, .33, .30, .16) + elipse(h, w, .45, .67, .30, .16)
    y, x = np.mgrid[0:h, 0:w]
    costillas = 0.07 * np.clip(np.sin(y / (h * .022) + np.abs(x - w / 2) / (w * .12)), 0, 1) ** 4 * pulmones
    columna = 0.22 * np.exp(-(((x - w / 2) / (w * .03)) ** 2)) * cuerpo
    corazon = 0.25 * elipse(h, w, .56, .44, .14, .11)
    return a_pixeles(0.05 + 0.55 * cuerpo - 0.33 * np.clip(pulmones, 0, 1) + costillas + columna + corazon)


def torax_lateral(h=1024, w=1024):
    cuerpo = elipse(h, w, .5, .5, .49, .27)
    pulmon = elipse(h, w, .45, .48, .30, .17)
    y, x = np.mgrid[0:h, 0:w]
    columna = 0.28 * np.exp(-(((x - w * .70) / (w * .035)) ** 2)) * cuerpo
    esternon = 0.18 * np.exp(-(((x - w * .30) / (w * .015)) ** 2)) * cuerpo
    corazon = 0.20 * elipse(h, w, .57, .40, .12, .09)
    return a_pixeles(0.05 + 0.55 * cuerpo - 0.32 * pulmon + columna + esternon + corazon)


def mano_ap(h=1024, w=768):
    img = 0.06 + 0.30 * elipse(h, w, .68, .5, .30, .30) + 0.16 * elipse(h, w, .38, .5, .40, .32)
    for x0, x1 in [(.22, .17), (.38, .36), (.50, .50), (.62, .64), (.76, .84)]:
        for a, b in [((.62, x0 + .05), (.44, x0)), ((.44, x0), (.30, (x0 + x1) / 2)), ((.30, (x0 + x1) / 2), (.16, x1))]:
            img += 0.45 * segmento(h, w, a, b, .022)
    return a_pixeles(img)


def rodilla_ap(h=1024, w=768):
    img = 0.06 + 0.28 * elipse(h, w, .5, .5, .5, .34)
    img += 0.42 * segmento(h, w, (.0, .5), (.44, .48), .085) + 0.42 * segmento(h, w, (.56, .5), (1.0, .52), .06)
    img += 0.10 * elipse(h, w, .5, .5, .05, .16) + 0.35 * elipse(h, w, .43, .49, .05, .05)
    img -= 0.08 * segmento(h, w, (.48, .40), (.48, .60), .012)  # espacio articular
    return a_pixeles(img)


def guardar(nombre, px, modalidad, sop_clase, pac, estudio, serie_desc, vista, parte, num_serie):
    h, w = px.shape
    meta = FileMetaDataset()
    meta.MediaStorageSOPClassUID = sop_clase
    meta.MediaStorageSOPInstanceUID = generate_uid()
    meta.TransferSyntaxUID = ExplicitVRLittleEndian
    ds = FileDataset(nombre, {}, file_meta=meta, preamble=b"\0" * 128)
    ds.SOPClassUID, ds.SOPInstanceUID = sop_clase, meta.MediaStorageSOPInstanceUID
    ds.PatientName, ds.PatientID = pac["nombre"], pac["id"]
    ds.PatientBirthDate, ds.PatientSex = pac["nac"], pac["sexo"]
    ds.PatientIdentityRemoved = "YES"
    ds.StudyInstanceUID, ds.SeriesInstanceUID = estudio["uid"], generate_uid()
    ds.StudyDate, ds.StudyTime = estudio["fecha"], "101500"
    ds.StudyDescription, ds.AccessionNumber, ds.StudyID = estudio["desc"], estudio["acc"], estudio["acc"]
    ds.Modality, ds.BodyPartExamined, ds.ViewPosition = modalidad, parte, vista
    ds.SeriesDescription, ds.SeriesNumber, ds.InstanceNumber = serie_desc, str(num_serie), "1"
    ds.InstitutionName = "CONSULTORIO DE PRUEBA (SINTETICO)"
    ds.Manufacturer = "SINTETICO"
    ds.ImageComments = "Imagen sintetica para pruebas tecnicas. No es un estudio clinico."
    ds.SamplesPerPixel, ds.PhotometricInterpretation = 1, "MONOCHROME2"
    ds.Rows, ds.Columns = h, w
    ds.BitsAllocated, ds.BitsStored, ds.HighBit, ds.PixelRepresentation = 16, 12, 11, 0
    ds.WindowCenter, ds.WindowWidth = "1900", "3800"
    ds.ImagerPixelSpacing = [0.2, 0.2]
    ds.PixelData = px.tobytes()
    ds["PixelData"].VR = "OW"
    ds.save_as(SALIDA / nombre, enforce_file_format=True)
    print("OK", nombre, f"{w}x{h}", modalidad)


CR, DX = "1.2.840.10008.5.1.4.1.1.1", "1.2.840.10008.5.1.4.1.1.1.1"
SALIDA.mkdir(parents=True, exist_ok=True)
hoy = datetime.date(2026, 9, 1)
p1 = {"nombre": "PRUEBA^PACIENTE^UNO", "id": "PRUEBA-0001", "nac": "19800101", "sexo": "M"}
p2 = {"nombre": "PRUEBA^PACIENTE^DOS", "id": "PRUEBA-0002", "nac": "19921215", "sexo": "F"}
p3 = {"nombre": "PRUEBA^PACIENTE^TRES", "id": "PRUEBA-0003", "nac": "19650630", "sexo": "M"}
e1 = {"uid": generate_uid(), "fecha": (hoy).strftime("%Y%m%d"), "desc": "TORAX PA Y LATERAL", "acc": "SINT-001"}
e2 = {"uid": generate_uid(), "fecha": (hoy - datetime.timedelta(days=9)).strftime("%Y%m%d"), "desc": "MANO DERECHA AP", "acc": "SINT-002"}
e3 = {"uid": generate_uid(), "fecha": (hoy - datetime.timedelta(days=20)).strftime("%Y%m%d"), "desc": "RODILLA AP", "acc": "SINT-003"}
guardar("torax_pa.dcm", torax_pa(), "CR", CR, p1, e1, "TORAX PA", "PA", "CHEST", 1)
guardar("torax_lateral.dcm", torax_lateral(), "CR", CR, p1, e1, "TORAX LATERAL", "LL", "CHEST", 2)
guardar("mano_ap.dcm", mano_ap(), "CR", CR, p2, e2, "MANO AP", "AP", "HAND", 1)
guardar("rodilla_ap.dcm", rodilla_ap(), "DX", DX, p3, e3, "RODILLA AP", "AP", "KNEE", 1)
