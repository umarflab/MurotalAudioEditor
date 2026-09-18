package id.umarflab.murotalaudioeditor

import java.nio.ByteBuffer

/** Instance handles are confined to the export coroutine and closed in finally. */
internal object Mp3Encoder {
    init { System.loadLibrary("audio_mp3") }
    external fun create(bitrate: Int): Long
    external fun encode(handle: Long, pcm: ByteBuffer, frames: Int, output: ByteArray): Int
    external fun flush(handle: Long, output: ByteArray): Int
    external fun close(handle: Long)
}
