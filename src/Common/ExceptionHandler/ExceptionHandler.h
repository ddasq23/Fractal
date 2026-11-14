#pragma once

void ExceptionHandler_Init();

bool CrashLog_Create();
void CrashLog_SetOutputChannels(bool writeToStdErr, bool writeToLogTxt);
void CrashLog_WriteLine(std::string_view text, bool newLine = true);
void CrashLog_WriteHeader(const char* header);

void ExceptionHandler_LogGeneralInfo();
void ExceptionHandler_LogThreadList(bool withStacks);

// what the calling thread is doing right now (e.g. which pipeline it hands to the driver), printed with the crash
// report if this thread crashes. nullptr clears it. Cheap: a copy into a thread local buffer
void ExceptionHandler_SetThreadNote(const char* note);
const char* ExceptionHandler_GetThreadNote();
