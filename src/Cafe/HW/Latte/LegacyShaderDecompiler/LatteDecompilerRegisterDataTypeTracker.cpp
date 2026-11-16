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
	// the end of the instruction group. Only on Qualcomm drivers: the Mali compiler never finished Watch Dogs' streamout
	// shaders in the switch form (the array form takes about a minute there). perf/tuning.txt relwrite_switch=0/1 forces it
	if (shaderContext->analyzer.usesRelativeGPRWrite)
	{
		const bool isMetal = g_renderer && g_renderer->GetType() == RendererAPI::Metal; // the MSL emitter only has the array form
		if (g_renderer && g_renderer->UseRelativeWriteSwitch() && !isMetal && !shaderContext->analyzer.relativeGPRWriteNeedsArray &&
			(!shaderContext->analyzer.usesRelativeGPRRead || PerfLog::Tunable("relread_select", 1) != 0))
		{
			shaderContext->typeTracker.switchRelativeGPRWrites = true;
			// With indexed reads as well the compiler still crashes on any dynamically indexed GPR array (Pikmin 3: an
			// insertion sort over R8..R15), so the reads take the select chain over the registers in use, like shaders
			// without indexed writes. Registers only reached through the index would be missing from that chain and from the
			// write switch: mark everything from the lowest indexed base up to 8 above the highest register the shader names
			// (the clause temporaries R124..R127 are never indexed). perf/tuning.txt relread_select=0 keeps the array form
			if (shaderContext->analyzer.usesRelativeGPRRead)
			{
				sint32 highest = -1;
				for (sint32 i = 0; i < 124; i++)
				{
					if ((shaderContext->analyzer.gprUseMask[i / 8] & (1 << (i % 8))) != 0)
						highest = i;
				}
				const sint32 last = std::min(highest + 8, 123);
				for (sint32 i = std::max(shaderContext->analyzer.lowestRelativeGPR, 0); i <= last; i++)
					shaderContext->analyzer.gprUseMask[i / 8] |= (1 << (i % 8));
			}
		}
		else
			shaderContext->typeTracker.useArrayGPRs = true;
	}
}