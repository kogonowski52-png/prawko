package pl.prawko.app.data.importing

import android.content.res.AssetManager
import android.database.sqlite.SQLiteException
import android.util.Log
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import pl.prawko.app.data.local.PrawkoDatabase
import pl.prawko.app.data.local.installCatalog

internal const val CatalogAssetPath = "catalog/questions.json"
private const val CatalogLogTag = "PrawkoCatalog"

internal suspend fun installBundledCatalog(assets: AssetManager, database: PrawkoDatabase) {
    try {
        val input = try {
            assets.open(CatalogAssetPath, AssetManager.ACCESS_STREAMING)
        } catch (_: FileNotFoundException) {
            return
        }
        val (bundle, payloadSha256) = parseCatalog(input)
        if (database.installCatalog(bundle, payloadSha256)) {
            Log.i(
                CatalogLogTag,
                "Zainstalowano katalog ${bundle.datasetVersion} ${bundle.source}, " +
                    "kategoria ${bundle.category}, pytania: ${bundle.questions.size}",
            )
        }
    } catch (failure: Exception) {
        when (failure) {
            is CancellationException -> throw failure
            is IOException, is SQLiteException, is IllegalArgumentException, is IllegalStateException ->
                Log.e(CatalogLogTag, "Nie udało się zainstalować katalogu pytań.", failure)
            else -> throw failure
        }
    }
}
