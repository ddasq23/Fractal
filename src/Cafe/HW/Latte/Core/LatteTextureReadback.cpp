#include "Cafe/HW/Latte/Core/Latte.h"
#include "Cafe/HW/Latte/Core/LattePerformanceMonitor.h"
#include "Cafe/HW/Latte/Renderer/Renderer.h"
#include "Cafe/HW/Latte/Core/LatteTexture.h"
#include "Cafe/HW/Latte/Core/LattePerfLog.h"
#include <unordered_map>
#include "config/ActiveSettings.h"
#include "Common/FileStream.h"
#include <chrono>

#define LOG_READBACK_TIME

struct LatteTextureReadbackQueueEntry
{
	HRTick initiateTime;
	uint32 lastUpdateDrawcallIndex;
	LatteTextureView* textureView;
};

std::vector<LatteTextureReadbackQueueEntry> sTextureScheduledReadbacks; // readbacks that have been queued but the actual transfer has not yet been started
std::queue<LatteTextureReadbackInfo*> sTextureActiveReadbackQueue; // readbacks in flight

void LatteTextureReadback_StartTransfer(LatteTextureView* textureView)
{
	cemuLog_log(LogType::TextureReadback, "[TextureReadback-Start] PhysAddr {:08x} Res {}x{} Fmt {} Slice {} Mip {}", textureView->baseTexture->physAddress, textureView->baseTexture->width, textureView->baseTexture->height, textureView->baseTexture->format, textureView->firstSlice, textureView->firstMip);
	HRTick currentTick = HighResolutionTimer().now().getTick();
	// create info entry and store in ordered linked list
	LatteTextureReadbackInfo* readbackInfo = g_renderer->texture_createReadback(textureView);
	if (!readbackInfo)
		return;
	sTextureActiveReadbackQueue.push(readbackInfo);
	readbackInfo->StartTransfer();
	readbackInfo->transferStartTime = currentTick;
}

/*
 * Checks for queued transfers and starts them if at least five drawcalls have passed since the last write
 * Called after a draw sequence is completed
 * Returns true if at least one transfer was started
 */
bool LatteTextureReadback_Update(bool forceStart)
{
	bool hasStartedTransfer = false;
	for (size_t i = 0; i < sTextureScheduledReadbacks.size(); i++)
	{
		LatteTextureReadbackQueueEntry& entry = sTextureScheduledReadbacks[i];
		uint32 numElapsedDrawcalls = LatteGPUState.drawCallCounter - entry.lastUpdateDrawcallIndex;
		if (forceStart || numElapsedDrawcalls >= 5)
		{
#ifdef LOG_READBACK_TIME
			double elapsedSecondsSinceInitiate = HighResolutionTimer::getTimeDiff(entry.initiateTime, HighResolutionTimer().now().getTick());
			cemuLog_log(LogType::TextureReadback, "[TextureReadback-Update] Starting transfer for {:08x} after {} elapsed drawcalls. Time since initiate: {:.4} Force-start: {}", entry.textureView->baseTexture->physAddress, numElapsedDrawcalls, elapsedSecondsSinceInitiate, forceStart?"yes":"no");
#endif
			LatteTextureReadback_StartTransfer(entry.textureView);
			// remove element
			vectorRemoveByIndex(sTextureScheduledReadbacks, i);
			i--;
			hasStartedTransfer = true;
		}
	}
	return hasStartedTransfer;
}

/*
 * Called when a texture is deleted
 */
void LatteTextureReadback_NotifyTextureDeletion(LatteTexture* texture)
{
	// delete from queue
	for (size_t i = 0; i < sTextureScheduledReadbacks.size(); i++)
	{
		LatteTextureReadbackQueueEntry& entry = sTextureScheduledReadbacks[i];
		if (entry.textureView->baseTexture == texture)
		{
			vectorRemoveByIndex(sTextureScheduledReadbacks, i);
			break;
		}
	}
}

void LatteTextureReadback_Initate(LatteTextureView* textureView)
{
	// currently we don't support readback for resized textures
	if (textureView->baseTexture->overwriteInfo.hasResolutionOverwrite)
	{
		cemuLog_log(LogType::Force, "Texture readback is not supported for textures with modified resolution. Texture: {:08x} {}x{}", textureView->baseTexture->physAddress, textureView->baseTexture->width, textureView->baseTexture->height);
		return;
	}
	// check if texture isn't already queued for transfer
	for (size_t i = 0; i < sTextureScheduledReadbacks.size(); i++)
	{
		LatteTextureReadbackQueueEntry& entry = sTextureScheduledReadbacks[i];
		if (entry.textureView == textureView)
		{
			entry.lastUpdateDrawcallIndex = LatteGPUState.drawCallCounter;
			return;
		}
	}
	// queue
	PerfLog::TextureReadbackScheduled();
	LatteTextureReadbackQueueEntry queueEntry;
	queueEntry.initiateTime = HighResolutionTimer().now().getTick();
	queueEntry.textureView = textureView;
	queueEntry.lastUpdateDrawcallIndex = LatteGPUState.drawCallCounter;
	sTextureScheduledReadbacks.emplace_back(queueEntry);
}

// Fractal diagnostics (debug session, always on): name the first readback of each texture, and report float data with NaN or
// Inf, which the game's CPU code then turns into its own state (an exposure computed from a luminance readback that turns NaN
// keeps the whole frame white or black). Readbacks are rare, so scanning them costs nothing
static void LatteTextureReadback_CheckData(LatteTextureReadbackInfo* info, const uint8* data)
{
	const auto& tex = info->hostTextureCopy;
	const uint32 fmt = (uint32)tex.format;
	PerfLog::Event(fmt::format("readback {:08x}", tex.physAddress), fmt::format("texture {}x{} GX2 {:04x} mip {} slice {} read back to memory",
		tex.width, tex.height, fmt, info->m_firstMip, info->m_firstSlice));
	// perf/tuning.txt readback_stats=1: once per second per texture, the byte statistics of what was read back (games that
	// cull on the CPU from a GPU visibility buffer hide everything when it reads back as all occluded)
	static const bool s_stats = PerfLog::Tunable("readback_stats", 0) != 0;
	if (s_stats && data)
	{
		static std::unordered_map<uint32, std::chrono::steady_clock::time_point> s_lastLog;
		auto now = std::chrono::steady_clock::now();
		auto& last = s_lastLog[tex.physAddress];
		if (now - last >= std::chrono::seconds(1))
		{
			last = now;
			const size_t n = (size_t)info->m_rowPitch * std::max<uint32>(tex.height >> info->m_firstMip, 1);
			uint64 sum = 0;
			size_t zeros = 0, ones = 0;
			for (size_t i = 0; i < n; i++)
			{
				sum += data[i];
				zeros += data[i] == 0x00;
				ones += data[i] == 0xFF;
			}
			uint64 hash = 0xcbf29ce484222325ull;
			for (size_t i = 0; i < n; i += 64)
				hash = (hash ^ data[i]) * 0x100000001b3ull;
			cemuLog_log(LogType::Force, "readback {:08x} {}x{} {:04x}: {} bytes mean {:.1f} 00:{:.1f}% FF:{:.1f}% hash {:08x}", tex.physAddress, tex.width,
				tex.height, fmt, n, n ? (double)sum / n : 0.0, n ? 100.0 * zeros / n : 0.0, n ? 100.0 * ones / n : 0.0, (uint32)hash);
		}
	}
	// perf/tuning.txt readback_dump=1: every 2 s per texture, the raw data (rows of m_rowPitch bytes) to perf/rb/
	static const bool s_dump = PerfLog::Tunable("readback_dump", 0) != 0;
	if (s_dump && data)
	{
		static std::unordered_map<uint32, std::chrono::steady_clock::time_point> s_lastDump;
		auto now = std::chrono::steady_clock::now();
		auto& last = s_lastDump[tex.physAddress];
		if (now - last >= std::chrono::seconds(2))
		{
			last = now;
			const uint32 h = std::max<uint32>(tex.height >> info->m_firstMip, 1);
			const auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
			std::error_code ec;
			fs::create_directories(ActiveSettings::GetUserDataPath("perf/rb"), ec);
			FileStream* fs = FileStream::createFile2(ActiveSettings::GetUserDataPath("perf/rb/{:08x}_{}_{}x{}_{:04x}_p{}.bin", tex.physAddress, ms, tex.width, h, fmt, info->m_rowPitch));
			if (fs)
			{
				fs->writeData(data, (size_t)info->m_rowPitch * h);
				delete fs;
			}
		}
	}
	if (!data || (fmt & 0x800) == 0)
		return;
	const uint32 hw = fmt & 0x3F;
	const bool half = hw == 0x06 || hw == 0x10 || hw == 0x20;
	const bool single = hw == 0x0E || hw == 0x1E || hw == 0x23;
	if (!half && !single)
		return;
	const uint32 height = std::max<uint32>(tex.height >> info->m_firstMip, 1);
	const size_t size = (size_t)info->m_rowPitch * height;
	uint32 bad = 0;
	if (half)
	{
		for (size_t i = 0; i + 1 < size; i += 2)
			if (((*(const uint16*)(data + i)) & 0x7C00) == 0x7C00)
				bad++;
	}
	else
	{
		for (size_t i = 0; i + 3 < size; i += 4)
			if (((*(const uint32*)(data + i)) & 0x7F800000) == 0x7F800000)
				bad++;
	}
	if (bad)
		PerfLog::Event(fmt::format("readback_nan {:08x}", tex.physAddress), fmt::format("texture {}x{} GX2 {:04x} read back with {} NaN/Inf values",
			tex.width, tex.height, fmt, bad));
}

void LatteTextureReadback_UpdateFinishedTransfers(bool forceFinish)
{
	if (forceFinish)
	{
		// start any delayed transfers
		LatteTextureReadback_Update(true);
	}
	performanceMonitor.gpuTime_waitForAsync.beginMeasuring();
	while (!sTextureActiveReadbackQueue.empty())
	{
		LatteTextureReadbackInfo* readbackInfo = sTextureActiveReadbackQueue.front();
		if (forceFinish)
		{
			if (!readbackInfo->IsFinished())
			{
				readbackInfo->waitStartTime = HighResolutionTimer().now().getTick();
#ifdef LOG_READBACK_TIME
				if (cemuLog_isLoggingEnabled(LogType::TextureReadback))
				{
					double elapsedSecondsTransfer = HighResolutionTimer::getTimeDiff(readbackInfo->transferStartTime, HighResolutionTimer().now().getTick());
					cemuLog_log(LogType::TextureReadback, "[Texture-Readback] Force-finish: {:08x} Res {:}/{:} TM {:} FMT {:04x} Transfer time so far: {:.4}ms", readbackInfo->hostTextureCopy.physAddress, readbackInfo->hostTextureCopy.width, readbackInfo->hostTextureCopy.height, readbackInfo->hostTextureCopy.tileMode, (uint32)readbackInfo->hostTextureCopy.format, elapsedSecondsTransfer * 1000.0);
				}
#endif
				readbackInfo->forceFinish = true;
				readbackInfo->ForceFinish();
				// rerun logic since ->ForceFinish() can recurively call this function and thus modify the queue
				continue;
			}
		}
		else
		{
			if (!readbackInfo->IsFinished())
				break;
			readbackInfo->waitStartTime = HighResolutionTimer().now().getTick();
		}
		// performance testing
#ifdef LOG_READBACK_TIME
		if (cemuLog_isLoggingEnabled(LogType::TextureReadback))
		{
			HRTick currentTick = HighResolutionTimer().now().getTick();
			double elapsedSecondsTransfer = HighResolutionTimer::getTimeDiff(readbackInfo->transferStartTime, currentTick);
			double elapsedSecondsWaiting = HighResolutionTimer::getTimeDiff(readbackInfo->waitStartTime, currentTick);
			cemuLog_log(LogType::TextureReadback, "[Texture-Readback] {:08x} Res {}/{} TM {} FMT {:04x} ReadbackLatency: {:6.3}ms WaitTime: {:6.3}ms ForcedWait {}", readbackInfo->hostTextureCopy.physAddress, readbackInfo->hostTextureCopy.width, readbackInfo->hostTextureCopy.height, readbackInfo->hostTextureCopy.tileMode, (uint32)readbackInfo->hostTextureCopy.format, elapsedSecondsTransfer * 1000.0, elapsedSecondsWaiting * 1000.0, readbackInfo->forceFinish ? "yes" : "no");
		}
#endif
		uint8* pixelData = readbackInfo->GetData();
		LatteTextureReadback_CheckData(readbackInfo, pixelData);
		LatteTextureLoader_writeReadbackTextureToMemory(&readbackInfo->hostTextureCopy, readbackInfo->m_firstSlice, readbackInfo->m_firstMip, pixelData, readbackInfo->m_rowPitch);
		readbackInfo->ReleaseData();
		// get the original texture if it still exists and invalidate the current data hash
		LatteTextureView* origTexView = LatteTextureViewLookupCache::lookupSlice(readbackInfo->hostTextureCopy.physAddress, readbackInfo->hostTextureCopy.width, readbackInfo->hostTextureCopy.height, readbackInfo->hostTextureCopy.pitch, 0, 0, readbackInfo->hostTextureCopy.format);
		if (origTexView)
			LatteTC_ResetTextureChangeTracker(origTexView->baseTexture, true);
		delete readbackInfo;
		// remove from queue
		cemu_assert_debug(!sTextureActiveReadbackQueue.empty());
		cemu_assert_debug(readbackInfo == sTextureActiveReadbackQueue.front());
		sTextureActiveReadbackQueue.pop();
	}
	performanceMonitor.gpuTime_waitForAsync.endMeasuring();
}

bool LatteTextureReadback_ReadbackToLinearBlocking(LatteTextureView* sourceView, uint8* dstPtr, uint32 dstWidth, uint32 dstHeight, uint32 dstPitch)
{
	LatteTextureReadbackInfo* info = g_renderer->texture_createReadback(sourceView);
	if (!info)
		return false;

	info->StartTransfer();
	info->ForceFinish();
	cemu_assert(info->IsFinished());

	uint8* data = info->GetData(); // returned pixel format should match Latte format
	uint32 bpp = Latte::GetFormatBits(sourceView->baseTexture->format) / 8;
	uint32 srcRowBytes = info->m_rowPitch;
	uint32 dstRowBytes = dstWidth * bpp;
	for (uint32 y = 0; y < dstHeight; y++)
		memcpy(dstPtr + y * dstPitch * bpp, data + y * srcRowBytes, dstRowBytes);

	info->ReleaseData();
	delete info;
	return true;
}

uint32 LatteTextureReadbackInfo::GetReadbackRowPitch(LatteTextureView* textureView, uint32 rowAlignment)
{
	uint32 width = textureView->baseTexture->GetMipWidth(textureView->firstMip);
	if (Latte::IsCompressedFormat(textureView->format))
		width = (width + 3) / 4;
	uint32 rowPitch = width * (Latte::GetFormatBits(textureView->format) / 8);
	return (rowPitch + rowAlignment - 1) & ~(rowAlignment - 1);
}

uint32 LatteTextureReadbackInfo::GetReadbackImageSize(LatteTextureView* textureView, uint32 rowPitch)
{
	uint32 height = textureView->baseTexture->GetMipHeight(textureView->firstMip);
	if (Latte::IsCompressedFormat(textureView->format))
		height = (height + 3) / 4;
	return height * rowPitch;
}
