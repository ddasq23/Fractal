#pragma once

#include "HW/Espresso/Recompiler/IML/IMLInstruction.h"
#include "../PPCRecompiler.h"

bool PPCRecompiler_generateAArch64Code(struct PPCRecFunction_t* PPCRecFunction, struct ppcImlGenContext_t* ppcImlGenContext);
void PPCRecompiler_cleanupAArch64Code(void* code, size_t size);

void PPCRecompilerAArch64Gen_generateRecompilerInterfaceFunctions();

// true when PPCInterpreter_t::remainingCycles lives in a host register (w24) while recompiled code
// runs; that register is then not handed to the register allocator. perf/tuning.txt jit_cycles_reg=0 turns it off.
bool PPCRecompilerAArch64_cyclesInRegister();

// architecture specific constants
namespace IMLArchAArch64
{
	static constexpr int PHYSREG_GPR_BASE = 0;
	static constexpr int PHYSREG_GPR_COUNT = 25;
	static constexpr int PHYSREG_FPR_BASE = PHYSREG_GPR_COUNT;
	static constexpr int PHYSREG_FPR_COUNT = 31;
	static constexpr int PHYSREG_GPR_CYCLES = PHYSREG_GPR_BASE + 24; // remainingCycles, see PPCRecompilerAArch64_cyclesInRegister
}; // namespace IMLArchAArch64