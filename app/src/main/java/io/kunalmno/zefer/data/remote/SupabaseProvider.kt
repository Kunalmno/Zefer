package io.kunalmno.zefer.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.kunalmno.zefer.BuildConfig
import kotlinx.serialization.json.Json

/** The single Supabase client for the whole app. URL + key come from local.properties via BuildConfig. */
object SupabaseProvider {

    val client: SupabaseClient by lazy {
        require(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()) {
            "Supabase URL / key are empty. Check local.properties, then Sync Gradle and rebuild."
        }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
            install(Postgrest)
        }
    }

    /** Public URL of a file in the `product-images` bucket. */
    fun imageUrl(path: String): String =
        "${BuildConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/public/product-images/$path"
}
