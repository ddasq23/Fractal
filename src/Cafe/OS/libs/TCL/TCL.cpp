#include "Cafe/OS/common/OSCommon.h"
#include "Cafe/OS/libs/TCL/TCL.h"
#include "HW/Latte/Core/LattePM4.h"
#include "OS/libs/coreinit/coreinit_Time.h"
#include "Common/CafeStaticVar.h"

namespace TCL
{
	constinit COSStaticData s_rplData;

	CafeStaticVar<coreinit::OSEvent> s_updateRetirementEvent{s_rplData};
	uint64 s_currentRetireMarker = 0;

	struct TCLStatePPC // mapped into PPC space
	{
		uint64be gpuRetireMarker; // written by GPU
	};

	CafeStaticVar<TCLStatePPC> s_tclStatePPC{s_rplData};

	// called from GPU for timestamp EOP event
	void TCLGPUNotifyNewRetirementTimestamp()
	{
		if (!s_updateRetirementEvent.IsAllocated())
			return;
		// gpuRetireMarker is updated via event eop command
		__OSLockScheduler();
		coreinit::OSSignalEventAllInternal(s_updateRetirementEvent.GetPtr());
		__OSUnlockScheduler();
	}

	int TCLTimestamp(TCLTimestampId id, uint64be* timestampOut)
	{
		if (id == TCLTimestampId::TIMESTAMP_LAST_BUFFER_RETIRED)
		{
			MEMPTR<uint32> b;
			// this is the timestamp of the last buffer that was retired by the GPU
			stdx::atomic_ref<uint64be> retireTimestamp(s_tclStatePPC->gpuRetireMarker);
			*timestampOut = retireTimestamp.load();
			return 0;
		}
		else
		{
			cemuLog_log(LogType::Force, "TCLTimestamp(): Unsupported timestamp ID {}", (uint32)id);
			*timestampOut = 0;
			return 0;
		}
	}

	int TCLWaitTimestamp(TCLTimestampId id, uint64 waitTs, uint64 timeout)
	{
		if (id == TCLTimestampId::TIMESTAMP_LAST_BUFFER_RETIRED)
		{
			while ( true )
			{
				stdx::atomic_ref<uint64be> retireTimestamp(s_tclStatePPC->gpuRetireMarker);
				uint64 currentTimestamp = retireTimestamp.load();
				if (currentTimestamp >= waitTs)
					return 0;
				coreinit::OSWaitEvent(s_updateRetirementEvent.GetPtr());
			}
		}
		else
		{
			cemuLog_log(LogType::Force, "TCLWaitTimestamp(): Unsupported timestamp ID {}", (uint32)id);
		}
		return 0;
	}

	static constexpr uint32 TCL_RING_BUFFER_SIZE = 4096; // in U32s

	std::atomic<uint32> tclRingBufferA[TCL_RING_BUFFER_SIZE];
	std::atomic<uint32> tclRingBufferA_readIndex{0};
	std::atomic<uint32> tclRingBufferA_writeIndex{0};

	// sleeping GPU thread: woken by TCLGPUWake (seq_cst on both sides, so either the waiter sees the new token or the waker sees it sleeping)
	static std::mutex s_gpuWakeMutex;
	static std::condition_variable s_gpuWakeCondition;
	static std::atomic<uint32> s_gpuWakeSeq{0};
	static std::atomic<bool> s_gpuSleeping{false};

	uint32 TCLGPUWakeToken()
	{
		return s_gpuWakeSeq.load(std::memory_order::seq_cst);
	}

	void TCLGPUWake()
	{
		s_gpuWakeSeq.fetch_add(1, std::memory_order::seq_cst);
		if (s_gpuSleeping.load(std::memory_order::seq_cst))
		{
			std::lock_guard lock(s_gpuWakeMutex);
			s_gpuWakeCondition.notify_one();
		}
	}

	void TCLGPUWaitForWork(uint32 wakeToken, uint32 timeoutUs)
	{
		std::unique_lock lock(s_gpuWakeMutex);
		s_gpuSleeping.store(true, std::memory_order::seq_cst);
		s_gpuWakeCondition.wait_for(lock, std::chrono::microseconds(timeoutUs), [wakeToken] {
			return s_gpuWakeSeq.load(std::memory_order::seq_cst) != wakeToken;
		});
		s_gpuSleeping.store(false, std::memory_order::relaxed);
	}

	// GPU code calls this to grab the next command word
	bool TCLGPUReadRBWord(uint32& cmdWord)
	{
		uint32 readIndex = tclRingBufferA_readIndex.load(std::memory_order::relaxed);
		uint32 writeIndex = tclRingBufferA_writeIndex.load(std::memory_order::acquire);
		if (readIndex == writeIndex)
			return false;
		cmdWord = tclRingBufferA[readIndex].load(std::memory_order::relaxed);
		tclRingBufferA_readIndex.store((readIndex + 1) % TCL_RING_BUFFER_SIZE, std::memory_order::release);
		return true;
	}

	void TCLWaitForRBSpace(uint32be numU32s)
	{
		uint32 writeIndex = tclRingBufferA_writeIndex.load(std::memory_order::relaxed);
		while (true)
		{
			uint32 readIndex = tclRingBufferA_readIndex.load(std::memory_order::acquire);
			uint32 distance = (readIndex + TCL_RING_BUFFER_SIZE - writeIndex) & (TCL_RING_BUFFER_SIZE - 1);
			if (writeIndex == readIndex) // buffer completely empty
				distance = TCL_RING_BUFFER_SIZE;
			if (distance >= numU32s + 1) // assume distance minus one, because we are never allowed to completely wrap around
				break;
			coreinit::OSSleepTicks(coreinit::EspressoTime::ConvertNsToTimerTicks(500000)); // sleep 0.5ms
		}
	}

	// this function assumes that TCLWaitForRBSpace was called and that there is enough space
	void TCLWriteCmd(uint32be* cmd, uint32 cmdLen)
	{
		uint32 writeIndex = tclRingBufferA_writeIndex.load(std::memory_order::relaxed);

		while (cmdLen > 0)
		{
			tclRingBufferA[writeIndex].store(*cmd, std::memory_order::relaxed);
			writeIndex++;
			writeIndex &= (TCL_RING_BUFFER_SIZE - 1);
			cmd++;
			cmdLen--;
		}

		tclRingBufferA_writeIndex.store(writeIndex, std::memory_order::release);
		TCLGPUWake();
	}

	#define EVENT_TYPE_TS		5

	void TCLSubmitRetireMarker(bool triggerEventInterrupt)
	{
		s_currentRetireMarker++;
		uint32be cmd[6];
		cmd[0] = pm4HeaderType3(IT_EVENT_WRITE_EOP, 5);
		cmd[1] = (4 | (EVENT_TYPE_TS << 8)); // event type (bits 8-15) and event index (bits 0-7).
		cmd[2] = MEMPTR<void>(&s_tclStatePPC->gpuRetireMarker).GetMPTR(); // address lower 32bits + data sel bits
		cmd[3] = 0x40000000; // select 64bit write, lower 16 bits are the upper bits of the address
		if (triggerEventInterrupt)
			cmd[3] |= 0x2000000; // trigger interrupt after value has been written
		cmd[4] = (uint32)s_currentRetireMarker; // data lower 32 bits
		cmd[5] = (uint32)(s_currentRetireMarker>>32); // data higher 32 bits
		TCLWriteCmd(cmd, 6);
	}

	// Submissions come from several cores at once: Watch Dogs records its streamout display lists on a job thread and
	// submits them with GX2DirectCallDisplayList while the main thread flushes its command buffer. Unserialized, two
	// submissions can copy their commands to the same ring position. On the console this is a kernel driver call. The lock is a flag, not a host mutex, because the
	// holder can sleep as an emulated thread (TCLWaitForRBSpace) and another emulated thread may then run on the same host
	// thread and try to submit too
	static std::atomic_flag s_submitLock = ATOMIC_FLAG_INIT;

	int TCLSubmitToRing(uint32be* cmd, uint32 cmdLen, betype<TCLSubmissionFlag>* controlFlags, uint64be* timestampValueOut)
	{
		while (s_submitLock.test_and_set(std::memory_order_acquire))
			coreinit::OSSleepTicks(coreinit::EspressoTime::ConvertNsToTimerTicks(20000));
		TCLSubmissionFlag flags = *controlFlags;
		cemu_assert_debug(timestampValueOut); // handle case where this is null

		// make sure there is enough space to submit all commands at one
		uint32 totalCommandLength = cmdLen;
		totalCommandLength += 6; // space needed for TCLSubmitRetireMarker

		TCLWaitForRBSpace(totalCommandLength);

		// submit command buffer
		TCLWriteCmd(cmd, cmdLen);

		// create new marker timestamp and tell GPU to write it to our variable after its done processing the command
		if ((HAS_FLAG(flags, TCLSubmissionFlag::USE_RETIRED_MARKER)))
		{
			TCLSubmitRetireMarker(!HAS_FLAG(flags, TCLSubmissionFlag::NO_MARKER_INTERRUPT));
			*timestampValueOut = s_currentRetireMarker; // incremented before each submit
		}
		else
		{
			cemu_assert_unimplemented();
		}
		s_submitLock.clear(std::memory_order_release);
		return 0;
	}

	class : public COSModule
	{
		public:
		std::string_view GetName() override
		{
			return "tcl";
		}

		void RPLUnmapped() override
		{
			s_rplData.Deallocate();
		}

		void RPLMapped() override
		{
			tclRingBufferA_readIndex = 0;
			tclRingBufferA_writeIndex = 0;
			s_rplData.Allocate();
			cafeExportRegister("TCL", TCLSubmitToRing, LogType::Placeholder);
			cafeExportRegister("TCL", TCLTimestamp, LogType::Placeholder);
			cafeExportRegister("TCL", TCLWaitTimestamp, LogType::Placeholder);
		};

		void rpl_entry(uint32 moduleHandle, coreinit::RplEntryReason reason) override
		{
			if (reason == coreinit::RplEntryReason::Loaded)
			{
				s_currentRetireMarker = 0;
				s_tclStatePPC->gpuRetireMarker = 0;
				coreinit::OSInitEvent(s_updateRetirementEvent.GetPtr(), coreinit::OSEvent::EVENT_STATE::STATE_NOT_SIGNALED, coreinit::OSEvent::EVENT_MODE::MODE_AUTO);
			}
			else if (reason == coreinit::RplEntryReason::Unloaded)
			{
				s_currentRetireMarker = 0;
				s_tclStatePPC->gpuRetireMarker = 0;
			}
		}
	}s_COStclModule;

	COSModule* GetModule()
	{
		return &s_COStclModule;
	}
}
