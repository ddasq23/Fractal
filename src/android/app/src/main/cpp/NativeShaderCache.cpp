#include "Cemu/FileCache/FileCache.h"
#include "JNIUtils.h"

#include <jni.h>
#include <memory>

// Community shader caches: entries of a downloaded Cemu FileCache (transferable shaders or Vulkan pipelines) are added
// to the game's own cache. The game must not be running (its process keeps the cache open).

namespace
{
	sint32 CountEntries(FileCache& cache)
	{
		sint32 count = 0;
		std::vector<uint8> data;
		for (sint32 i = 0; i <= cache.GetMaximumFileIndex(); i++)
		{
			uint64 n1, n2;
			if (cache.GetFileByIndex(i, &n1, &n2, data))
				count++;
		}
		return count;
	}
} // namespace

// {entries, extraVersion}, or null when the file is not a valid cache
extern "C" [[maybe_unused]] JNIEXPORT jlongArray JNICALL
Java_dev_dfd_fractal_nativeinterface_NativeShaderCache_info(JNIEnv* env, [[maybe_unused]] jclass clazz, jstring path)
{
	std::unique_ptr<FileCache> cache(FileCache::Open(JNIUtils::FromJString(env, path)));
	if (!cache)
		return nullptr;
	jlong out[2] = {CountEntries(*cache), (jlong)cache->GetExtraVersion()};
	jlongArray result = env->NewLongArray(2);
	env->SetLongArrayRegion(result, 0, 2, out);
	return result;
}

// Adds the entries of `source` missing from `target` (created as a copy of the source format when it doesn't exist).
// Returns {added, entries in target after}, {-1, 0} when the source is not a valid cache, {-2, 0} when the two caches
// have different versions (made by an incompatible emulator build) and {-3, 0} when the target can't be opened.
extern "C" [[maybe_unused]] JNIEXPORT jlongArray JNICALL
Java_dev_dfd_fractal_nativeinterface_NativeShaderCache_merge(JNIEnv* env, [[maybe_unused]] jclass clazz, jstring sourcePath, jstring targetPath)
{
	auto reply = [env](jlong a, jlong b) {
		jlong out[2] = {a, b};
		jlongArray result = env->NewLongArray(2);
		env->SetLongArrayRegion(result, 0, 2, out);
		return result;
	};
	std::unique_ptr<FileCache> source(FileCache::Open(JNIUtils::FromJString(env, sourcePath)));
	if (!source)
		return reply(-1, 0);
	const std::string target = JNIUtils::FromJString(env, targetPath);
	std::unique_ptr<FileCache> dest(FileCache::Open(target));
	if (dest && dest->GetExtraVersion() != source->GetExtraVersion())
		return reply(-2, 0);
	if (!dest)
		dest.reset(FileCache::Create(target, source->GetExtraVersion()));
	if (!dest)
		return reply(-3, 0);
	// the entries are zstd inside already; Cemu writes them uncompressed too
	dest->UseCompression(false);
	jlong added = 0;
	std::vector<uint8> data;
	for (sint32 i = 0; i <= source->GetMaximumFileIndex(); i++)
	{
		uint64 n1, n2;
		if (!source->GetFileByIndex(i, &n1, &n2, data))
			continue;
		if (dest->HasFile({n1, n2}))
			continue;
		dest->AddFile({n1, n2}, data.data(), (sint32)data.size());
		added++;
	}
	const jlong total = CountEntries(*dest);
	return reply(added, total);
}
