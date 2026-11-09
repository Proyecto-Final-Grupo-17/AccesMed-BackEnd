"""Genera los audios fijos del canal de WhatsApp con la API de voz de OpenAI.

Uso, desde la raíz del repo:  python -I Scripts/generar-audios-fijos.py [archivo ...]
Sin argumentos genera todos los de Scripts/audios-fijos.md; con argumentos, solo esos
(por ejemplo: python -I Scripts/generar-audios-fijos.py espera despedida).

Lee la key de ACCESMED_OPENAI_API_KEY (variable de entorno o .env) y el modelo, la voz y
las instrucciones de src/main/resources/application.yml, los mismos que usa el back para
las respuestas. Escribe los .ogg (Opus) en ./audios. Detalle en Scripts/audios-fijos.md.
"""

import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
ARCHIVO_TEXTOS = RAIZ / "Scripts" / "audios-fijos.md"
ARCHIVO_CONFIG = RAIZ / "src" / "main" / "resources" / "application.yml"
ARCHIVO_ENV = RAIZ / ".env"
CARPETA_SALIDA = RAIZ / "audios"
URL_VOZ = "https://api.openai.com/v1/audio/speech"


def read_api_key():
    """Devuelve la key de OpenAI: primero la variable de entorno, después el .env."""
    api_key = os.environ.get("ACCESMED_OPENAI_API_KEY", "").strip()
    if not api_key and ARCHIVO_ENV.exists():
        for linea in ARCHIVO_ENV.read_text(encoding="utf-8").splitlines():
            if linea.startswith("ACCESMED_OPENAI_API_KEY="):
                api_key = linea.split("=", 1)[1].strip().strip('"').strip("'")
    if not api_key:
        sys.exit("Falta ACCESMED_OPENAI_API_KEY (variable de entorno o .env).")
    return api_key


def read_config_voz():
    """Lee modelo, voz e instrucciones del bloque accesmed.audio de application.yml."""
    contenido = ARCHIVO_CONFIG.read_text(encoding="utf-8")
    config_voz = {}
    for clave in ("modelo-voz", "voz", "instrucciones-voz"):
        coincidencia = re.search(r"^\s+" + re.escape(clave) + r":\s*(.+)$", contenido, re.MULTILINE)
        if not coincidencia:
            sys.exit(f"No encontré accesmed.audio.{clave} en application.yml.")
        config_voz[clave] = coincidencia.group(1).strip().strip('"').strip("'")
    return config_voz


def read_textos():
    """Devuelve {archivo: texto} de las líneas '- `archivo`: texto' de audios-fijos.md."""
    textos = {}
    for linea in ARCHIVO_TEXTOS.read_text(encoding="utf-8").splitlines():
        coincidencia = re.match(r"^- `([a-z0-9-]+)`: (.+)$", linea)
        if coincidencia:
            textos[coincidencia.group(1)] = coincidencia.group(2).strip()
    if not textos:
        sys.exit("No hay textos en Scripts/audios-fijos.md.")
    return textos


def create_audio(api_key, config_voz, texto):
    """Sintetiza un texto con los mismos parámetros que OpenAiClient.createVoz."""
    cuerpo = json.dumps({
        "model": config_voz["modelo-voz"],
        "voice": config_voz["voz"],
        "input": texto,
        "instructions": config_voz["instrucciones-voz"],
        "response_format": "opus",
    }).encode("utf-8")
    pedido = urllib.request.Request(URL_VOZ, data=cuerpo, method="POST", headers={
        "Authorization": "Bearer " + api_key,
        "Content-Type": "application/json",
    })
    with urllib.request.urlopen(pedido, timeout=60) as respuesta:
        return respuesta.read()


def main():
    api_key = read_api_key()
    config_voz = read_config_voz()
    textos = read_textos()

    pedidos = sys.argv[1:] or list(textos)
    desconocidos = [nombre for nombre in pedidos if nombre not in textos]
    if desconocidos:
        sys.exit("No están en audios-fijos.md: " + ", ".join(desconocidos))

    CARPETA_SALIDA.mkdir(exist_ok=True)
    print(f"Modelo {config_voz['modelo-voz']}, voz {config_voz['voz']} -> {CARPETA_SALIDA}")
    for nombre in pedidos:
        try:
            audio = create_audio(api_key, config_voz, textos[nombre])
        except urllib.error.HTTPError as error:
            sys.exit(f"{nombre}: OpenAI respondió {error.code}: {error.read().decode('utf-8', 'replace')[:300]}")
        destino = CARPETA_SALIDA / f"{nombre}.ogg"
        destino.write_bytes(audio)
        print(f"  {destino.name}: {len(audio) // 1024} KB")


if __name__ == "__main__":
    main()
