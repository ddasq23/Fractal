/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

#include "Cafe/HW/Latte/Core/LattePerfLog.h"
#include "Cafe/CafeSystem.h"
#include "config/ActiveSettings.h"
#include "config/CemuConfig.h"
#include "Common/version.h"
#include "util/helpers/helpers.h"

#include <condition_variable>
#include <ctime>
#include <fstream>
#include <unordered_map>

#if BOOST_OS_LINUX || BOOST_PLAT_ANDROID
#include <dirent.h>
#include <unistd.h>
#endif
#if BOOST_PLAT_ANDROID
#include <sys/system_properties.h>
#endif

#if defined(__aarch64__)
void PPCRecompilerAArch64Gen_reportInterfaceFunctionsToPerfLog(); // BackendAArch64.cpp
#endif

namespace PerfLog
{
	using Clock = std::chrono::steady_clock;

	struct FrameSample
	{
		float tMs;  // since session start
		float ftMs; // frametime
	};

	// counters reset every second by the writer thread
	struct Counters
	{
		std::atomic<uint32> shaders{};
		std::atomic<uint32> shadersFromCache{};
		std::atomic<uint32> shadersFailed{};
		std::atomic<uint64> shaderUs{};
		std::atomic<uint32> pipelines{};
		std::atomic<uint32> pipelinesSync{};
		std::atomic<uint32> pipelinesFailed{};
		std::atomic<uint64> pipelineUs{};
		std::atomic<uint64> pipelineSyncUs{};
		std::atomic<uint64> gpuBusyNs{};
		std::atomic<uint64> renderPasses{};
		std::atomic<uint64> draws{};
		std::atomic<uint64> renderPassEnds[(size_t)RenderPassEnd::Count]{};
		std::atomic<uint64> bufferUploadsDirect{};
		std::atomic<uint64> pipelineBarriers{};
		std::atomic<uint64> gpuWaitNs{};
		std::atomic<uint64> selfDependencyKept{};
		std::atomic<uint64> selfDependencyOtherSubresource{};
		std::atomic<uint64> selfDependencyColor{}, selfDependencyDepth{};
		std::atomic<uint64> selfDependencyReasons[4]{};
		std::atomic<uint64> uploadFallbackReasons[3]{};
		std::atomic<uint64> bufferRenames{};
		std::atomic<uint64> bufferRenameBytes{};
		std::atomic<uint64> bufferRenameSkipped[4]{};
		std::atomic<uint64> imageCopiesViaBuffer{};
		std::atomic<uint64> uploadsInPrologue{};
		std::atomic<uint64> depthPasses{}, depthPassesUnused{}, depthPassesReadOnly{}, depthStoreSkipped{}, depthStoreRestarts{};
		std::atomic<uint64> renderPassRestarts[(size_t)RenderPassEnd::Count]{};
		std::atomic<uint64> textureReadbacks{};
	};
	static uint64 s_gpuLastEndNs = 0; // only touched by the render thread

	static std::atomic<bool> s_active{false};
	static Counters s_counters;
	static Clock::time_point s_sessionStart;
	static Clock::time_point s_lastFrame;
	static bool s_hasLastFrame = false;

	static std::mutex s_frameMutex;
	static std::vector<FrameSample> s_frames;

	static std::mutex s_gpuMutex;
	static std::string s_gpuName, s_gpuDriver;

	static std::thread s_writer;
	static std::mutex s_stopMutex;
	static std::condition_variable s_stopCv;
	static bool s_stop = false;

	static std::vector<ScriptedPress> s_inputScript; // set before s_active, read-only afterwards

	static std::mutex s_jitMutex;
	static std::ofstream s_jitMap, s_jitFail;

	bool IsActive()
	{
		return s_active.load(std::memory_order_relaxed);
	}

	const std::vector<ScriptedPress>& InputScript()
	{
		static const std::vector<ScriptedPress> empty;
		return IsActive() ? s_inputScript : empty;
	}

	double SessionSeconds()
	{
		return std::chrono::duration<double>(Clock::now() - s_sessionStart).count();
	}

	static std::vector<ScriptedPress> LoadInputScript(uint64 titleId)
	{
		std::vector<ScriptedPress> script;
		fs::path path = ActiveSettings::GetUserDataPath("perf/input_{:016x}.txt", titleId);
		std::error_code ec;
		if (!fs::exists(path, ec))
			path = ActiveSettings::GetUserDataPath("perf/input.txt");
		std::ifstream f(path);
		if (!f)
			return script;
		std::string line;
		while (std::getline(f, line))
		{
			if (const size_t hash = line.find('#'); hash != std::string::npos)
				line.resize(hash);
			std::istringstream ss(line);
			double t;
			std::string button;
			double holdMs = 150.0;
			if (!(ss >> t >> button))
				continue;
			ss >> holdMs;
			for (char& ch : button)
				ch = (char)toupper((unsigned char)ch);
			script.push_back({t, t + holdMs / 1000.0, button});
		}
		cemuLog_log(LogType::Force, "Performance log: input script {} ({} presses)", _pathToUtf8(path), script.size());
		return script;
	}

	void SetGpuInfo(std::string_view gpu, std::string_view driver)
	{
		std::scoped_lock lock(s_gpuMutex);
		s_gpuName = gpu;
		s_gpuDriver = driver;
	}

	void FramePresented()
	{
		if (!IsActive())
			return;
		const auto now = Clock::now();
		if (s_hasLastFrame)
		{
			FrameSample sample;
			sample.tMs = std::chrono::duration<float, std::milli>(now - s_sessionStart).count();
			sample.ftMs = std::chrono::duration<float, std::milli>(now - s_lastFrame).count();
			std::scoped_lock lock(s_frameMutex);
			s_frames.push_back(sample);
		}
		s_lastFrame = now;
		s_hasLastFrame = true;
	}

	void ShaderCompiled(uint64 durationUs, bool fromCache, bool failed)
	{
		if (!IsActive())
			return;
		s_counters.shaders++;
		if (fromCache)
			s_counters.shadersFromCache++;
		if (failed)
			s_counters.shadersFailed++;
		s_counters.shaderUs += durationUs;
	}

	void PipelineCompiled(uint64 durationUs, bool renderThread, bool failed)
	{
		if (!IsActive())
			return;
		s_counters.pipelines++;
		s_counters.pipelineUs += durationUs;
		if (renderThread)
		{
			s_counters.pipelinesSync++;
			s_counters.pipelineSyncUs += durationUs;
		}
		if (failed)
			s_counters.pipelinesFailed++;
	}

	// ---------------- system readings (Linux/Android /proc and /sys) ----------------

	static std::string ReadSmallFile(const char* path)
	{
		std::ifstream f(path);
		if (!f)
			return {};
		std::string s;
		std::getline(f, s);
		return s;
	}

	struct ThreadTicks
	{
		std::string name;
		uint64 ticks;
	};

	static std::unordered_map<int, ThreadTicks> ReadThreadTicks()
	{
		std::unordered_map<int, ThreadTicks> result;
#if BOOST_OS_LINUX || BOOST_PLAT_ANDROID
		DIR* dir = opendir("/proc/self/task");
		if (!dir)
			return result;
		while (dirent* entry = readdir(dir))
		{
			if (entry->d_name[0] < '0' || entry->d_name[0] > '9')
				continue;
			const int tid = atoi(entry->d_name);
			std::string stat = ReadSmallFile(fmt::format("/proc/self/task/{}/stat", tid).c_str());
			// "tid (comm) state ..." -- comm can hold spaces and parentheses, so cut at the last ')'
			const size_t open = stat.find('(');
			const size_t close = stat.rfind(')');
			if (open == std::string::npos || close == std::string::npos || close < open)
				continue;
			ThreadTicks t;
			t.name = stat.substr(open + 1, close - open - 1);
			// after ") ": field 3 (state) ... utime is field 14, stime field 15
			std::istringstream rest(stat.substr(close + 2));
			std::string field;
			uint64 utime = 0, stime = 0;
			for (int i = 3; i <= 15 && rest >> field; i++)
			{
				if (i == 14)
					utime = std::strtoull(field.c_str(), nullptr, 10);
				else if (i == 15)
					stime = std::strtoull(field.c_str(), nullptr, 10);
			}
			t.ticks = utime + stime;
			result.emplace(tid, std::move(t));
		}
		closedir(dir);
#endif
		return result;
	}

	static uint64 ReadRssMb()
	{
#if BOOST_OS_LINUX || BOOST_PLAT_ANDROID
		std::string statm = ReadSmallFile("/proc/self/statm");
		std::istringstream ss(statm);
		uint64 size = 0, resident = 0;
		ss >> size >> resident;
		return resident * (uint64)sysconf(_SC_PAGESIZE) / (1024 * 1024);
#else
		return 0;
#endif
	}

	static int CpuCount()
	{
#if BOOST_OS_LINUX || BOOST_PLAT_ANDROID
		return (int)sysconf(_SC_NPROCESSORS_CONF);
#else
		return 0;
#endif
	}

	// current clock of each CPU in MHz; empty when offline or not readable
	static std::string ReadCpuFreqs(int cpuCount)
	{
		std::string out;
		for (int i = 0; i < cpuCount; i++)
		{
			std::string v = ReadSmallFile(fmt::format("/sys/devices/system/cpu/cpu{}/cpufreq/scaling_cur_freq", i).c_str());
			if (i > 0)
				out += ',';
			if (!v.empty())
				out += fmt::format("{}", std::strtoull(v.c_str(), nullptr, 10) / 1000);
		}
		return out;
	}

	static std::string AndroidProp(const char* name)
	{
#if BOOST_PLAT_ANDROID
		char value[PROP_VALUE_MAX] = {};
		__system_property_get(name, value);
		return value;
#else
		return {};
#endif
	}

	static std::string CsvText(std::string s)
	{
		for (char& c : s)
			if (c == ',' || c == '\n' || c == '\r' || c == '"')
				c = ' ';
		return s;
	}

	// ---------------- writer ----------------

	static void WriteInfo(const fs::path& dir)
	{
		std::ofstream f(dir / "info.txt", std::ios::trunc);
		const auto& config = GetConfig();
		std::string gpu, driver;
		{
			std::scoped_lock lock(s_gpuMutex);
			gpu = s_gpuName;
			driver = s_gpuDriver;
		}
		f << "build: " << BUILD_VERSION_WITH_NAME_STRING << "\n";
		f << "title_id: " << fmt::format("{:016x}", CafeSystem::GetForegroundTitleId()) << "\n";
		f << "title: " << CafeSystem::GetForegroundTitleName() << "\n";
#if BOOST_PLAT_ANDROID
		f << "device: " << AndroidProp("ro.product.manufacturer") << " " << AndroidProp("ro.product.model") << "\n";
		f << "soc: " << AndroidProp("ro.soc.manufacturer") << " " << AndroidProp("ro.soc.model") << " (" << AndroidProp("ro.board.platform") << ")\n";
		f << "android: " << AndroidProp("ro.build.version.release") << "\n";
#endif
		f << "cpus: " << CpuCount() << "\n";
		f << "gpu: " << gpu << "\n";
		f << "driver: " << driver << "\n";
		f << "custom_driver: " << (config.custom_driver_path.GetValue().empty() ? "none" : config.custom_driver_path.GetValue()) << "\n";
		f << "async_compile: " << (config.async_compile.GetValue() ? 1 : 0) << "\n";
		f << "vsync: " << config.vsync.GetValue() << "\n";
		f << "accurate_barriers: " << (config.vk_accurate_barriers.GetValue() ? 1 : 0) << "\n";
		f << "cpu_mode: " << (int)config.cpu_mode.GetValue() << "\n";
		f << "upscale_filter: " << config.upscale_filter.GetValue() << "\n";
	}

	static void WriterThread(fs::path dir)
	{
		SetThreadName("PerfLog");
		std::ofstream seconds(dir / "seconds.csv", std::ios::trunc);
		std::ofstream threads(dir / "threads.csv", std::ios::trunc);
		std::ofstream frames(dir / "frames.csv", std::ios::trunc);
		const int cpuCount = CpuCount();
		std::string freqHeader;
		for (int i = 0; i < cpuCount; i++)
			freqHeader += fmt::format(",cpu{}_mhz", i);
		seconds << "t_s,frames,fps,ft_avg_ms,ft_p99_ms,low1_fps,ft_max_ms,"
				   "shaders,shaders_cached,shaders_failed,shader_ms,"
				   "pipelines,pipelines_sync,pipelines_failed,pipeline_ms,pipeline_sync_ms,gpu_busy_ms,render_passes,draws,"
				   "rpe_other,rpe_fbo,rpe_buffer_upload,rpe_buffer_copy,rpe_texture_upload,rpe_clear,rpe_copy,rpe_readback,rpe_query,rpe_submit,rpe_self_dependency,buffer_uploads_direct,barriers,gpu_wait_ms,readbacks,selfdep_kept,selfdep_other_subresource,selfdep_color,selfdep_depth,selfdep_why_drawwrites,selfdep_why_passwrote,selfdep_why_otherbarrier,selfdep_why_vertex,upload_fb_blocked,upload_fb_current,upload_fb_inflight,buffer_renames,buffer_rename_kb,rename_skip_streamout,rename_skip_size,rename_skip_heap,rename_skip_busy,copies_via_buffer,uploads_prologue,depth_passes,depth_unused,depth_readonly,depth_store_skipped,depth_store_restarts,"
				   "rs_other,rs_fbo,rs_buffer_upload,rs_buffer_copy,rs_texture_upload,rs_clear,rs_copy,rs_readback,rs_query,rs_submit,rs_self_dependency,"
				   "cpu_process_pct,rss_mb" << freqHeader << "\n";
		threads << "t_s,tid,name,cpu_pct\n";
		frames << "t_ms,ft_ms\n";

#if BOOST_OS_LINUX || BOOST_PLAT_ANDROID
		const double ticksPerSecond = (double)sysconf(_SC_CLK_TCK);
#else
		const double ticksPerSecond = 100.0;
#endif
		auto prevTicks = ReadThreadTicks();
		auto prevTime = Clock::now();
		bool infoWritten = false;
		std::vector<FrameSample> batch;
		std::vector<float> sorted;

		while (true)
		{
			{
				std::unique_lock lock(s_stopMutex);
				if (s_stopCv.wait_for(lock, std::chrono::seconds(1), [] { return s_stop; }))
					break;
			}
			const auto now = Clock::now();
			const double dt = std::chrono::duration<double>(now - prevTime).count();
			prevTime = now;
			const double tS = std::chrono::duration<double>(now - s_sessionStart).count();

			if (!infoWritten)
			{
				// the renderer reports the GPU after the session starts
				WriteInfo(dir);
				infoWritten = true;
			}

			batch.clear();
			{
				std::scoped_lock lock(s_frameMutex);
				batch.swap(s_frames);
			}
			sorted.clear();
			double ftSum = 0.0;
			float ftMax = 0.0f;
			for (const auto& s : batch)
			{
				frames << fmt::format("{:.1f},{:.3f}\n", s.tMs, s.ftMs);
				sorted.push_back(s.ftMs);
				ftSum += s.ftMs;
				ftMax = std::max(ftMax, s.ftMs);
			}
			std::sort(sorted.begin(), sorted.end());
			const size_t n = sorted.size();
			const double fps = dt > 0.0 ? (double)n / dt : 0.0;
			const double ftAvg = n ? ftSum / (double)n : 0.0;
			// 99th percentile frametime; "1% low" fps is its inverse
			const double ftP99 = n ? sorted[std::min(n - 1, (size_t)std::ceil(0.99 * (double)n) - 1)] : 0.0;
			const double low1 = ftP99 > 0.0 ? 1000.0 / ftP99 : 0.0;

			const uint32 shaders = s_counters.shaders.exchange(0);
			const uint32 shadersCached = s_counters.shadersFromCache.exchange(0);
			const uint32 shadersFailed = s_counters.shadersFailed.exchange(0);
			const uint64 shaderUs = s_counters.shaderUs.exchange(0);
			const uint32 pipelines = s_counters.pipelines.exchange(0);
			const uint32 pipelinesSync = s_counters.pipelinesSync.exchange(0);
			const uint32 pipelinesFailed = s_counters.pipelinesFailed.exchange(0);
			const uint64 pipelineUs = s_counters.pipelineUs.exchange(0);
			const uint64 pipelineSyncUs = s_counters.pipelineSyncUs.exchange(0);

			auto ticks = ReadThreadTicks();
			double processPct = 0.0;
			for (const auto& [tid, t] : ticks)
			{
				auto it = prevTicks.find(tid);
				const uint64 before = it != prevTicks.end() ? it->second.ticks : 0;
				if (t.ticks < before)
					continue;
				const double pct = (double)(t.ticks - before) / ticksPerSecond / dt * 100.0;
				processPct += pct;
				if (pct >= 0.5)
					threads << fmt::format("{:.0f},{},{},{:.1f}\n", tS, tid, CsvText(t.name), pct);
			}
			prevTicks = std::move(ticks);

			std::string freqs = ReadCpuFreqs(cpuCount);
			const uint64 gpuBusyNs = s_counters.gpuBusyNs.exchange(0);
			const uint64 renderPasses = s_counters.renderPasses.exchange(0);
			const uint64 draws = s_counters.draws.exchange(0);
			std::string passEnds;
			for (auto& c : s_counters.renderPassEnds)
				passEnds += fmt::format("{},", c.exchange(0));
			passEnds += fmt::format("{},", s_counters.bufferUploadsDirect.exchange(0));
			passEnds += fmt::format("{},", s_counters.pipelineBarriers.exchange(0));
			passEnds += fmt::format("{:.1f},", s_counters.gpuWaitNs.exchange(0) / 1e6);
			passEnds += fmt::format("{},", s_counters.textureReadbacks.exchange(0));
			passEnds += fmt::format("{},", s_counters.selfDependencyKept.exchange(0));
			passEnds += fmt::format("{},", s_counters.selfDependencyOtherSubresource.exchange(0));
			passEnds += fmt::format("{},{},", s_counters.selfDependencyColor.exchange(0), s_counters.selfDependencyDepth.exchange(0));
			for (auto& c : s_counters.selfDependencyReasons)
				passEnds += fmt::format("{},", c.exchange(0));
			for (auto& c : s_counters.uploadFallbackReasons)
				passEnds += fmt::format("{},", c.exchange(0));
			passEnds += fmt::format("{},{},", s_counters.bufferRenames.exchange(0), s_counters.bufferRenameBytes.exchange(0) / 1024);
			for (auto& c : s_counters.bufferRenameSkipped)
				passEnds += fmt::format("{},", c.exchange(0));
			passEnds += fmt::format("{},", s_counters.imageCopiesViaBuffer.exchange(0));
			passEnds += fmt::format("{},", s_counters.uploadsInPrologue.exchange(0));
			passEnds += fmt::format("{},{},{},", s_counters.depthPasses.exchange(0), s_counters.depthPassesUnused.exchange(0), s_counters.depthPassesReadOnly.exchange(0));
			passEnds += fmt::format("{},{},", s_counters.depthStoreSkipped.exchange(0), s_counters.depthStoreRestarts.exchange(0));
			for (auto& c : s_counters.renderPassRestarts)
				passEnds += fmt::format("{},", c.exchange(0));
			seconds << fmt::format("{:.0f},{},{:.2f},{:.2f},{:.2f},{:.2f},{:.2f},{},{},{},{:.1f},{},{},{},{:.1f},{:.1f},{:.1f},{},{},{}{:.1f},{}{}{}\n",
								   tS, n, fps, ftAvg, ftP99, low1, ftMax,
								   shaders, shadersCached, shadersFailed, shaderUs / 1000.0,
								   pipelines, pipelinesSync, pipelinesFailed, pipelineUs / 1000.0, pipelineSyncUs / 1000.0, gpuBusyNs / 1e6, renderPasses, draws, passEnds,
								   processPct, ReadRssMb(), cpuCount ? "," : "", freqs);
			seconds.flush();
			threads.flush();
			frames.flush();
		}
	}

	void SessionStart()
	{
		if (IsActive() || !GetConfig().perf_log.GetValue())
			return;
		const std::time_t now = std::time(nullptr);
		std::tm tm{};
#if BOOST_OS_WINDOWS
		localtime_s(&tm, &now);
#else
		localtime_r(&now, &tm);
#endif
		char stamp[32];
		std::strftime(stamp, sizeof(stamp), "%Y%m%d-%H%M%S", &tm);
		const fs::path dir = ActiveSettings::GetUserDataPath("perf") / fmt::format("{}_{:016x}", stamp, CafeSystem::GetForegroundTitleId());
		std::error_code ec;
		fs::create_directories(dir, ec);
		if (ec)
		{
			cemuLog_log(LogType::Force, "Performance log: cannot create {}: {}", _pathToUtf8(dir), ec.message());
			return;
		}
		cemuLog_log(LogType::Force, "Performance log: {}", _pathToUtf8(dir));
		s_counters.shaders = 0;
		s_counters.shadersFromCache = 0;
		s_counters.shadersFailed = 0;
		s_counters.shaderUs = 0;
		s_counters.pipelines = 0;
		s_counters.pipelinesSync = 0;
		s_counters.pipelinesFailed = 0;
		s_counters.pipelineUs = 0;
		s_counters.pipelineSyncUs = 0;
		s_counters.gpuBusyNs = 0;
		s_counters.renderPasses = 0;
		s_counters.draws = 0;
		for (auto& c : s_counters.renderPassEnds)
			c = 0;
		s_counters.bufferUploadsDirect = 0;
		s_counters.pipelineBarriers = 0;
		s_counters.gpuWaitNs = 0;
		s_counters.textureReadbacks = 0;
		s_counters.selfDependencyKept = 0;
		s_counters.selfDependencyOtherSubresource = 0;
		s_counters.selfDependencyColor = 0;
		s_counters.selfDependencyDepth = 0;
		for (auto& c : s_counters.selfDependencyReasons)
			c = 0;
		for (auto& c : s_counters.uploadFallbackReasons)
			c = 0;
		s_counters.bufferRenames = 0;
		s_counters.bufferRenameBytes = 0;
		for (auto& c : s_counters.bufferRenameSkipped)
			c = 0;
		s_counters.imageCopiesViaBuffer = 0;
		s_counters.uploadsInPrologue = 0;
		s_counters.depthPasses = 0;
		s_counters.depthPassesUnused = 0;
		s_counters.depthPassesReadOnly = 0;
		s_counters.depthStoreSkipped = 0;
		s_counters.depthStoreRestarts = 0;
		for (auto& c : s_counters.renderPassRestarts)
			c = 0;
		s_gpuLastEndNs = 0;
		{
			std::scoped_lock lock(s_frameMutex);
			s_frames.clear();
		}
		s_inputScript = LoadInputScript(CafeSystem::GetForegroundTitleId());
		{
			std::scoped_lock lock(s_jitMutex);
			s_jitMap.open(dir / "jitmap.txt", std::ios::trunc);
			s_jitFail.open(dir / "jitfail.txt", std::ios::trunc);
		}
#if defined(__aarch64__)
		PPCRecompilerAArch64Gen_reportInterfaceFunctionsToPerfLog();
#endif
		s_sessionStart = Clock::now();
		s_hasLastFrame = false;
		s_stop = false;
		s_active = true;
		s_writer = std::thread(WriterThread, dir);
	}

	void SessionEnd()
	{
		if (!IsActive())
			return;
		s_active = false;
		{
			std::scoped_lock lock(s_stopMutex);
			s_stop = true;
		}
		s_stopCv.notify_all();
		if (s_writer.joinable())
			s_writer.join();
		std::scoped_lock lock(s_jitMutex);
		s_jitMap.close();
		s_jitFail.close();
	}

	void RenderPassBegin()
	{
		if (IsActive())
			s_counters.renderPasses.fetch_add(1, std::memory_order_relaxed);
	}

	sint64 Tunable(std::string_view key, sint64 defaultValue)
	{
		static std::once_flag s_once;
		static std::unordered_map<std::string, sint64> s_values;
		std::call_once(s_once, [] {
			std::ifstream file(ActiveSettings::GetUserDataPath("perf/tuning.txt"));
			std::string line;
			while (std::getline(file, line))
			{
				const size_t eq = line.find('=');
				if (eq == std::string::npos || line.empty() || line[0] == '#')
					continue;
				try
				{
					s_values[line.substr(0, eq)] = std::stoll(line.substr(eq + 1));
				}
				catch (...)
				{
				}
			}
			for (auto& [k, v] : s_values)
				cemuLog_log(LogType::Force, "PerfLog: tuning {}={}", k, v);
		});
		auto it = s_values.find(std::string(key));
		return it != s_values.end() ? it->second : defaultValue;
	}

	void RenderPassEnded(RenderPassEnd reason)
	{
		if (IsActive())
			s_counters.renderPassEnds[(size_t)reason].fetch_add(1, std::memory_order_relaxed);
	}

	void RenderPassRestarted(RenderPassEnd previousEnd)
	{
		if (IsActive())
			s_counters.renderPassRestarts[(size_t)previousEnd].fetch_add(1, std::memory_order_relaxed);
	}

	void BufferUploadDirect()
	{
		if (IsActive())
			s_counters.bufferUploadsDirect.fetch_add(1, std::memory_order_relaxed);
	}

	void GpuWait(uint64 ns)
	{
		if (IsActive())
			s_counters.gpuWaitNs.fetch_add(ns, std::memory_order_relaxed);
	}

	void TextureReadbackScheduled()
	{
		if (IsActive())
			s_counters.textureReadbacks.fetch_add(1, std::memory_order_relaxed);
	}

	void SelfDependencyOtherSubresource()
	{
		if (IsActive())
			s_counters.selfDependencyOtherSubresource.fetch_add(1, std::memory_order_relaxed);
	}

	void SelfDependencyAspects(bool color, bool depth)
	{
		if (!IsActive())
			return;
		if (color)
			s_counters.selfDependencyColor.fetch_add(1, std::memory_order_relaxed);
		if (depth)
			s_counters.selfDependencyDepth.fetch_add(1, std::memory_order_relaxed);
	}

	void SelfDependencyReason(int reason)
	{
		if (IsActive() && reason >= 0 && reason < 4)
			s_counters.selfDependencyReasons[reason].fetch_add(1, std::memory_order_relaxed);
	}

	void ImageCopyViaBuffer()
	{
		if (IsActive())
			s_counters.imageCopiesViaBuffer.fetch_add(1, std::memory_order_relaxed);
	}

	void DepthAttachmentUse(bool tested, bool written)
	{
		if (!IsActive())
			return;
		s_counters.depthPasses.fetch_add(1, std::memory_order_relaxed);
		if (!tested && !written)
			s_counters.depthPassesUnused.fetch_add(1, std::memory_order_relaxed);
		else if (!written)
			s_counters.depthPassesReadOnly.fetch_add(1, std::memory_order_relaxed);
	}

	void DepthStoreSkipped(bool restarted)
	{
		if (IsActive())
			(restarted ? s_counters.depthStoreRestarts : s_counters.depthStoreSkipped).fetch_add(1, std::memory_order_relaxed);
	}

	void TextureUploadInPrologue()
	{
		if (IsActive())
			s_counters.uploadsInPrologue.fetch_add(1, std::memory_order_relaxed);
	}

	void BufferCacheRename(uint32 bytes)
	{
		if (!IsActive())
			return;
		s_counters.bufferRenames.fetch_add(1, std::memory_order_relaxed);
		s_counters.bufferRenameBytes.fetch_add(bytes, std::memory_order_relaxed);
	}

	void BufferCacheRenameSkipped(int reason)
	{
		if (IsActive() && reason >= 0 && reason < 4)
			s_counters.bufferRenameSkipped[reason].fetch_add(1, std::memory_order_relaxed);
	}

	void BufferUploadFallback(int reason)
	{
		if (IsActive() && reason >= 0 && reason < 3)
			s_counters.uploadFallbackReasons[reason].fetch_add(1, std::memory_order_relaxed);
	}

	void SelfDependencyKept()
	{
		if (IsActive())
			s_counters.selfDependencyKept.fetch_add(1, std::memory_order_relaxed);
	}

	void PipelineBarrier()
	{
		if (IsActive())
			s_counters.pipelineBarriers.fetch_add(1, std::memory_order_relaxed);
	}

	void DrawCall()
	{
		if (IsActive())
			s_counters.draws.fetch_add(1, std::memory_order_relaxed);
	}

	void GpuWork(uint64 startNs, uint64 endNs)
	{
		if (!IsActive())
			return;
		constexpr uint64 kSecondNs = 1000000000ull;
		if (endNs - startNs > kSecondNs)
			return; // not a real command buffer duration (bad timestamp)
		if (s_gpuLastEndNs > endNs + kSecondNs)
			s_gpuLastEndNs = 0; // the GPU clock went backwards (or an earlier timestamp was bogus)
		const uint64 from = std::max(startNs, s_gpuLastEndNs);
		if (endNs > from)
			s_counters.gpuBusyNs += endNs - from;
		s_gpuLastEndNs = std::max(s_gpuLastEndNs, endNs);
	}

	void JitFunction(uint32 ppcAddress, uint32 ppcSize, const void* hostCode, uint32 hostSize)
	{
		if (!IsActive())
			return;
		std::scoped_lock lock(s_jitMutex);
		if (!s_jitMap.is_open())
			return;
		s_jitMap << fmt::format("{:x} {:x} {:08x} {}\n", (uint64)(uintptr_t)hostCode, hostSize, ppcAddress, ppcSize);
		s_jitMap.flush();
	}

	void JitGlue(const void* hostCode, uint32 hostSize, const char* name)
	{
		std::scoped_lock lock(s_jitMutex);
		if (!s_jitMap.is_open())
			return;
		s_jitMap << fmt::format("{:x} {:x} 0 {}\n", (uint64)(uintptr_t)hostCode, hostSize, name);
		s_jitMap.flush();
	}

	void JitFailure(uint32 ppcAddress, uint32 ppcSize, const char* stage)
	{
		if (!IsActive())
			return;
		std::scoped_lock lock(s_jitMutex);
		if (!s_jitFail.is_open())
			return;
		s_jitFail << fmt::format("{:08x} {} {}\n", ppcAddress, ppcSize, stage);
		s_jitFail.flush();
	}
}
