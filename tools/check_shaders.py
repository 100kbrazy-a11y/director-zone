#!/usr/bin/env python3
"""Pulls the GLSL out of Shaders.kt and runs glslangValidator on it (GLSL ES 1.00)."""
import re, subprocess, sys, os, tempfile
src = open(os.path.join(os.path.dirname(__file__), "..", "app/src/main/java/com/directorzone/engine/Shaders.kt")).read()
parts = re.findall(r'"""(.*?)"""', src, re.S)
vertex, common, grade, trans, phone = parts[:5]
jobs = {"vert": ("vert", vertex), "grade": ("frag", common + grade), "transition": ("frag", common + trans), "phone": ("frag", common + phone)}
bad = 0
for name, (stage, code) in jobs.items():
    f = os.path.join(tempfile.gettempdir(), f"dz_{name}.{stage}")
    open(f, "w").write("#version 100\n" + code)
    r = subprocess.run(["glslangValidator", "-S", stage, f], capture_output=True, text=True)
    print(name, "OK" if r.returncode == 0 else "FAIL"); 
    if r.returncode: print(r.stdout, r.stderr); bad += 1
sys.exit(bad)
