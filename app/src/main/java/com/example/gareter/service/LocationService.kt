package com.example.gareter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.gareter.MainActivity
import com.example.gareter.data.model.Route
import com.example.gareter.data.model.RouteDirection
import com.example.gareter.data.model.Station
import com.example.gareter.data.repository.RouteRepository
import com.example.gareter.util.Haversine
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

class LocationService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_ROUTE_ID = "route_id"
        const val EXTRA_ROUTE_DIRECTION = "route_direction"
        const val CHANNEL_ID = "gare_ter_channel"
        const val NOTIF_ID = 1

        private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Idle)
        val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

        // Expose active route so TrackingScreen can display the full stop list
        private val _activeRoute = MutableStateFlow<Route?>(null)
        val activeRoute: StateFlow<Route?> = _activeRoute.asStateFlow()

        // Expose current location for real-time tracking map
        private val _currentLocation = MutableStateFlow<Location?>(null)
        val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()
    }

    sealed class ServiceState {
        object Idle : ServiceState()
        data class Tracking(
            val routeTitle: String,
            val nextStationName: String,
            val distanceMeters: Double
        ) : ServiceState()
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tts: TextToSpeech
    private lateinit var repository: RouteRepository
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var preferredDeviceId: Int? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Improvement #7: Mutex prevents concurrent location processing
    private val locationMutex = Mutex()

    // Improvement #3: Cooldowns persisted in DataStore
    private val cooldowns = mutableMapOf<String, MutableMap<String, Long>>()
    private val cooldownMillis = 60_000L

    private val approachTriggered = mutableSetOf<String>()
    private val arrivalTriggered = mutableSetOf<String>()

    private var currentRoute: Route? = null
    private var currentSpeedMs = 0f
    private var ttsReady = false
    @Volatile private var scoActive = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            // Improvement #2: Discard low-accuracy fixes
            if (location.accuracy > 80f) return
            if (location.hasSpeed()) currentSpeedMs = location.speed
            _currentLocation.value = location
            serviceScope.launch { processLocation(location) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = RouteRepository(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts.language = Locale.FRENCH
                initTtsListener()
            }
        }
        serviceScope.launch {
            repository.loadCooldowns().forEach { (routeId, map) ->
                cooldowns[routeId] = map.toMutableMap()
            }
        }
        // Device ID is now collected only during tracking (see onStartCommand ACTION_START)
        // This ensures changes are picked up in real-time while the service is active
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val routeId = intent.getStringExtra(EXTRA_ROUTE_ID) ?: return START_NOT_STICKY
                val directionOrdinal = intent.getIntExtra(EXTRA_ROUTE_DIRECTION, RouteDirection.FORWARD.ordinal)
                val direction = RouteDirection.values().getOrNull(directionOrdinal) ?: RouteDirection.FORWARD
                serviceScope.launch {
                    var route = repository.routesFlow.first().find { it.id == routeId }
                    // Inverser les stations si direction BACKWARD
                    if (route != null && direction == RouteDirection.BACKWARD) {
                        route = route.copy(stations = route.stations.reversed())
                    }
                    currentRoute = route
                    _activeRoute.value = route
                    if (route != null) {
                        repository.saveTrackingState(true)
                        repository.saveActiveRouteId(routeId)
                        _serviceState.value = ServiceState.Tracking(
                            routeTitle = route.title,
                            nextStationName = route.stations.firstOrNull()?.name ?: "",
                            distanceMeters = 0.0
                        )
                        // Listen for real-time device changes during tracking
                        repository.preferredDeviceIdFlow.collect { deviceId ->
                            preferredDeviceId = deviceId
                        }
                    }
                }
                startForeground(NOTIF_ID, buildNotification("Tracking actif", "Démarrage…"))
                startLocationUpdates()
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(1500L)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            Log.e("LocationService", "Location permission denied", e)
            stopSelf()
        }
    }

    private suspend fun processLocation(location: Location) {
        locationMutex.withLock {
            val route = currentRoute ?: return
            val speedKmh = currentSpeedMs * 3.6f

            // Improvement #8: Speed-adaptive radius for buses
            val radiusMultiplier = when {
                speedKmh > 80 -> 4.0
                speedKmh > 50 -> 2.0
                speedKmh > 30 -> 1.33
                else -> 1.0
            }

            val now = System.currentTimeMillis()
            var closestStation: Station? = null
            var closestDistance = Double.MAX_VALUE

            route.stations.forEach { station ->
                val distance = Haversine.distance(
                    location.latitude, location.longitude,
                    station.latitude, station.longitude
                )

                if (distance < closestDistance && !arrivalTriggered.contains(station.id)) {
                    closestDistance = distance
                    closestStation = station
                }

                val effectiveArrival = station.arrivalRadius * radiusMultiplier
                val effectiveApproach = station.approachRadius * radiusMultiplier

                if (distance <= effectiveArrival && !arrivalTriggered.contains(station.id)) {
                    val key = "${station.id}_arrival"
                    if (now - (cooldowns[route.id]?.get(key) ?: 0L) > cooldownMillis) {
                        arrivalTriggered.add(station.id)
                        approachTriggered.add(station.id)
                        cooldowns.getOrPut(route.id) { mutableMapOf() }[key] = now
                        triggerArrival(station)
                        serviceScope.launch { repository.saveCooldowns(cooldowns.toImmutable()) }
                    }
                } else if (distance <= effectiveApproach && !approachTriggered.contains(station.id)) {
                    val key = "${station.id}_approach"
                    if (now - (cooldowns[route.id]?.get(key) ?: 0L) > cooldownMillis) {
                        approachTriggered.add(station.id)
                        cooldowns.getOrPut(route.id) { mutableMapOf() }[key] = now
                        triggerApproach(station, distance.toInt())
                        serviceScope.launch { repository.saveCooldowns(cooldowns.toImmutable()) }
                    }
                }
            }

            // Improvement #4 & #5: Update UI state and notification
            closestStation?.let { station ->
                _serviceState.value = ServiceState.Tracking(
                    routeTitle = route.title,
                    nextStationName = station.name,
                    distanceMeters = closestDistance
                )
                updateNotification(station.name, closestDistance.toInt())
            }
        }
    }

    private fun triggerApproach(station: Station, distanceM: Int) {
        vibrate(longArrayOf(0, 200, 100, 200))
        val template = station.approachMessage
            ?: "Prochain arrêt : {nom}"
        val message = template
            .replace("{nom}", station.name)
            .replace("{distance}", distanceM.toString())
        speakMessage(message, "${station.id}_approach")
    }

    private fun triggerArrival(station: Station) {
        vibrate(longArrayOf(0, 400, 100, 400))
        val template = station.arrivalMessage ?: "Arrêt : {nom}"
        val message = template.replace("{nom}", station.name)
        speakMessage(message, "${station.id}_arrival")
    }

    private fun speakMessage(message: String, utterId: String) {
        if (!ttsReady) return

        serviceScope.launch {
            val useDucking = repository.audioDuckingFlow.first()

            // Detect if the preferred device is Bluetooth SCO (earpiece/headset profile)
            // SCO needs a different stream and audio mode than STREAM_MUSIC
            val selectedDevice = preferredDeviceId?.let { id ->
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).find { it.id == id }
            }
            val isSco = selectedDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO

            val stream: Int
            val usage: Int
            if (isSco && selectedDevice != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.setCommunicationDevice(selectedDevice)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.startBluetoothSco()
                    scoActive = true
                    delay(400) // SCO connection is asynchronous
                }
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                stream = AudioManager.STREAM_VOICE_CALL
                usage = AudioAttributes.USAGE_VOICE_COMMUNICATION
            } else {
                stream = AudioManager.STREAM_MUSIC
                usage = AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
            }

            val focusGain = if (useDucking) {
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            } else {
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            }

            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val request = AudioFocusRequest.Builder(focusGain)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { }
                .build()

            audioFocusRequest = request
            val result = audioManager.requestAudioFocus(request)

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                val params = Bundle()
                params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, stream)
                tts.speak(message, TextToSpeech.QUEUE_ADD, params, utterId)
            }
        }
    }

    private fun releaseSpeechResources() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        if (scoActive) {
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            scoActive = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    private fun initTtsListener() {
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { releaseSpeechResources() }
            override fun onError(utteranceId: String?) { releaseSpeechResources() }
        })
    }

    @Suppress("DEPRECATION")
    private fun vibrate(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(stationName: String, distanceM: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification("Tracking actif", "Prochain arrêt : $stationName — ${distanceM}m"))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Girouette BUS Tracking", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Suivi GPS ligne bus de substitution TER" }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        tts.shutdown()
        runBlocking {
            repository.saveTrackingState(false)
            repository.saveActiveRouteId(null)
            repository.saveCooldowns(cooldowns.toImmutable())
        }
        serviceScope.cancel()
        _serviceState.value = ServiceState.Idle
        _activeRoute.value = null
        _currentLocation.value = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun Map<String, MutableMap<String, Long>>.toImmutable(): Map<String, Map<String, Long>> =
        mapValues { it.value.toMap() }
}
