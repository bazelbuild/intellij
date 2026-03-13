package com.google.idea.testing.bazel

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.collections.contentEquals
import kotlin.io.use
import kotlin.jvm.Throws
import kotlin.text.replace
import kotlin.use

private val EXECUTABLE_MARKER = byteArrayOf(0x45, 0x58)

/**
 * Compress a directory into a ZIP file.
 *
 * Custom ZIP to preserve a file's executbale flag when extraced with [unzip].
 */
@Throws(IOException::class)
fun zip(srcDirectory: Path, outFile: Path) {
    ZipOutputStream(Files.newOutputStream(outFile, StandardOpenOption.CREATE)).use { out ->
        Files.walk(srcDirectory).use { stream ->
            stream.filter(Files::isRegularFile).forEach { file ->
                out.putNextEntry(createEntry(srcDirectory, file))
                Files.newInputStream(file).use { it.transferTo(out) }
            }
        }
    }
}

/**
 * Extracts a ZIP file into a directory.
 *
 * Custom ZIP to preserve a file's executbale flag when compressed with [zip].
 */
@Throws(IOException::class)
fun unzip(srcFile: Path, outDirectory: Path, stripPrefix: Int = 0) {
    ZipInputStream(Files.newInputStream(srcFile)).use { src ->
        for (entry in generateSequence { src.nextEntry }) {
            if (entry.isDirectory) continue

            val path = outDirectory.resolve(Path.of(entry.name).stripPrefix(stripPrefix))
            Files.createDirectories(path.parent)
            Files.newOutputStream(path, StandardOpenOption.CREATE).use(src::transferTo)

            if (entry.isExecutable()) {
                path.toFile().setExecutable(true)
            }
        }
    }
}

private fun Path.stripPrefix(prefix: Int): Path {
    return subpath(prefix, nameCount)
}

private fun createEntry(base: Path, file: Path): ZipEntry {
    val relativePath = base.relativize(file).toString().replace('\\', '/')
    val entry = ZipEntry(relativePath)

    if (Files.isExecutable(file)) {
        entry.extra = EXECUTABLE_MARKER
    }

    return entry
}

private fun ZipEntry.isExecutable(): Boolean {
    return extra != null && extra.contentEquals(EXECUTABLE_MARKER)
}
