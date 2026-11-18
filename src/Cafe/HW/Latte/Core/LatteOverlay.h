#pragma once

void LatteOverlay_init();
void LatteOverlay_render(bool pad_view);
// bottom of the stats box in the top left corner of the TV window, as a fraction of its height; 0 when it isn't there
float LatteOverlay_GetStatsTopLeftBottom();
void LatteOverlay_updateStats(double fps, sint32 drawcalls, sint32 fastDrawcalls);
// frames per second of the running game in the last measured interval (also with the overlay off)
double LatteOverlay_GetFps();
// called for every image presented to the TV, generated is true for frames created by frame generation
void LatteOverlay_notifyFramePresented(bool generated);

// the overlay box's statistics, for the app's performance HUD (gathered only while it wants them)
struct LatteOverlayHudStats
{
	double fps = 0.0; // game frames
	double presentedFps = 0.0; // real and generated frames shown on the TV
	double frameGenRatio = 1.0;
	bool frameGenActive = false;
	sint32 drawCallsPerFrame = 0;
	sint32 fastDrawCallsPerFrame = 0;
	float cpuUsage = 0.0f; // the emulator's process, in percent of the whole CPU
	uint32 ramUsageMB = 0; // the emulator's process
	int vramUsageMB = -1, vramTotalMB = -1; // -1 where the renderer can't tell
};
void LatteOverlay_SetHudStatsWanted(bool wanted);
LatteOverlayHudStats LatteOverlay_GetHudStats();

void LatteOverlay_pushNotification(const std::string& text, sint32 duration);