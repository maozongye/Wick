package dev.wick.agent.environment

import dev.wick.utils.BuildFingerprint

/**
 * Directory names the Agent uses inside a Linux environment's working directory.
 *
 * These were fixed literals (`.weagent`, `.weagent-edit-*`, `.weagent-invoke-tool-*`): a
 * recognisable marker in every managed environment, and visible on remote SSH hosts where the
 * module has no business advertising itself. [dir] is derived from the build nonce instead.
 *
 * The whole set lives here so the naming stays consistent across backends — each backend can
 * be pointed at a different environment, but they must all agree on the layout.
 */
object AgentPaths {

    /** Root for Agent-owned files inside a working directory (replaces the old `.weagent`). */
    val dir: String = "." + BuildFingerprint.label

    /** Rooted path helpers; callers pass the environment's working directory. */
    fun bridgeBin(workingDirectory: String): String = "$workingDirectory/$dir/bin/invoke_tool"

    fun outputs(workingDirectory: String): String = "$workingDirectory/$dir/outputs"

    fun binDirectory(workingDirectory: String): String = "$workingDirectory/$dir/bin"

    /** Temporary-file prefixes for atomic edits and uploads. */
    val editTempPrefix: String = "$dir-edit-"

    val invokeToolUploadPrefix: String = "$dir-invoke-tool-"
}
