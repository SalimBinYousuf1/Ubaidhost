package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.ScreenCaptureManager
import com.example.service.UbaidHostService
import com.example.ui.screens.AuthorizedDevicesScreen
import com.example.ui.screens.DeviceDetailsScreen
import com.example.ui.screens.HostMainScreen
import com.example.ui.screens.OemGuideScreen
import com.example.ui.screens.PairingScreen
import com.example.ui.screens.ProtocolInspectorScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.HostNavigationScreen
import com.example.ui.viewmodel.HostViewModel
import com.example.ui.viewmodel.HostViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: HostViewModel by viewModels {
        val app = application as UbaidApplication
        HostViewModelFactory(
            context = applicationContext,
            deviceRepository = app.deviceRepository,
            hostConfigRepository = app.hostConfigRepository,
            keystoreHelper = app.keystoreHelper,
            permissionManager = app.permissionManager,
            telemetryProvider = app.telemetryProvider
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

                // MediaProjection permission launcher
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val mediaProjectionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                        val projection = projectionManager.getMediaProjection(result.resultCode, result.data!!)
                        if (projection != null) {
                            ScreenCaptureManager.instance.setMediaProjection(projection)
                            viewModel.refreshAll()
                            Toast.makeText(this, "Screen mirror authorized", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Failed to initialize screen mirror", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "Screen capture was declined", Toast.LENGTH_SHORT).show()
                    }
                }

                fun launchMediaProjectionRequest() {
                    mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Padding is handled within each scrollable screen with edge-to-edge support
                    when (currentScreen) {
                        is HostNavigationScreen.Dashboard -> {
                            HostMainScreen(
                                viewModel = viewModel,
                                onRequestMediaProjection = { launchMediaProjectionRequest() }
                            )
                        }
                        is HostNavigationScreen.Setup -> {
                            SetupScreen(
                                viewModel = viewModel,
                                onRequestMediaProjection = { launchMediaProjectionRequest() }
                            )
                        }
                        is HostNavigationScreen.Pairing -> {
                            PairingScreen(viewModel = viewModel)
                        }
                        is HostNavigationScreen.AuthorizedDevices -> {
                            AuthorizedDevicesScreen(viewModel = viewModel)
                        }
                        is HostNavigationScreen.TelemetryDetails -> {
                            DeviceDetailsScreen(viewModel = viewModel)
                        }
                        is HostNavigationScreen.OemGuide -> {
                            OemGuideScreen(viewModel = viewModel)
                        }
                        is HostNavigationScreen.ProtocolInspector -> {
                            ProtocolInspectorScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAll()
    }
}
