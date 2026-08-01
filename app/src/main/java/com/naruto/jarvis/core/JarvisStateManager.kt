package com.naruto.jarvis.core

/**
 * JarvisStateManager
 * ---------------------------------------------------------------
 * Single source of truth for whether Jarvis is SLEEPING (only the
 * lightweight local wake-word engine is running) or ACTIVE (full
 * cloud STT + LLM intent processing + device control is live).
 *
 * Nothing else in the app should flip state directly — always go
 * through toggle_state()/setState() so every subsystem reacts
 * consistently (mic mode, TTS, accessibility routing, battery use).
 */
enum class JarvisState { SLEEP, ACTIVE }

interface JarvisStateListener {
    fun onStateChanged(newState: JarvisState)
}

object JarvisStateManager {

    var currentState: JarvisState = JarvisState.SLEEP
        private set

    private val listeners = mutableListOf<JarvisStateListener>()

    fun addListener(listener: JarvisStateListener) {
        listeners.add(listener)
    }

    /**
     * toggle_state() — the ONLY way state should change.
     * Called from:
     *   - WakeWordService, when "Jarvis are you there" is detected (-> ACTIVE)
     *   - CommandRouter, when a transcript matches "go sleep" / "go to sleep" (-> SLEEP)
     */
    fun toggleState(target: JarvisState) {
        if (target == currentState) return
        currentState = target
        listeners.forEach { it.onStateChanged(target) }
    }

    fun wake() = toggleState(JarvisState.ACTIVE)
    fun sleep() = toggleState(JarvisState.SLEEP)
}
