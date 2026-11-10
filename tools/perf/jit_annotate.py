#!/usr/bin/env python3
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# Copyright (c) 2026 DFD
#
# Per-instruction profile of the recompiled code of one thread.
#
#   tools/perf/jit_annotate.py <perf.data> <session dir> --thread "OSSched[core=1]" [--hot 12]
#
# Needs a session recorded with perf/tuning.txt jit_code=1 (jitcode.bin: the host code of every
# recompiled function). Prints the JIT samples grouped by instruction kind (guest memory, PPC state
# loads/stores, cycle counting, jump table, FPU, ...) and the hottest functions disassembled with
# the share of samples per instruction.
import argparse, bisect, collections, os, re, struct, subprocess, sys, tempfile

ap = argparse.ArgumentParser()
ap.add_argument("perf_data"); ap.add_argument("session")
ap.add_argument("--thread", default="OSSched[core=1]")
ap.add_argument("--ndk", default=os.path.expanduser("~/Android/Sdk/ndk/29.0.14206865"))
ap.add_argument("--hot", type=int, default=10, help="functions to print disassembled")
ap.add_argument("--min", type=float, default=0.3, help="only print instructions above this %% of the function")
a = ap.parse_args()
sys.path.insert(0, os.path.join(a.ndk, "simpleperf"))
from simpleperf_report_lib import ReportLib
BIN = os.path.join(a.ndk, "toolchains/llvm/prebuilt/linux-x86_64/bin")

# jitcode.bin: uint64 host, uint32 size, uint32 ppc, bytes. A later record at the same host
# address replaces an earlier one (recompiled code reuses freed memory only in the old allocator).
funcs = {}
with open(os.path.join(a.session, "jitcode.bin"), "rb") as f:
    data = f.read()
pos = 0
while pos + 16 <= len(data):
    host, size, ppc = struct.unpack_from("<QII", data, pos)
    pos += 16
    funcs[host] = (size, ppc, data[pos:pos + size])
    pos += size
starts = sorted(funcs)

def lookup(ip):
    i = bisect.bisect_right(starts, ip) - 1
    if i >= 0 and ip < starts[i] + funcs[starts[i]][0]:
        return starts[i]
    return None

samples = collections.Counter()   # host ip -> weight
total = 0; jit_total = 0
lib = ReportLib()
lib.SetRecordFile(a.perf_data)
while True:
    s = lib.GetNextSample()
    if s is None: break
    if s.thread_comm != a.thread: continue
    total += s.period
    if lookup(s.ip) is not None:
        samples[s.ip] += s.period
        jit_total += s.period
print(f"thread {a.thread}: {jit_total * 100 / max(total, 1):.1f} % of its cycles in recompiled code")

# disassemble only the functions that got samples
hot_funcs = collections.Counter()
for ip, w in samples.items():
    hot_funcs[lookup(ip)] += w
blob = bytearray(); where = {}
for h in hot_funcs:
    size, ppc, code = funcs[h]
    where[len(blob)] = h
    blob += code
    blob += b"\0" * ((-len(blob)) % 4)
with tempfile.TemporaryDirectory() as td:
    raw = os.path.join(td, "code.bin"); elf = os.path.join(td, "code.o")
    open(raw, "wb").write(blob)
    subprocess.run([os.path.join(BIN, "llvm-objcopy"), "-I", "binary", "-O", "elf64-littleaarch64",
                    "--rename-section", ".data=.text,code", raw, elf], check=True)
    out = subprocess.run([os.path.join(BIN, "llvm-objdump"), "-d", "--no-show-raw-insn", elf],
                         check=True, capture_output=True, text=True).stdout
blob_starts = sorted(where)
insn = {}  # host ip -> text
for line in out.splitlines():
    m = re.match(r"\s*([0-9a-f]+):\s+(.*)", line)
    if not m: continue
    off = int(m.group(1), 16)
    i = bisect.bisect_right(blob_starts, off) - 1
    h = where[blob_starts[i]]
    insn[h + off - blob_starts[i]] = m.group(2).split("//")[0].strip()

def kind(t):
    op = t.split()[0] if t else "?"
    if "x28" in t and (op.startswith("ld") or op.startswith("st")): return "guest memory ld/st"
    if "x29" in t and (op.startswith("ld") or op.startswith("st")): return "PPC state ld/st (spill/fill, LR, CR, cycles)"
    if "x27" in t: return "jump table"
    if op in ("br", "blr", "ret"): return "indirect branch / call"
    if op == "rev" or op == "rev16": return "byte swap"
    if op.startswith("fcvt") or op.startswith("scvtf") or op.startswith("ucvtf"): return "FP convert"
    if op.startswith("f") or op.startswith("ld1") or op.startswith("st1") or op == "dup" or op.startswith("ins"): return "FPU / SIMD"
    if op.startswith("b.") or op in ("b", "cbz", "cbnz", "tbz", "tbnz"): return "branch"
    if op in ("cmp", "cmn", "cset", "csel", "ccmp", "tst"): return "compare / cset"
    if op.startswith("ld") or op.startswith("st"): return "other ld/st"
    if op in ("mov", "movz", "movk", "movn", "orr") and "#" in t: return "constant"
    return "ALU"

by_kind = collections.Counter()
for ip, w in samples.items():
    by_kind[kind(insn.get(ip, "?"))] += w
print("\nJIT samples by instruction kind (the sample often lands one instruction after the one that stalls):")
for k, v in by_kind.most_common():
    print(f"  {k:46s} {v * 100 / jit_total:5.1f} %")

# static instruction mix of the sampled functions, weighted by samples per function
print("\nhottest functions:")
for h, w in hot_funcs.most_common(a.hot):
    size, ppc, code = funcs[h]
    print(f"\n== ppc {ppc:08x}  {w * 100 / jit_total:.1f} % of JIT, {size} bytes")
    for ip in range(h, h + size, 4):
        sw = samples.get(ip, 0)
        t = insn.get(ip, "?")
        mark = f"{sw * 100 / w:5.1f}" if sw else "     "
        if sw * 100 / w >= a.min or (t.split()[0] if t else "") in ("br", "blr", "ret"):
            print(f"  {mark} +{ip - h:5x}  {t}")
