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

void LatteOverlay_pushNotification(const std::string& text, sint32 duration);