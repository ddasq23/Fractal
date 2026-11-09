#!/usr/bin/env python3
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# Copyright (c) 2026 DFD
#
# Breaks a simpleperf recording of the emulation process down per thread into
# JIT code (mapped to PPC functions with the perf log's jitmap.txt), interpreter,
# HLE, idle loop, time reads, kernel, GPU driver and the rest. With a call-graph
# recording (--call-graph fp), samples under coreinit::__OSThreadCoreIdle (the emulated
# core's busy idle loop: audio, alarms, run-queue polling) are counted as "idle loop".
#
#   python3 tools/perf/jit_profile.py <perf.data> <session dir with jitmap.txt> [--symfs binary_cache]
#       [--ndk <ndk dir>] [--threads "OSSched[core=1],LatteThread"] [--top 25]
import argparse, bisect, collections, os, sys

ap = argparse.ArgumentParser()
ap.add_argument("perf_data"); ap.add_argument("session")
ap.add_argument("--symfs", default=None)
ap.add_argument("--ndk", default=os.path.expanduser("~/Android/Sdk/ndk/29.0.14206865"))
ap.add_argument("--threads", default=None)
ap.add_argument("--top", type=int, default=25)
a = ap.parse_args()
sys.path.insert(0, os.path.join(a.ndk, "simpleperf"))
from simpleperf_report_lib import ReportLib

# jitmap: host start, host size, ppc address, ppc size
jit = []
for line in open(os.path.join(a.session, "jitmap.txt")):
    p = line.split()
    if len(p) == 4:
        # PPC function: host, size, ppc address, ppc size; glue (thunks): host, size, 0, name
        jit.append((int(p[0], 16), int(p[1], 16), int(p[2], 16), p[3]))
jit.sort()
starts = [j[0] for j in jit]
fails = {}
fp = os.path.join(a.session, "jitfail.txt")
if os.path.exists(fp):
    for line in open(fp):
        p = line.split()
        if len(p) >= 3: fails[int(p[0], 16)] = p[2]

def jit_lookup(ip):
    i = bisect.bisect_right(starts, ip) - 1
    if i >= 0 and ip < jit[i][0] + jit[i][1]:
        return jit[i]
    return None

TIME = ("clock_gettime", "cntvct", "el0_sys", "do_sysinstr", "PPCTimer_getFromRDTSC", "steady_clock::now", "__udivmodti4")
def category(dso, sym, ip):
    if dso in ("unknown", "[unknown]", "") or sym.startswith("unknown"):
        j = jit_lookup(ip)
        if j and j[2] == 0:
            return ("JIT glue (" + j[3] + ")", None)
        return ("JIT", j[2]) if j else ("anon (not in jitmap)", None)
    if any(t in sym for t in TIME): return ("time reads", None)
    if "PPCInterpreter" in sym or "PPCInterpreterSlim" in sym: return ("interpreter", None)
    if "virtualHLE" in sym or "osLib_" in sym or "HLE" in sym: return ("HLE dispatch", None)
    if "kernel" in dso: return ("kernel (other)", None)
    if "vulkan" in dso or "libgsl" in dso or "adreno" in dso or "mali" in dso or "panfrost" in dso or "freedreno" in dso: return ("GPU driver", None)
    if "libCemuAndroid" in dso: return ("Cemu (C++)", None)
    return ("libc / other libs", None)

lib = ReportLib()
lib.SetRecordFile(a.perf_data)
if a.symfs: lib.SetSymfs(a.symfs)
want = set(a.threads.split(",")) if a.threads else None
cat = collections.defaultdict(collections.Counter)      # thread -> category -> samples
ppcfun = collections.defaultdict(collections.Counter)   # thread -> ppc function -> samples
cppsym = collections.defaultdict(collections.Counter)   # thread -> (category, symbol) -> samples
total = collections.Counter()
while True:
    s = lib.GetNextSample()
    if s is None: break
    t = s.thread_comm
    if want and t not in want: continue
    sym = lib.GetSymbolOfCurrentSample()
    dso, name = sym.dso_name, sym.symbol_name or ""
    c, ppc = category(dso, name, s.ip)
    chain = lib.GetCallChainOfCurrentSample()
    if chain is not None and chain.nr:
        for k in range(chain.nr):
            if "__OSThreadCoreIdle" in (chain.entries[k].symbol.symbol_name or ""):
                c, ppc = "idle loop", None
                break
    w = s.period
    total[t] += w; cat[t][c] += w
    if ppc is not None: ppcfun[t][ppc] += w
    else: cppsym[t][(c, name[:90])] += w

for t, tot in total.most_common():
    if tot * 100 < sum(total.values()) * 2: continue  # threads under 2 %
    print(f"\n## {t} ({tot * 100 / sum(total.values()):.1f} % of process cycles)")
    for c, v in cat[t].most_common():
        print(f"  {c:24s} {v * 100 / tot:5.1f} %")
    if ppcfun[t]:
        jt = sum(ppcfun[t].values())
        print(f"  top PPC functions (share of JIT time; {len(ppcfun[t])} functions sampled):")
        for f, v in ppcfun[t].most_common(a.top):
            print(f"    {f:08x}  {v * 100 / jt:5.1f} %{'  [also failed: ' + fails[f] + ']' if f in fails else ''}")
    print("  top native symbols:")
    for (c, n), v in cppsym[t].most_common(a.top):
        print(f"    {v * 100 / tot:5.1f} %  [{c}] {n}")
print(f"\njitmap: {len(jit)} functions; jitfail: {len(fails)} functions left to the interpreter",
      collections.Counter(fails.values()).most_common())
