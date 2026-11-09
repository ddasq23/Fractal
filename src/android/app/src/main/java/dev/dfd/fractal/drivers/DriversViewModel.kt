/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.drivers

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dfd.fractal.common.customdrivers.Driver
import dev.dfd.fractal.common.customdrivers.DriverCaches
import dev.dfd.fractal.common.customdrivers.DriverInstallResult
import dev.dfd.fractal.common.customdrivers.DriverSourceInfo
import dev.dfd.fractal.common.customdrivers.installDriverZip
import dev.dfd.fractal.common.customdrivers.parseInstalledDrivers
import dev.dfd.fractal.common.customdrivers.readSourceInfo
import dev.dfd.fractal.common.gpu.Gpu
import dev.dfd.fractal.common.gpu.GpuInfo
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.nativeinterface.NativeEmulation
import dev.dfd.fractal.nativeinterface.NativeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class InstalledDriver(val driver: Driver, val source: DriverSourceInfo?)

data class SourceState(
    val releases: List<GhRelease> = emptyList(),
    val fetchedAt: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class DriversViewModel(application: Application) : AndroidViewModel(application) {
    private val catalog = DriverCatalog(application.filesDir.resolve("driver-catalog"))
    private val downloadDir = application.cacheDir.resolve("driver-download")

    val supportsCustomDrivers: Boolean = NativeEmulation.supportsLoadingCustomDriver()

    private val _gpu = MutableStateFlow<Gpu?>(null)
    val gpu = _gpu.asStateFlow()

    private val _installed = MutableStateFlow<List<InstalledDriver>>(emptyList())
    val installed = _installed.asStateFlow()

    private val _globalDriver = MutableStateFlow(NativeSettings.getCustomDriverPath())
    val globalDriver = _globalDriver.asStateFlow()

    private val _sources = MutableStateFlow<Map<String, SourceState>>(emptyMap())
    val sources = _sources.asStateFlow()

    /** Download progress per asset URL: 0..1, or -1 when the size is unknown. */
    private val _downloads = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloads = _downloads.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private var messageJob: Job? = null

    init {
        viewModelScope.launch {
            _gpu.value = withContext(Dispatchers.IO) { GpuInfo.current }
            reloadInstalled()
            loadCatalog(force = false)
        }
    }

    /** Sources for this device's GPU vendor. */
    fun visibleSources(gpu: Gpu?): List<DriverSource> =
        if (gpu == null || !supportsCustomDrivers) emptyList() else DriverSources.filter { it.vendor == gpu.vendor && it.supports(gpu) }

    fun refresh() = viewModelScope.launch { loadCatalog(force = true) }

    private suspend fun loadCatalog(force: Boolean) {
        val now = System.currentTimeMillis()
        visibleSources(_gpu.value).forEach { source ->
            val cached = catalog.cached(source)
            setSource(source) { SourceState(cached?.releases.orEmpty(), cached?.fetchedAt) }
            val stale = cached == null || now - cached.fetchedAt > CATALOG_MAX_AGE_MS
            if (force || stale) {
                viewModelScope.launch {
                    setSource(source) { it.copy(loading = true, error = null) }
                    runCatching { catalog.fetch(source) }
                        .onSuccess { listing -> setSource(source) { SourceState(listing.releases, listing.fetchedAt) } }
                        .onFailure { e -> setSource(source) { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) } }
                }
            }
        }
    }

    private fun setSource(source: DriverSource, block: (SourceState) -> SourceState) =
        _sources.update { it + (source.key to block(it[source.key] ?: SourceState())) }

    private suspend fun reloadInstalled() {
        _installed.value = withContext(Dispatchers.IO) {
            parseInstalledDrivers().map { InstalledDriver(it, readSourceInfo(File(it.path))) }
        }
        // the selected driver may have been deleted elsewhere
        val global = _globalDriver.value
        if (global != null && _installed.value.none { it.driver.path == global }) select(null)
    }

    fun install(source: DriverSource, release: GhRelease, asset: GhAsset) {
        if (asset.downloadUrl in _downloads.value) return
        viewModelScope.launch {
            _downloads.update { it + (asset.downloadUrl to 0f) }
            val file = downloadDir.resolve(asset.name)
            try {
                catalog.download(asset, file) { p -> _downloads.update { it + (asset.downloadUrl to p) } }
                val info = DriverSourceInfo(source.owner, source.repo, release.tag, asset.name)
                when (val result = file.inputStream().use { installDriverZip(it, info) }) {
                    is DriverInstallResult.Installed -> show(tr("Installed {0}", result.driver.metadata.name))
                    is DriverInstallResult.AlreadyInstalled -> show(tr("{0} is already installed", result.driver.metadata.name))
                    DriverInstallResult.Invalid -> show(tr("{0} is not a valid driver package for this device", asset.name))
                }
                reloadInstalled()
            } catch (e: Exception) {
                show(tr("Download failed: {0}", e.message ?: e.javaClass.simpleName))
            } finally {
                file.delete()
                _downloads.update { it - asset.downloadUrl }
            }
        }
    }

    fun importFromFile(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { installDriverZip(it) }
        }.getOrNull()
        when (result) {
            is DriverInstallResult.Installed -> show(tr("Installed {0}", result.driver.metadata.name))
            is DriverInstallResult.AlreadyInstalled -> show(tr("{0} is already installed", result.driver.metadata.name))
            else -> show(tr("This file is not a valid driver package"))
        }
        reloadInstalled()
    }

    /** Uses [path] as the global driver (null = system driver). Each driver keeps its own pipeline cache. */
    fun select(path: String?) = viewModelScope.launch {
        if (_globalDriver.value == path) return@launch
        DriverCaches.setGlobalDriver(path)
        _globalDriver.value = path
        val name = _installed.value.firstOrNull { it.driver.path == path }?.driver?.metadata?.name ?: tr("System driver")
        show(tr("Now using {0}", name))
    }

    fun delete(item: InstalledDriver) = viewModelScope.launch {
        if (_globalDriver.value == item.driver.path) select(null).join()
        withContext(Dispatchers.IO) { File(item.driver.path).deleteRecursively() }
        reloadInstalled()
    }

    private fun show(text: String) {
        _message.value = text
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(6000)
            _message.value = null
        }
    }
}
