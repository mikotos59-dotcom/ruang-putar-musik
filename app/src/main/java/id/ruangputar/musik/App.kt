package id.ruangputar.musik

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import id.ruangputar.musik.inti.Jaringan
import id.ruangputar.musik.inti.Penyimpanan
import id.ruangputar.musik.inti.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        aku = this
        YouTube.mulai()
        data = Penyimpanan(File(filesDir, "data.json"), scope)
        scope.launch {
            data.pengaturan.collect { Jaringan.pembatas.batas = it.batasByte }
        }
    }

    /** Cache unduh-sambil-putar: lagu yang pernah diputar bisa diulang tanpa internet. */
    val cache: SimpleCache by lazy {
        SimpleCache(
            File(cacheDir, "lagu"),
            LeastRecentlyUsedCacheEvictor(data.pengaturan.value.cacheMb.toLong() * 1024 * 1024),
            StandaloneDatabaseProvider(this),
        )
    }

    companion object {
        lateinit var aku: App
            private set
        lateinit var data: Penyimpanan
            private set
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
