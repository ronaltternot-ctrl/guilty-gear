#!/usr/bin/env python3
"""Compiles the post-processing shaders the way PPSSPP does, to catch errors before a build.

  1. As GLSL ES 1.00 (what PPSSPP's GL backend parses with glslang).
  2. Converted for Vulkan with the same text rewrites as PPSSPP's ConvertToVulkanGLSL
     (Common/GPU/ShaderTranslation.cpp), then compiled to SPIR-V.

Usage: python3 scripts/check_shaders.py <glslangValidator> <shader_dir> [vertex_shader]
"""
import os
import re
import subprocess
import sys
import tempfile

VULKAN_PROLOGUE = """#version 450
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable
"""
VULKAN_UBO = """
layout (std140, set = 0, binding = 0) uniform Data {
	vec2 u_texelDelta;
	vec2 u_pixelDelta;
	vec4 u_time;
	vec4 u_timeDelta;
	vec4 u_setting;
	float u_video;
	float u_vr;
};
"""


def to_vulkan(src, stage):
    out = [VULKAN_PROLOGUE]
    if stage == "frag":
        out.append("layout (location = 0) out vec4 fragColor0;\n")
    out.append(VULKAN_UBO)
    for line in src.split("\n"):
        if "uniform bool" in line:
            continue
        elif line.startswith("uniform sampler2D"):
            m = re.match(r"uniform sampler2D sampler(\d+)", line)
            if m:
                line = "layout(set = 0, binding = %d) " % (int(m.group(1)) + 1) + line
            elif "sampler0" in line:
                line = "layout(set = 0, binding = 1) " + line
            else:
                line = "layout(set = 0, binding = 2) " + line
        elif "uniform " in line:
            continue
        else:
            m = re.match(r"varying\s+vec(\d)\s+v_texcoord(\d)\s*;", line)
            if m:
                size, num = m.groups()
                q = "in" if stage == "frag" else "out"
                line = "layout(location = %s) %s vec%s v_texcoord%s;" % (num, q, size, num)
        if stage == "vert":
            line = line.replace("attribute vec4 a_position;", "layout(location = 0) in vec4 a_position;")
            line = line.replace("attribute vec2 a_texcoord0;", "layout(location = 3) in vec2 a_texcoord0;")
            line = line.replace("varying vec2 v_position;", "layout(location = 0) out vec2 v_position;")
        else:
            line = line.replace("varying vec2 v_position;", "layout(location = 0) in vec2 v_position;")
            line = line.replace("texture2D(", "texture(")
            line = line.replace("gl_FragColor", "fragColor0")
        out.append(line + "\n")
    return "".join(out)


def run(validator, path, args):
    r = subprocess.run([validator] + args + [path], capture_output=True, text=True)
    return r.returncode, (r.stdout + r.stderr).strip()


def main(validator, shader_dir, vertex=None):
    files = sorted(f for f in os.listdir(shader_dir) if f.endswith(".fsh"))
    if vertex:
        files.append(vertex)
    failures = 0
    with tempfile.TemporaryDirectory() as tmp:
        for name in files:
            path = name if os.path.isabs(name) else os.path.join(shader_dir, name)
            stage = "vert" if name.endswith(".vsh") else "frag"
            src = open(path, encoding="utf-8").read()
            es = os.path.join(tmp, os.path.basename(name) + ".es." + stage)
            with open(es, "w") as f:
                f.write(src)
            code1, log1 = run(validator, es, [])
            vk = os.path.join(tmp, os.path.basename(name) + ".vk." + stage)
            with open(vk, "w") as f:
                f.write(to_vulkan(src, stage))
            code2, log2 = run(validator, vk, ["-V", "-o", os.path.join(tmp, "out.spv")])
            ok = code1 == 0 and code2 == 0
            failures += 0 if ok else 1
            print("%-28s GLES 1.00: %s   Vulkan: %s" % (os.path.basename(name), "ok" if code1 == 0 else "FAIL", "ok" if code2 == 0 else "FAIL"))
            for code, log in ((code1, log1), (code2, log2)):
                if code != 0:
                    print("   " + "\n   ".join(l for l in log.splitlines() if "ERROR" in l or "error" in l)[:2000])
    print("failures:", failures)
    return failures


if __name__ == "__main__":
    sys.exit(1 if main(*sys.argv[1:]) else 0)
