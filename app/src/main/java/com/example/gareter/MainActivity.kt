package com.example.gareter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gareter.ui.navigation.NavGraph
import com.example.gareter.ui.theme.GareTERTheme
import com.example.gareter.ui.viewmodel.CaisseViewModel
import com.example.gareter.ui.viewmodel.CreateRouteViewModel
import com.example.gareter.ui.viewmodel.HomeViewModel
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()
    private val createRouteViewModel: CreateRouteViewModel by viewModels()
    private val caisseViewModel: CaisseViewModel by viewModels()

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* App continues regardless — location unavailability is handled in LocationService */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            load(this@MainActivity, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = "GirouetteBUS/1.0 (madi.douhouchina@gmail.com)"
        }
        requestNeededPermissions()
        setContent {
            val themeMode by homeViewModel.themeMode.collectAsStateWithLifecycle()
            GareTERTheme(themeMode = themeMode) {
                NavGraph(
                    homeViewModel = homeViewModel,
                    createRouteViewModel = createRouteViewModel,
                    caisseViewModel = caisseViewModel,
                )
            }
        }
    }


    private fun requestNeededPermissions() {
        val needed = buildList {
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        if (needed.isNotEmpty()) requestPermissions.launch(needed.toTypedArray())
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
