#include "Cafe/OS/RPL/COSModule.h"

namespace TCL
{
	enum class TCLTimestampId
	{
		TIMESTAMP_LAST_BUFFER_RETIRED = 1,
	};

	enum class TCLSubmissionFlag : uint32
	{
		SURFACE_SYNC = 0x400000, // submit surface sync packet before cmd
		NO_MARKER_INTERRUPT = 0x200000,
		USE_RETIRED_MARKER = 0x20000000, // Controls whether the timer is updated before or after (retired) the cmd. Also controls which timestamp is returned for the submission. Before and after using separate counters
	};

	int TCLTimestamp(TCLTimestampId id, uint64be* timestampOut);
	int TCLWaitTimestamp(TCLTimestampId id, uint64 waitTs, uint64 timeout);
	int TCLSubmitToRing(uint32be* cmd, uint32 cmdLen, betype<TCLSubmissionFlag>* controlFlags, uint64be* timestampValueOut);

	// called from Latte code
	bool TCLGPUReadRBWord(uint32& cmdWord);

	// lets the GPU thread sleep while it has no work instead of spinning:
	// take a token, check every work source, then wait with the token; any TCLGPUWake() after the token was taken ends the wait
	uint32 TCLGPUWakeToken();
	void TCLGPUWaitForWork(uint32 wakeToken, uint32 timeoutUs);
	void TCLGPUWake(); // new ring buffer data or another job for the GPU thread
	void TCLGPUNotifyNewRetirementTimestamp();

	COSModule* GetModule();
}
ENABLE_BITMASK_OPERATORS(TCL::TCLSubmissionFlag);
