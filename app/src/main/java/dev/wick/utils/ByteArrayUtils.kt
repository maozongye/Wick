@file:Suppress("NOTHING_TO_INLINE")

package dev.wick.utils

import java.nio.ByteBuffer

inline fun ByteArray.toByteBuffer() = ByteBuffer.wrap(this)

