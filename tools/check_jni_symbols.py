#!/usr/bin/env python3
"""Gleicht die `external fun`-Deklarationen in WhisperLib.kt mit den JNIEXPORT-Symbolen in
whisper_jni.cpp ab — in beide Richtungen, inklusive Parameterzahl und Symbol-Praefix aus
Paket + Objektname. Lokal gibt es kein NDK; der Abgleich ersetzt den Linker-Test.
Aufruf: python3 tools/check_jni_symbols.py  (Exit 0 = ok, 1 = Abweichung)"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
KT = ROOT / "app/src/main/java/com/chris/whisperloom/whisper/WhisperLib.kt"
CPP = ROOT / "app/src/main/cpp/whisper_jni.cpp"


def count_params(param_list: str) -> int:
    return len([p for p in param_list.split(",") if p.strip()])


def main() -> int:
    kt = KT.read_text(encoding="utf-8")
    cpp = CPP.read_text(encoding="utf-8")

    package = re.search(r"^package\s+([\w.]+)", kt, re.M).group(1)
    obj = re.search(r"\bobject\s+(\w+)", kt).group(1)
    # JNI-Mangling: "." -> "_", "_" -> "_1"
    prefix = "Java_" + (package + "." + obj).replace("_", "_1").replace(".", "_") + "_"

    kt_funs = {
        m.group(1): count_params(m.group(2))
        for m in re.finditer(r"external\s+fun\s+(\w+)\s*\(([^)]*)\)", kt)
    }
    cpp_funs = {}
    errors = []
    for m in re.finditer(r"JNIEXPORT\s+\w+\s+JNICALL\s+(Java_\w+)\s*\(([^)]*)\)", cpp):
        symbol, params = m.group(1), m.group(2)
        if not symbol.startswith(prefix):
            errors.append(f"C++-Symbol {symbol} hat nicht das Praefix {prefix}")
            continue
        name = symbol[len(prefix):]
        if name in cpp_funs:
            errors.append(f"C++-Symbol {symbol} doppelt")
        cpp_funs[name] = count_params(params) - 2  # JNIEnv*, jobject

    for name, n in kt_funs.items():
        if name not in cpp_funs:
            errors.append(f"Kotlin external fun {name} ohne C++-Symbol {prefix}{name}")
        elif cpp_funs[name] != n:
            errors.append(f"{name}: Kotlin hat {n} Parameter, C++ {cpp_funs[name]}")
    for name in cpp_funs:
        if name not in kt_funs:
            errors.append(f"C++-Symbol {prefix}{name} ohne Kotlin external fun")

    if not kt_funs:
        errors.append("keine external fun in WhisperLib.kt gefunden")
    for e in errors:
        print("FEHLER:", e)
    if errors:
        return 1
    print(f"OK: {len(kt_funs)} JNI-Symbole stimmen ueberein ({prefix}*)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
