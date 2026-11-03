package com.movtery.zalithlauncher.ui.screens.content.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.movtery.zalithlauncher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Horizon — Minecraft Java 26.3 на Rust, неофициальный порт на Android (github.com/Vasysik/horizon-android, MIT).
 * Мы его не встраиваем в себя: скачиваем их APK с GitHub автора, ставим и запускаем как отдельное приложение.
 */
object Horizon {
    const val PACKAGE = "dev.horizon.android"
    private const val LATEST = "https://api.github.com/repos/Vasysik/horizon-android/releases/latest"
    private val http by lazy { OkHttpClient() }

    fun isInstalled(context: Context) = context.packageManager.getLaunchIntentForPackage(PACKAGE) != null

    fun launch(context: Context) {
        context.packageManager.getLaunchIntentForPackage(PACKAGE)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Имя файла подходит к процессору устройства: arm64 не путаем с armv7, а x86 с x86_64 */
    fun matchesAbi(name: String, abi: String): Boolean {
        val n = name.lowercase()
        return when (abi) {
            "arm64-v8a" -> listOf("arm64", "aarch64").any { it in n }
            "armeabi-v7a" -> listOf("armv7", "armeabi-v7a", "arm32").any { it in n }
            "x86_64" -> listOf("x86_64", "x86-64", "x64").any { it in n }
            "x86" -> "x86" in n && listOf("x86_64", "x86-64").none { it in n }
            else -> false
        }
    }

    private fun get(url: String) = Request.Builder().url(url)
        .header("User-Agent", "Voxa-Android")
        .header("Accept", if ("api.github.com" in url) "application/vnd.github+json" else "*/*")
        .build()

    /** Скачивает свежий APK под это устройство; сверяет SHA-256, если автор выложил файл с суммами */
    suspend fun download(context: Context, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val release = http.newCall(get(LATEST)).execute().use { r ->
            if (!r.isSuccessful) error("GitHub ответил ${r.code}")
            JSONObject(r.body.string())
        }
        val assets = release.getJSONArray("assets")
        val all = (0 until assets.length()).map { assets.getJSONObject(it) }
        val apk = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi ->
            all.firstOrNull { it.getString("name").endsWith(".apk") && matchesAbi(it.getString("name"), abi) }
        } ?: all.firstOrNull { it.getString("name").endsWith(".apk") }
            ?: error("В релизе ${release.optString("tag_name")} нет APK для этого устройства")
        val name = File(apk.getString("name")).name
        val dir = File(context.externalCacheDir ?: context.cacheDir, "horizon").apply { mkdirs() }
        val file = File(dir, name)

        val digest = MessageDigest.getInstance("SHA-256")
        http.newCall(get(apk.getString("browser_download_url"))).execute().use { r ->
            if (!r.isSuccessful) error("Не скачалось: ${r.code}")
            val total = r.body.contentLength().takeIf { it > 0 } ?: apk.optLong("size", -1)
            var done = 0L
            r.body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        }

        val sums = all.firstOrNull { it.getString("name").contains("SHA256", ignoreCase = true) }
        if (sums != null) {
            val text = http.newCall(get(sums.getString("browser_download_url"))).execute().use { it.body.string() }
            val expected = text.lines().firstOrNull { it.trim().endsWith(name) }?.trim()?.substringBefore(' ')?.lowercase()
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (expected != null && expected != actual) {
                file.delete()
                error("Файл повреждён или подменён, контрольная сумма не совпала")
            }
        }
        file
    }

    /** Открывает системную установку; без разрешения на установку ведёт в настройки, чтобы его выдать */
    fun install(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}

/** Карточка Horizon в ленте сборок: ставит или запускает Horizon */
@Composable
fun HorizonCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(Horizon.isInstalled(context)) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var downloaded by remember { mutableStateOf<File?>(null) }

    // вернулись из установщика или настроек — проверяем заново
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { installed = Horizon.isInstalled(context) }

    val subtitle = when {
        progress != null -> stringResource(R.string.showcase_horizon_downloading, ((progress ?: 0f) * 100).toInt())
        installed -> stringResource(R.string.showcase_horizon_open)
        downloaded != null -> stringResource(R.string.showcase_horizon_install)
        else -> stringResource(R.string.showcase_horizon_get)
    }

    Box(
        modifier = Modifier
            .width(196.dp)
            .height(158.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF7C6CFF), Color(0xFF1A1440))))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .clickable(enabled = progress == null) {
                when {
                    installed -> Horizon.launch(context)
                    downloaded != null -> Horizon.install(context, downloaded!!)
                    else -> scope.launch {
                        progress = 0f
                        try {
                            val file = Horizon.download(context) { p -> progress = p }
                            downloaded = file
                            progress = null
                            Horizon.install(context, file)
                        } catch (e: Exception) {
                            progress = null
                            Toast.makeText(context, e.message ?: e.toString(), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .padding(14.dp)
    ) {
        Text(
            modifier = Modifier
                .align(Alignment.TopStart)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 9.dp, vertical = 3.dp),
            text = stringResource(R.string.showcase_horizon_tag),
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            Text(text = "Horizon", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1)
            Text(text = subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
            progress?.let {
                LinearProgressIndicator(
                    progress = { it },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(CircleShape),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
            }
        }
    }
}
