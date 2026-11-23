package pl.prawko.app.data

import android.content.Context
import pl.prawko.app.data.importing.LocalCatalogRepository
import pl.prawko.app.data.local.PrawkoDatabase
import pl.prawko.app.ui.settings.LocalCatalogSettings

internal fun createCatalogPreview(context: Context, database: () -> PrawkoDatabase): CatalogPreview {
    val catalog = LocalCatalogRepository(context.contentResolver, database())
    return CatalogPreview(catalog.questions) { LocalCatalogSettings(catalog) }
}
