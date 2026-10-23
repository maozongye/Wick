package dev.wick.agent.terminal

import dev.wick.agent.environment.EnvironmentSnapshot

interface TerminalBackend {
    suspend fun start(
        environment: EnvironmentSnapshot,
        argv: List<String>,
        workingDirectory: String?,
        environmentVariables: Map<String, String>,
        cols: Int,
        rows: Int,
    ): TerminalBackendStart
}
