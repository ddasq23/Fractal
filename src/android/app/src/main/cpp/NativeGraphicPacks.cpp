#include "Cafe/CafeSystem.h"
#include "config/CemuConfig.h"
#include "Cafe/GraphicPack/GraphicPack2.h"
#include "Cafe/HW/Latte/Core/LatteAsyncCommands.h"
#include "JNIUtils.h"

namespace NativeGraphicPacks
{
	std::unordered_map<sint64, GraphicPackPtr> g_graphicPacks;

	void SaveGraphicPackStateToConfig(const GraphicPackPtr& graphicPack)
	{
		auto& data = GetConfig();
		auto filename = _utf8ToPath(graphicPack->GetNormalizedPathString());
		if (data.graphic_pack_entries.contains(filename))
			data.graphic_pack_entries.erase(filename);
		if (graphicPack->IsEnabled())
		{
			data.graphic_pack_entries.try_emplace(filename);
			auto& it = data.graphic_pack_entries[filename];
			// otherwise store all selected presets
			for (const auto& preset : graphicPack->GetActivePresets())
				it.try_emplace(preset->category, preset->name);
		}
		else if (graphicPack->IsDefaultEnabled())
		{
			// save that its disabled
			data.graphic_pack_entries.try_emplace(filename);
			auto& it = data.graphic_pack_entries[filename];
			it.try_emplace("_disabled", "false");
		}
	}

	jobjectArray GetGraphicPresets(JNIEnv* env, const GraphicPackPtr& graphicPack, sint64 id)
	{
		using namespace std::ranges::views;
		auto graphicPackPresetClass = env->FindClass("dev/dfd/fractal/nativeinterface/NativeGraphicPacks$GraphicPackPreset");
		auto graphicPackPresetCtorId = env->GetMethodID(graphicPackPresetClass, "<init>", "(JLjava/lang/String;[Ljava/lang/String;Ljava/lang/String;)V");

		std::vector<std::string> order;
		using CategorizedPresets = std::unordered_map<std::string, std::vector<GraphicPack2::PresetPtr>>;
		CategorizedPresets presets = graphicPack->GetCategorizedPresets(order);

		struct PresetData
		{
			const std::string& category;
			const std::vector<GraphicPack2::PresetPtr>& presets;
		};

		auto presetsData = order |
						   transform([&](const auto& category) { return PresetData{category, presets.at(category)}; }) |
						   filter([](const PresetData& presetData) {
							   return std::ranges::any_of(presetData.presets, [](const auto& p) { return p->visible; });
						   });

		return JNIUtils::CreateObjectArray(
			env,
			graphicPackPresetClass,
			presetsData,
			[&](const auto& presetData) -> jobject {
				jstring categoryJStr = presetData.category.empty() ? nullptr : JNIUtils::ToJString(env, presetData.category);
				std::vector<std::string> presetSelections;
				std::optional<std::string> activePreset;
				for (auto& pentry : presetData.presets)
				{
					if (!pentry->visible)
						continue;

					presetSelections.push_back(pentry->name);

					if (pentry->active)
						activePreset = pentry->name;
				}

				jstring activePresetJstr = nullptr;

				if (activePreset.has_value())
					activePresetJstr = JNIUtils::ToJString(env, activePreset.value());
				else if (!presetSelections.empty())
					activePresetJstr = JNIUtils::ToJString(env, presetSelections.front());

				return env->NewObject(graphicPackPresetClass, graphicPackPresetCtorId, id, categoryJStr, JNIUtils::CreateStringObjectArray(env, presetSelections), activePresetJstr);
			});
	}
} // namespace NativeGraphicPacks

extern "C" [[maybe_unused]] JNIEXPORT void JNICALL
Java_dev_dfd_fractal_nativeinterface_NativeGraphicPacks_refreshGraphicPacks([[maybe_unused]] JNIEnv* env, [[maybe_unused]] jclass clazz)
{
	if (CafeSystem::IsTitleRunning())
	{
		return;
	}

	GraphicPack2::ClearGraphicPacks();
	GraphicPack2::LoadAll();
	NativeGraphicPacks::g_graphicPacks.clear();

	auto graphicPacks = GraphicPack2::GetGraphicPacks();

	for (auto&& graphicPack : graphicPacks)
	{
		NativeGraphicPacks::g_graphicPacks[reinterpret_cast<sint64>(graphicPack.get())] = graphicPack;
	}
}

extern "C" [[maybe_unused]] JNIEXPORT jobjectArray JNICALL
Java_dev_dfd_fractal_nativeinterface_NativeGraphicPacks_getGraphicPackBasicInfos(JNIEnv* env, [[maybe_unused]] jclass clazz)
{
	jclass graphicPackInfoClass = env->FindClass("dev/dfd/fractal/nativeinterface/NativeGraphicPacks$GraphicPackBasicInfo");
	jmethodID graphicPackInfoCtorId = env->GetMethodID(graphicPackInfoClass, "<init>", "(JLjava/lang/String;Z[J)V");

	return JNIUtils::CreateObjectArray(
		env,
		graphicPackInfoClass,
		NativeGraphicPacks::g_graphicPacks,
		[&](const auto& graphicPack) {
			jstring virtualPath = JNIUtils::ToJString(env, graphicPack.second->GetVirtualPath());
			jlong id = graphicPack.first;
			jlongArray titleIds = JNIUtils::CreateLongArray(env, graphicPack.second->GetTitleIds());
			return env->NewObject(graphicPackInfoClass, graphicPackInfoCtorId, id, virtualPath, graphicPack.second->IsEnabled(), titleIds);
		});
}

extern "C" [[maybe_unused]] JNIEXPORT jobject JNICALL
Java_dev_dfd_fractal_nativeinterface_NativeGraphicPacks_getGraphicPack(JNIEnv* env, [[maybe_unused]] jclass clazz, jlong id)
{
	jclass graphicPackClass = env->FindClass("dev/dfd/fractal/nativeinterface/NativeGraphicPacks$GraphicPack");
	jmethodID graphicPackCtorId = env->GetMethodID(graphicPackClass, "<init>", "(JZLjava/lang/String;Ljava/lang/String;[Ldev/dfd/fractal/nativeinterface/NativeGraphicPacks$GraphicPackPreset;)V");
	const auto& graphicPack = NativeGraphicPacks::g_graphicPacks.at(id);

	jstring graphicPackName = JNIUtils::ToJString(env, graphicPack->GetName());
	jstring graphicPackDescription = JNIUtils::ToJString(env, graphicPack->GetDescription());
	return env->NewObject(
		graphicPackClass,
		graphicPackCtorId,
		id,
		graphicPack->IsEnabled(),
		graphicPackName,
		graphicPackDescription,
		NativeGraphicPacks::GetGraphicPresets(env, graphicPack, id));
}

extern "C" [[maybe_unused]] JNIEXPORT void JNICALL
Java_dev_dfd_fractal_nativeinterface_NativeGraphicPacks_setGraphicPackActive([[maybe_unused]] JNIEnv* env, [[maybe_unused]] jclass clazz, jlong id, jboolean active)
{
	const auto& graphicPack = NativeGraphicPacks::g_graphicPacks.at(id);
	graphicPack->SetEnabled(active);
	NativeGraphicPacks::SaveGraphicPackStateToConfig(graphicPack);
}

extern "C" [[maybe_unused]] JNIEXPORT void JNICALL
Java_dev_dfd_fractal_nativeinterface_NativeGraphicPacks_setGraphicPackActivePreset([[maybe_unused]] JNIEnv* env, [[maybe_unused]] jclass clazz, jlong id, jstring category, jstring preset)
{
	std::string presetCategory = category == nullptr ? "" : JNIUtils::FromJString(env, category);
	const auto& graphicPack = NativeGraphicPacks::g_graphicPacks.at(id);
	graphicPack->SetActivePreset(presetCategory, JNIUtils::FromJString(env, preset));
	NativeGraphicPacks::SaveGraphicPackStateToConfig(graphicPack);
}

extern "C" [[maybe_unused]] JNIEXPORT jobjectArray JNICALL
Java_dev_dfd_fractal_nativeinterface_NativeGraphicPacks_getGraphicPackPresets(JNIEnv* env, [[maybe_unused]] jclass clazz, jlong id)
{
	return NativeGraphicPacks::GetGraphicPresets(env, NativeGraphicPacks::g_graphicPacks.at(id), id);
}

namespace
{
	// same as the PC graphic packs window: shaders of the pack are dropped from the runtime cache so they compile again
	void DeleteShadersFromRuntimeCache(const GraphicPackPtr& graphicPack)
	{
		for (const auto& shader : graphicPack->GetCustomShaders())
		{
			LatteConst::ShaderType shaderType;
			if (shader.type == GraphicPack2::GP_SHADER_TYPE::VERTEX)
				shaderType = LatteConst::ShaderType::Vertex;
			else if (shader.type == GraphicPack2::GP_SHADER_TYPE::GEOMETRY)
				shaderType = LatteConst::ShaderType::Geometry;
			else
				shaderType = LatteConst::ShaderType::Pixel;
			LatteAsyncCommands_queueDeleteShader(shader.shader_base_hash, shader.shader_aux_hash, shaderType);
		}
	}

	void ReloadPack(const GraphicPackPtr& graphicPack)
	{
		if (graphicPack->HasShaders() || graphicPack->HasPatches() || graphicPack->HasCustomVSyncFrequency())
		{
			if (graphicPack->Reload())
				DeleteShadersFromRuntimeCache(graphicPack);
		}
	}
} // namespace

// Brings the running game in line with the pack's saved state, like Cemu on PC does when a pack is toggled or a preset
// changes while a game runs. 0: no game of this pack is running, 1: applied now, 2: applies when the game starts again
// (packs with texture rules, e.g. most resolution packs).
extern "C" [[maybe_unused]] JNIEXPORT jint JNICALL
Java_dev_dfd_fractal_nativeinterface_NativeGraphicPacks_applyToRunningGame([[maybe_unused]] JNIEnv* env, [[maybe_unused]] jclass clazz, jlong id)
{
	const auto it = NativeGraphicPacks::g_graphicPacks.find(id);
	if (it == NativeGraphicPacks::g_graphicPacks.end())
		return 0;
	const auto& graphicPack = it->second;
	if (!CafeSystem::IsTitleRunning() || !graphicPack->ContainsTitleId(CafeSystem::GetForegroundTitleId()))
		return 0;
	if (graphicPack->RequiresRestart(true, true))
		return 2;
	if (graphicPack->IsEnabled())
	{
		if (!graphicPack->IsActivated())
			GraphicPack2::ActivateGraphicPack(graphicPack);
		ReloadPack(graphicPack);
	}
	else if (graphicPack->IsActivated())
	{
		DeleteShadersFromRuntimeCache(graphicPack);
		GraphicPack2::DeactivateGraphicPack(graphicPack);
	}
	return 1;
}
