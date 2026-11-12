#include "Cafe/HW/Espresso/Debugger/GuestWatch.h"
#include "Cafe/HW/Espresso/PPCState.h"
#include "Cafe/HW/Latte/Core/LattePerfLog.h"
#include "Cafe/OS/libs/coreinit/coreinit_Thread.h"
#include "Common/ExceptionHandler/ExceptionHandler.h"

#include <mutex>
#include "config/ActiveSettings.h"

namespace GuestWatch
{
	uint32 g_lo = 0;
	uint32 g_hi = 0;

	struct Entry
	{
		uint64 seq;
		uint32 pc;
		uint32 lr;
		uint32 thread;
		uint32 addr;
		uint32 value;
		uint8 size;
		char kind;
	};

	static std::mutex s_mutex;
	static std::vector<Entry> s_ring;
	static uint64 s_seq = 0;
	static bool s_pairs = false;
	static bool s_logEach = false;
	static uint32 s_dupLogs = 0;
	static uint32 s_lastDupMask = 0;

	void Init()
	{
		const sint64 lo = PerfLog::Tunable("watch_lo", 0);
		const sint64 size = PerfLog::Tunable("watch_size", 0);
		if (lo <= 0 || size <= 0)
			return;
		const sint64 ringSize = std::max<sint64>(PerfLog::Tunable("watch_ring", 4096), 64);
		s_ring.resize((size_t)ringSize);
		s_pairs = PerfLog::Tunable("watch_pairs", 0) != 0;
		s_logEach = PerfLog::Tunable("watch_log", 0) != 0; // every write to the log (low volume ranges only)
		g_lo = (uint32)lo;
		g_hi = (uint32)(lo + size);
		cemuLog_log(LogType::Force, "GuestWatch: watching 0x{:08x}-0x{:08x}, ring {}, pairs {}", g_lo, g_hi, ringSize, s_pairs);
	}

	static void FormatEntry(const Entry& e, char* buf, size_t bufSize)
	{
		snprintf(buf, bufSize, "w#%llu %c%u [%08x]=%08x pc %08x lr %08x thr %08x", (unsigned long long)e.seq, e.kind, (uint32)e.size, e.addr, e.value, e.pc, e.lr, e.thread);
	}

	// called with s_mutex held
	static void CheckPairs(const Entry& last)
	{
		const uint32 count = (g_hi - g_lo) / 8;
		uint32 dupMask = 0;
		for (uint32 i = 0; i < count && i < 32; i++)
		{
			const uint32 pi = memory_readU32(g_lo + i * 8);
			if (pi < 0x10000) // skips lock words and counters
				continue;
			for (uint32 j = i + 1; j < count && j < 32; j++)
			{
				if (memory_readU32(g_lo + j * 8) == pi)
					dupMask |= (1u << i) | (1u << j);
			}
		}
		const bool isNew = (dupMask & ~s_lastDupMask) != 0;
		s_lastDupMask = dupMask;
		if (!isNew || s_dupLogs >= 8)
			return;
		s_dupLogs++;
		char buf[160];
		cemuLog_log(LogType::Force, "GuestWatch: DUPLICATE pointer entries (mask {:08x}) after write:", dupMask);
		FormatEntry(last, buf, sizeof(buf));
		cemuLog_log(LogType::Force, "  {}", buf);
		std::string line;
		for (uint32 i = 0; i < count && i < 32; i++)
		{
			snprintf(buf, sizeof(buf), "%08x/%08x ", memory_readU32(g_lo + i * 8), memory_readU32(g_lo + i * 8 + 4));
			line += buf;
		}
		cemuLog_log(LogType::Force, "  entries: {}", line);
		cemuLog_log(LogType::Force, "  previous writes:");
		const size_t n = std::min<size_t>(s_ring.size(), std::min<uint64>(s_seq, 96));
		for (size_t k = n; k > 0; k--)
		{
			const Entry& e = s_ring[(s_seq - k) % s_ring.size()];
			FormatEntry(e, buf, sizeof(buf));
			cemuLog_log(LogType::Force, "  {}", buf);
		}
	}

	static void Add(uint32 pc, uint32 lr, uint32 thread, uint32 addr, uint32 size, uint32 value, char kind)
	{
		std::lock_guard _l(s_mutex);
		Entry& e = s_ring[s_seq % s_ring.size()];
		e.seq = s_seq;
		e.pc = pc;
		e.lr = lr;
		e.thread = thread;
		e.addr = addr;
		e.value = value;
		e.size = (uint8)std::min<uint32>(size, 255);
		e.kind = kind;
		s_seq++;
		if (s_logEach)
		{
			char buf[160];
			FormatEntry(e, buf, sizeof(buf));
			cemuLog_log(LogType::Force, "GuestWatch: {}", buf);
		}
		if (s_pairs)
			CheckPairs(e);
	}

	static uint32 CurrentThread()
	{
		return memory_getVirtualOffsetFromPointer(coreinit::OSGetCurrentThread());
	}

	// callers record after the store, so the pair check sees the new state
	void Record(PPCInterpreter_t* hCPU, uint32 addr, uint32 size, uint32 value, char kind)
	{
		Add(hCPU->instructionPointer, hCPU->spr.LR, CurrentThread(), addr, size, value, kind);
	}

	void RecordHLE(uint32 addr, uint32 size, uint32 value, char kind)
	{
		PPCInterpreter_t* hCPU = PPCInterpreter_getCurrentInstance();
		Add(hCPU ? hCPU->instructionPointer : 0, hCPU ? hCPU->spr.LR : 0, hCPU ? CurrentThread() : 0, addr, size, value, kind);
	}

	void DumpToCrashLog()
	{
		if (g_hi == 0)
			return;
		std::unique_lock _l(s_mutex, std::try_to_lock); // the crash may have happened while holding it
		CrashLog_WriteLine("");
		char buf[200];
		snprintf(buf, sizeof(buf), "GuestWatch 0x%08x-0x%08x, %llu writes, last %zu:", g_lo, g_hi, (unsigned long long)s_seq, (size_t)std::min<uint64>(s_seq, s_ring.size()));
		CrashLog_WriteLine(buf);
		// whole ring, oldest first, to files/watch_ring.bin (Entry as in GuestWatch.cpp, 32 bytes, little endian)
		const size_t n = std::min<uint64>(s_seq, s_ring.size());
		const std::string binPath = _pathToUtf8(ActiveSettings::GetUserDataPath("watch_ring.bin"));
		if (FILE* f = fopen(binPath.c_str(), "wb"))
		{
			for (size_t k = n; k > 0; k--)
				fwrite(&s_ring[(s_seq - k) % s_ring.size()], sizeof(Entry), 1, f);
			fclose(f);
			snprintf(buf, sizeof(buf), "ring written to %s (%zu entries of %zu bytes)", binPath.c_str(), n, sizeof(Entry));
			CrashLog_WriteLine(buf);
		}
		const size_t shown = std::min<size_t>(n, 64);
		for (size_t k = shown; k > 0; k--)
		{
			FormatEntry(s_ring[(s_seq - k) % s_ring.size()], buf, sizeof(buf));
			CrashLog_WriteLine(buf);
		}
	}
}
