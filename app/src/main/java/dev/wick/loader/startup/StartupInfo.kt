package dev.wick.loader.startup

import dev.wick.loader.abc.IHookBridge
import dev.wick.loader.abc.ILoaderService

object StartupInfo {

    lateinit var modulePath: String
    lateinit var loaderService: ILoaderService
    var hookBridge: IHookBridge? = null
}
