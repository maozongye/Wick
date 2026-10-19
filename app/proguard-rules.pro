# WeKit R8 rules — identity obfuscation with a minimal keep set.
#
# Goal: no `dev.ujhhgtg.wekit.*` class name survives in the release DEX, so
# ClassLoader enumeration, stack walking, and reflection over module class names
# all see short meaningless names like the host's own obfuscated classes.
#
# The keep set is intentionally TINY: only entry points looked up by fixed name
# from outside the JVM (assets/xposed_init, META-INF/xposed/java_init.list,
# native RegisterNatives) and the BuildConfig the native decoder reads.
#
# Everything else is renamable. Known consumers to watch when changing this file:
#  - KSP-generated FeaturesProvider/AgentToolsProvider reference feature objects
#    directly (typed), so R8 keeps those automatically — no name-based lookup.
#  - DexResolutionTestRegistry stores qualified names as STRINGS for desktop
#    tests only; protected test builds never run these renames (tests run on
#    unprotected variants), so renames are OK.
#  - technicalId strings are data (KvStore keys), not class lookups — unaffected.
#  - lsparanoid already excludes these same entry classes from string protection.

# ── Entry points looked up by fixed name from outside the JVM ──────────────────

# Legacy Xposed API entry (assets/xposed_init)
-keep class dev.ujhhgtg.wekit.loader.entry.xp51.Xp51HookEntry { *; }

# libxposed entry (standard flavor, META-INF/xposed/java_init.list)
-keep class dev.ujhhgtg.wekit.loader.entry.lxp.LxpHookEntry { *; }

# Zygisk entries loaded by name through the InMemoryDexClassLoader and
# registered via native RegisterNatives (wekit-zygisk natives.rs).
-keep class dev.ujhhgtg.wekit.loader.entry.zygisk.ZygiskEntry { *; }
-keep class dev.ujhhgtg.wekit.loader.entry.zygisk.ArtHookBridge { *; }
-keep class dev.ujhhgtg.wekit.loader.entry.zygisk.ArtHookBridgeRuntime** { *; }
-keep class dev.ujhhgtg.wekit.loader.entry.zygisk.ZygiskLoaderService { *; }

# lsparanoid string-decoder bootstrap: native side resolves it by name.
-keep class dev.ujhhgtg.lsparanoid.generated.LspBootstrap { *; }

# BuildConfig is read by the native decoder and by early startup.
-keep class dev.ujhhgtg.wekit.BuildConfig { *; }

# ── Native-lookup helpers ─────────────────────────────────────────────────────

# Classes owning `external fun` declarations must keep their names and the
# native method names: JNI resolves them from the registered method table.
-keepclasseswithmembers class dev.ujhhgtg.wekit.** {
    native <methods>;
}

# ── Serialization & reflection safety nets ────────────────────────────────────

# kotlinx-serialization serializers are generated companions; keep their names so
# the generated serializer() lookups survive renaming.
-keepclassmembers class dev.ujhhgtg.wekit.** {
    *** Companion;
}
-keepclasseswithmembers class dev.ujhhgtg.wekit.** {
    kotlinx.serialization.KSerializer serializer(...);
}
