#include "Cafe/HW/Latte/Core/LatteConst.h"
#include "Cafe/HW/Latte/Core/LatteShaderAssembly.h"
#include "Cafe/HW/Latte/LegacyShaderDecompiler/LatteDecompilerInstructions.h"
#include "Cafe/HW/Latte/LegacyShaderDecompiler/LatteDecompiler.h"
#include "Cafe/HW/Latte/LegacyShaderDecompiler/LatteDecompilerInternal.h"
#include "Cafe/HW/Latte/Core/LattePerfLog.h"

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
	if (shaderContext->analyzer.usesRelativeGPRWrite)
	{
		shaderContext->typeTracker.useArrayGPRs = true;
	}
}