package com.example.gareter.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import com.example.gareter.data.model.Route
import com.example.gareter.data.model.RouteDirection
import com.example.gareter.data.repository.RouteRepository
import com.example.gareter.service.LocationService
import com.example.gareter.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RouteRepository(application)

    private val _syncLoading = MutableStateFlow(false)
    val syncLoading = _syncLoading.asStateFlow()

    init {
        syncRoutesFromSupabase()
    }

    fun syncRoutesFromSupabase() {
        viewModelScope.launch {
            _syncLoading.value = true
            repository.fetchRoutesFromSupabase()
            _syncLoading.value = false
        }
    }

    val routes: StateFlow<List<Route>> = repository.routesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trackingState: StateFlow<LocationService.ServiceState> = LocationService.serviceState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocationService.ServiceState.Idle)

    // Active route exposed by the service — used by TrackingScreen to show stop list
    val activeRoute: StateFlow<Route?> = LocationService.activeRoute
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentLocation: StateFlow<Location?> = LocationService.currentLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val audioDucking: StateFlow<Boolean> = repository.audioDuckingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val selectedAudioDeviceId: StateFlow<Int?> = repository.preferredDeviceIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val themeMode: StateFlow<ThemeMode> = repository.themeModeFlow
        .map { ThemeMode.valueOf(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val driverAgent: StateFlow<String?> = repository.driverAgentFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val driverName: StateFlow<String?> = repository.driverNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun logoutDriver() {
        viewModelScope.launch {
            repository.clearDriverSession()
        }
    }

    fun startTracking(route: Route, direction: RouteDirection = RouteDirection.FORWARD) {
        val ctx = getApplication<Application>()
        ctx.startForegroundService(Intent(ctx, LocationService::class.java).apply {
            action = LocationService.ACTION_START
            putExtra(LocationService.EXTRA_ROUTE_ID, route.id)
            putExtra(LocationService.EXTRA_ROUTE_DIRECTION, direction.ordinal)
        })
    }

    fun stopTracking() {
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP
        })
    }

    fun setAudioDucking(enabled: Boolean) {
        viewModelScope.launch {
            repository.saveAudioDucking(enabled)
        }
    }

    fun setAudioDevice(deviceId: Int?) {
        viewModelScope.launch {
            repository.savePreferredDeviceId(deviceId)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.saveThemeMode(mode.name) }
    }

    fun exportRoutes(context: Context) {
        viewModelScope.launch {
            try {
                val json = repository.exportRoutesToJson()
                val cacheFile = File(context.cacheDir, "gare_ter_routes.json")
                cacheFile.writeText(json)
                
                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )
                
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                val chooser = Intent.createChooser(intent, "Partager les trajets")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importRoutes(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val contentResolver = context.contentResolver
                val json = contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                }
                if (json == null) {
                    onResult(false, "Impossible de lire le fichier")
                    return@launch
                }
                
                val result = repository.importRoutesFromJson(json)
                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    onResult(true, "$count trajet(s) importé(s) avec succès")
                } else {
                    onResult(false, "Format de fichier invalide")
                }
            } catch (e: Exception) {
                onResult(false, "Erreur lors de l'importation : ${e.localizedMessage}")
            }
        }
    }
}
