package pl.prawko.app.data.media

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

internal val MediaVersionPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")
internal val MediaPathPattern = Regex("media/([0-9a-f]{64})\\.(jpg|mp4)")
private val DigestPattern = Regex("[0-9a-f]{64}")
private const val MAX_ARCHIVE_SIZE = 128L * 1024 * 1024
private const val MAX_MEDIA_BYTES = 64L * 1024 * 1024
private const val MAX_MANIFEST_SIZE = 64L * 1024
private const val MAX_FILE_COUNT = 128
private const val MANIFEST_ENTRY = "manifest.json"

data class MediaPackInstallation(val packId: String, val fileCount: Int)

internal data class MediaPackManifest(
    val schemaVersion: Int,
    val datasetVersion: String,
    val packId: String,
    val files: List<MediaPackFile>,
)

internal data class MediaPackFile(val path: String, val sizeBytes: Long)

internal class MediaPackCache(
    private val root: File,
    private val parseManifest: (String) -> MediaPackManifest,
) {
    fun locate(datasetVersion: String, path: String): File? {
        if (!MediaVersionPattern.matches(datasetVersion) || !MediaPathPattern.matches(path)) return null

        return File(root, datasetVersion).listFiles().orEmpty().asSequence()
            .filter { pack -> DigestPattern.matches(pack.name) && pack.isDirectory }
            .map { pack -> File(pack, path) }
            .firstOrNull { asset -> asset.isFile && asset.length() in 1..MAX_MEDIA_BYTES }
    }

    fun install(
        datasetVersion: String,
        archive: InputStream,
        expectedZipSha256: String,
    ): MediaPackInstallation {
        require(MediaVersionPattern.matches(datasetVersion)) { "Nieprawidłowa wersja katalogu." }
        require(DigestPattern.matches(expectedZipSha256)) { "Nieprawidłowy SHA-256 archiwum." }
        val versionFolder = File(root, datasetVersion)
        ensureDirectory(versionFolder)

        val work = File(versionFolder, ".pack-" + UUID.randomUUID())
        if (!work.mkdir()) throw IOException("Nie można utworzyć katalogu roboczego paczki.")
        try {
            val checkedZip = File(work, "archive.zip")
            val actualHash = checkedZip.outputStream().buffered().use { output ->
                copyAndHash(archive, output, MAX_ARCHIVE_SIZE).hash
            }
            require(actualHash == expectedZipSha256) { "Suma SHA-256 archiwum się nie zgadza." }

            val candidate = File(work, "verified")
            ensureDirectory(File(candidate, "media"))
            val manifest = unpack(checkedZip, candidate, datasetVersion)
            val destination = File(versionFolder, expectedZipSha256)
            if (destination.exists()) {
                // Android may evict individual files from cache between two installs.
                if (!manifest.files.all { assetIsVerified(destination, it) }) {
                    if (!destination.renameTo(File(work, "evicted"))) {
                        throw IOException("Nie można odświeżyć wcześniejszej paczki mediów.")
                    }
                }
            }
            if (!destination.isDirectory && !candidate.renameTo(destination)) {
                throw IOException("Nie można umieścić paczki w pamięci podręcznej.")
            }
            return MediaPackInstallation(manifest.packId, manifest.files.size)
        } finally {
            work.deleteRecursively()
        }
    }

    private fun unpack(zip: File, target: File, datasetVersion: String): MediaPackManifest =
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            require(input.nextEntry?.name == MANIFEST_ENTRY) { "Brakuje pierwszego wpisu manifest.json." }
            val manifestText = input.readAtMost(MAX_MANIFEST_SIZE)
            val manifest = parseManifest(manifestText)
            validateManifest(manifest, datasetVersion)

            val expected = manifest.files.associateByTo(linkedMapOf()) { it.path }
            var mediaDirectorySeen = false
            while (true) {
                val entry = input.nextEntry ?: break
                if (entry.name == "media/" && entry.isDirectory && !mediaDirectorySeen) {
                    mediaDirectorySeen = true
                    require(input.read() == -1) { "Wpis katalogu mediów zawiera dane." }
                    continue
                }
                val file = requireNotNull(expected.remove(entry.name)) {
                    "Paczka zawiera nadmiarowy lub powielony plik."
                }
                val expectedHash = requireNotNull(MediaPathPattern.matchEntire(file.path)).groupValues[1]
                val receipt = File(target, file.path).outputStream().buffered().use { output ->
                    copyAndHash(input, output, file.sizeBytes)
                }
                require(receipt.size == file.sizeBytes && receipt.hash == expectedHash) {
                    "Plik w paczce ma inny skrót lub rozmiar niż w manifeście."
                }
            }
            require(expected.isEmpty()) { "W paczce brakuje zadeklarowanych plików." }
            manifest
        }

    private fun validateManifest(manifest: MediaPackManifest, datasetVersion: String) {
        require(manifest.schemaVersion == 1) { "Nieobsługiwana wersja manifestu mediów." }
        require(manifest.datasetVersion == datasetVersion) { "Paczka używa innej wersji katalogu." }
        require(MediaVersionPattern.matches(manifest.packId)) { "Nieprawidłowy identyfikator paczki." }
        require(manifest.files.size in 1..MAX_FILE_COUNT) { "Nieprawidłowa liczba plików w paczce." }
        require(manifest.files.map { it.path }.distinct().size == manifest.files.size) {
            "Manifest zawiera powielone ścieżki."
        }
        require(manifest.files.all {
            MediaPathPattern.matches(it.path) && it.sizeBytes in 1..MAX_MEDIA_BYTES
        }) { "Nieprawidłowa nazwa lub rozmiar pliku w manifeście." }
        require(manifest.files.sumOf { it.sizeBytes } <= MAX_ARCHIVE_SIZE) {
            "Paczka rozpakowuje się do rozmiaru powyżej 128 MiB."
        }
    }

    private fun assetIsVerified(root: File, asset: MediaPackFile): Boolean {
        val file = File(root, asset.path)
        if (!file.isFile || file.length() != asset.sizeBytes) return false

        return file.inputStream().buffered().use { source ->
            val digest = copyAndHash(source, null, asset.sizeBytes).hash
            digest == MediaPathPattern.matchEntire(asset.path)?.groupValues?.get(1)
        }
    }
}

private fun InputStream.readAtMost(limit: Long): String {
    val output = java.io.ByteArrayOutputStream()
    copyAndHash(this, output, limit)
    return output.toString(Charsets.UTF_8.name())
}

private fun ensureDirectory(file: File) {
    if (!file.isDirectory && !file.mkdirs() && !file.isDirectory) {
        throw IOException("Nie można utworzyć katalogu pamięci podręcznej.")
    }
}

private data class CopyReceipt(val size: Long, val hash: String)

private fun copyAndHash(source: InputStream, target: OutputStream?, limit: Long): CopyReceipt {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var size = 0L
    while (true) {
        val count = source.read(buffer)
        if (count < 0) break
        size += count
        require(size <= limit) { "Dane przekraczają dozwolony rozmiar paczki." }
        digest.update(buffer, 0, count)
        target?.write(buffer, 0, count)
    }
    return CopyReceipt(size, digest.digest().joinToString("") { "%02x".format(it) })
}
