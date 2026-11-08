/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

#pragma once

// Performance log: one folder per game session under <user data>/perf/ with
//   info.txt     device, GPU, driver, build and the settings that affect speed
//   seconds.csv  one row per second: fps, frametime (avg, 99th percentile = 1% low, max),
//                shaders and pipelines compiled and the time spent on them, CPU and RAM
//   threads.csv  CPU use of each thread, one row per thread per second
//   frames.csv   every frame: time since the session started and frametime
// Files are flushed every second, so a killed app keeps everything up to the last second.
// Enabled by the "PerformanceLog" setting; off by default.
namespace PerfLog
{
	void SessionStart();
	void SessionEnd();
	bool IsActive();

	// once per emulated frame
	void FramePresented();
	// a shader module was created (fromCache: SPIR-V came from the precompiled cache)
	void ShaderCompiled(uint64 durationUs, bool fromCache, bool failed);
	// vkCreateGraphicsPipelines returned (renderThread: the game waited for it)
	void PipelineCompiled(uint64 durationUs, bool renderThread, bool failed);

	void SetGpuInfo(std::string_view gpu, std::string_view driver);

	// PPC recompiler, for profiling (simpleperf samples in JIT code are mapped back to PPC functions):
	//   jitmap.txt   "<host address> <host size> <ppc address> <ppc size>" per recompiled function
	//   jitfail.txt  "<ppc address> <ppc size> <stage>" per function left to the interpreter
	void JitFunction(uint32 ppcAddress, uint32 ppcSize, const void* hostCode, uint32 hostSize);
	void JitFailure(uint32 ppcAddress, uint32 ppcSize, const char* stage);

	// Input script for reproducible benchmark scenes: <user data>/perf/input_<titleid>.txt (or
	// input.txt), one press per line: "<seconds since the game started> <button> [hold ms, default 150]".
	// Buttons: A B X Y L R ZL ZR PLUS MINUS UP DOWN LEFT RIGHT, and LUP LDOWN LLEFT LRIGHT RUP RDOWN
	// RLEFT RRIGHT for the sticks. Only read while the performance log is on; the GamePad (VPAD)
	// presses the buttons that are held at the current time.
	struct ScriptedPress
	{
		double startS;
		double endS;
		std::string button;
	};
	const std::vector<ScriptedPress>& InputScript();
	double SessionSeconds();
}

// measures the scope it lives in
class PerfLogTimer
{
public:
	PerfLogTimer() : m_start(std::chrono::steady_clock::now()) {}
	uint64 ElapsedUs() const
	{
		return (uint64)std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::steady_clock::now() - m_start).count();
	}
private:
	std::chrono::steady_clock::time_point m_start;
};
