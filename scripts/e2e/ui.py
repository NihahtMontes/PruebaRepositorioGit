#!/usr/bin/env python3
"""
Lee un volcado de `uiautomator dump` por la entrada estándar y busca nodos.

Modos:
  text VALOR      nodo cuyo texto es exactamente VALOR
  desc VALOR      nodo cuya descripción (content-desc) es exactamente VALOR
  contains VALOR  nodo cuyo texto o descripción contiene VALOR
  edit -          primer campo de texto editable
  keypad -        coordenadas de las teclas 0-9 y de "Aceptar" (una por línea: "tecla x y")

Imprime "x y" (centro del nodo) y sale con 0, o sale con 1 si no lo encuentra.
"""
import re
import sys
import xml.etree.ElementTree as ET


def center(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    if x2 <= x1 or y2 <= y1:
        return None
    return (x1 + x2) // 2, (y1 + y2) // 2


def main():
    mode = sys.argv[1]
    value = sys.argv[2] if len(sys.argv) > 2 else ""
    raw = sys.stdin.read()
    start = raw.find("<?xml")
    if start < 0:
        sys.exit(2)
    root = ET.fromstring(raw[start:].strip())

    if mode == "keypad":
        keys = {}
        for node in root.iter("node"):
            text, desc = node.get("text", ""), node.get("content-desc", "")
            pos = center(node)
            if pos is None:
                continue
            if len(text) == 1 and text.isdigit():
                keys[text] = pos
            elif desc == "Aceptar":
                keys["ok"] = pos
        if len(keys) < 11:
            sys.exit(1)
        for key, (x, y) in keys.items():
            print(key, x, y)
        return

    found = None
    for node in root.iter("node"):
        text, desc, cls = node.get("text", ""), node.get("content-desc", ""), node.get("class", "")
        is_edit = "EditText" in cls
        if mode == "edit":
            match = is_edit
        elif is_edit:
            continue  # nunca pulsar el campo de búsqueda por su texto
        elif mode == "text":
            match = text == value
        elif mode == "desc":
            match = desc == value
        elif mode == "contains":
            match = value in text or value in desc
        else:
            sys.exit(3)
        if match:
            pos = center(node)
            if pos is not None:
                found = pos
                if mode == "edit":
                    break
    if found is None:
        sys.exit(1)
    print(found[0], found[1])


if __name__ == "__main__":
    main()
