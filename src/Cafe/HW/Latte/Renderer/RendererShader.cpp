#include "Cafe/HW/Latte/Renderer/RendererShader.h"
#include "Cafe/HW/Latte/Core/LattePerfLog.h"
#include "Cafe/GameProfile/GameProfile.h"

// generate a Cemu version and setting dependent id
uint32 RendererShader::GeneratePrecompiledCacheId()
{
	uint32 v = 0;
	const char* s = EMULATOR_VERSION_SUFFIX;
	while (*s)
	{
		v = std::rotl<uint32>(v, 7);
		v += (uint32)(*s);
		s++;
	}
	v += (EMULATOR_VERSION_MAJOR * 1000000u);
	v += (EMULATOR_VERSION_MINOR * 10000u);
	v += (EMULATOR_VERSION_PATCH * 100u);

	// settings that can influence shaders
	v += (uint32)g_current_game_profile->GetAccurateShaderMul() * 133;
	v += (uint32)(PerfLog::Tunable("strict_mul", -1) + 1) * 977;
	// diagnostics that change the generated GLSL (LatteDecompilerEmitGLSL.cpp _emitPixelColorSanitizeDiag)
	v += (uint32)PerfLog::Tunable("ps_sanitize", 0) * 7919;
	v += (uint32)PerfLog::Tunable("nan_bits", 0) * 15485863;
	v += (uint32)PerfLog::Tunable("nan_clamp", 0) * 32452843;
	v += (uint32)PerfLog::Tunable("ps_sanitize_lo", 0) * 104729 + (uint32)PerfLog::Tunable("ps_sanitize_hi", 0xFFFF) * 1299709;

	v += 0x820a5291; // change this value for manual invalidation

	return v;
}

void RendererShader::GenerateShaderPrecompiledCacheFilename(RendererShader::ShaderType type, uint64 baseHash, uint64 auxHash, uint64& h1, uint64& h2)
{
	h1 = baseHash;
	h2 = auxHash;

	if (type == RendererShader::ShaderType::kVertex)
		h2 += 0xA16374cull;
	else if (type == RendererShader::ShaderType::kFragment)
		h2 += 0x8752deull;
	else if (type == RendererShader::ShaderType::kGeometry)
		h2 += 0x65a035ull;
}