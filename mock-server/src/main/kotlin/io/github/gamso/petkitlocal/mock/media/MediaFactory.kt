package io.github.gamso.petkitlocal.mock.media

import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * Placeholder media, generated: JPEGs drawn from a seed (a gradient and a few shapes, no text, no
 * PetKit artwork), and one tiny synthetic MP4 (ffmpeg's `testsrc2`, 2 s, 160x120, ~9 KB) bundled
 * as a resource. Cached, since the same few images are asked for again and again.
 */
object MediaFactory {
    private val cache = ConcurrentHashMap<String, ByteArray>()

    private val palette =
        listOf(
            Color(0x3D5A80) to Color(0x98C1D9),
            Color(0x6D597A) to Color(0xE56B6F),
            Color(0x2A9D8F) to Color(0xE9C46A),
            Color(0x264653) to Color(0xF4A261),
            Color(0x5F0F40) to Color(0xFB8B24),
            Color(0x386641) to Color(0xA7C957),
        )

    val clip: ByteArray by lazy {
        MediaFactory::class.java.getResourceAsStream("/media/clip.mp4")?.use { it.readBytes() }
            ?: error("media/clip.mp4 is missing from the classpath")
    }

    fun jpeg(
        width: Int,
        height: Int,
        seed: Int,
    ): ByteArray =
        cache.getOrPut("$width:$height:${Math.floorMod(seed, palette.size * 4)}") {
            draw(width, height, Math.floorMod(seed, palette.size * 4))
        }

    private fun draw(
        width: Int,
        height: Int,
        seed: Int,
    ): ByteArray {
        val (from, to) = palette[seed % palette.size]
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.paint = GradientPaint(0f, 0f, from, width.toFloat(), height.toFloat(), to)
            g.fillRect(0, 0, width, height)
            // A round "subject" in the middle and a floor line: enough to tell images apart.
            g.color = Color(255, 255, 255, 170)
            val r = height / 3 + (seed / palette.size) * height / 24
            g.fillOval(width / 2 - r, height / 2 - r, 2 * r, 2 * r)
            g.color = Color(0, 0, 0, 90)
            g.fillRect(0, height * 4 / 5, width, height / 5)
        } finally {
            g.dispose()
        }
        val out = ByteArrayOutputStream()
        check(ImageIO.write(image, "jpg", out)) { "no JPEG writer" }
        return out.toByteArray()
    }
}
