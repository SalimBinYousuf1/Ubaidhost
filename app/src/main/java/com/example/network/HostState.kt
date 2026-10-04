package com.example.network

enum class HostState(val label: String, val isRunning: Boolean) {
    INITIALIZING("Initializing...", false),
    SETUP_REQUIRED("Setup Required", false),
    READY("Ready to Start", false),
    PAIRING("Waiting for Controller Pairing...", true),
    PAIRED("Controller Paired", true),
    ONLINE("Online & Protected", true),
    CONNECTING("Connecting to Relay...", true),
    CONNECTED("Connected to Relay", true),
    ACTIVE_SESSION("Active Remote Session", true),
    RECONNECTING("Reconnecting...", true),
    ERROR("Service Error", false),
    STOPPED("Host Stopped", false)
}
