#include "Cafe/HW/Latte/Core/LatteConst.h"
#include "Cafe/HW/Latte/Core/LatteShaderAssembly.h"
#include "Cafe/HW/Latte/LegacyShaderDecompiler/LatteDecompilerInstructions.h"
#include "Cafe/HW/Latte/LegacyShaderDecompiler/LatteDecompiler.h"
#include "Cafe/HW/Latte/LegacyShaderDecompiler/LatteDecompilerInternal.h"
#include "Cafe/HW/Latte/Core/LattePerfLog.h"
#include "Cafe/HW/Latte/Renderer/Renderer.h"

void LatteDecompiler_analyzeDataTypes(LatteDecompilerShaderContext* shaderContext)
{
	// diagnostics: perf/tuning.txt streamout_int_gprs=1 keeps the GPRs of shaders that write streamout data as integers,
	// so integer data that only passes through float registers is not flushed as a denormal by the host GPU
	static const bool s_streamoutIntGprs = PerfLog::Tunable("streamout_int_gprs", 0) != 0;
	const bool writesStreamout = shaderContext->output->streamoutBufferWriteMask.any();
	// determine default type
	if (shaderContext->analyzer.usesIntegerValues || (s_streamoutIntGprs && writesStreamout))
	{
		shaderContext->typeTracker.defaultDataType = LATTE_DECOMPILER_DTYPE_SIGNED_INT;
		shaderContext->typeTracker.genIntReg = true;
	}
	else
	{
		shaderContext->typeTracker.defaultDataType = LATTE_DECOMPILER_DTYPE_FLOAT;
		shaderContext->typeTracker.genFloatReg = true;
	}
	// determine register representation
	// Indexed GPR writes (R[n+AR] = x) used to turn all 128 GPRs into one dynamically indexed array. The shader compiler
	// of the Qualcomm driver crashes on some of those (Tekken Tag Tournament 2: a 4x4 gather loop that fills R22..R37,
	// SIGSEGV in vkCreateGraphicsPipelines). Without indexed reads the written registers are only read by their own
	// index, so they stay separate variables and the indexed write becomes a switch over the registers in use, emitted at
	// the end of the instruction group. perf/tuning.txt relwrite_switch=0 restores the array
	static const bool s_relWriteSwitch = PerfLog::Tunable("relwrite_switch", 1) != 0;
	if (shaderContext->analyzer.usesRelativeGPRWrite)
	{
		const bool isMetal = g_renderer && g_renderer->GetType() == RendererAPI::Metal; // the MSL emitter only has the array form
		if (s_relWriteSwitch && !isMetal && !shaderContext->analyzer.usesRelativeGPRRead && !shaderContext->analyzer.relativeGPRWriteNeedsArray)
			shaderContext->typeTracker.switchRelativeGPRWrites = true;
		else
			shaderContext->typeTracker.useArrayGPRs = true;
	}
}