#pragma once

// Diagnostics: guest memory write watch for the interpreter and the HLE memory functions.
// perf/tuning.txt watch_lo=<decimal guest address> watch_size=<bytes> turns it on. Every write that touches the range
// goes into a ring buffer (PC, LR, thread, address, value) that the crash log dumps. watch_pairs=1 also checks, after
// each write, that no two 8-byte {pointer, tag} entries in the range hold the same non-zero pointer and logs the moment
// one does (free list heads handed out twice). watch_jit=1 also catches recompiled code (page protection).
struct PPCInterpreter_t;

namespace GuestWatch
{
	extern uint32 g_lo;
	extern uint32 g_hi; // exclusive, 0 = off

	void Init();
	void Record(PPCInterpreter_t* hCPU, uint32 addr, uint32 size, uint32 value, char kind);
	void RecordHLE(uint32 addr, uint32 size, uint32 value, char kind);
	void DumpToCrashLog();
	// watch_jit=1 (Android/AArch64): a fault on the read-only pages of the range; true when it was handled
	bool HandleFault(void* faultAddr, void* context);

	inline void OnWrite(PPCInterpreter_t* hCPU, uint32 addr, uint32 size, uint32 value, char kind)
	{
		if (addr < g_hi && addr + size > g_lo) [[unlikely]]
			Record(hCPU, addr, size, value, kind);
	}

	inline void OnWriteHLE(uint32 addr, uint32 size, uint32 value, char kind)
	{
		if (addr < g_hi && addr + size > g_lo) [[unlikely]]
			RecordHLE(addr, size, value, kind);
	}
}
