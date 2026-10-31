package com.omniplayer.core
import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

object DisplaySettings {
    val backgroundChoices = linkedMapOf(
        "雾蓝" to 0xFFF3F8FA,
        "暖白" to 0xFFFAF7F0,
        "浅绿" to 0xFFF1F7F2,
        "浅灰" to 0xFFF2F3F5,
        "淡紫" to 0xFFF5F1F8
    )
    private val faces=android.util.LruCache<String,Typeface>(4)
    fun scale(c: Context) = SettingsStore.prefs(c).getFloat("font-scale", 1f).coerceIn(.75f, 1.75f)
    fun face(c: Context) = SettingsStore.prefs(c).getString("font-face", "黑体") ?: "黑体"
    fun background(c: Context) = androidx.compose.ui.graphics.Color(
        backgroundChoices[SettingsStore.prefs(c).getString("background-preset", "雾蓝")] ?: backgroundChoices.getValue("雾蓝")
    )
    private fun file(c: Context, face: String): File? {
        if (face == "自定义") return File(c.filesDir, "custom-font.ttf").takeIf { it.exists() }
        val asset = when (face) { "楷体" -> "Kai.ttf"; "宋体" -> "Song.ttf"; else -> return null }
        return File(c.filesDir, asset).also { if (!it.exists()) c.assets.open("fonts/$asset").use { input -> it.outputStream().use { out -> input.copyTo(out) } } }
    }
    fun family(c: Context, face: String): FontFamily = runCatching { file(c, face)?.let { FontFamily(Font(it)) } ?: FontFamily.SansSerif }.getOrDefault(FontFamily.SansSerif)
    fun typeface(c: Context): Typeface = runCatching {
        val f=file(c,face(c)) ?: return@runCatching Typeface.DEFAULT
        val key=f.absolutePath+":"+f.lastModified()
        faces.get(key) ?: Typeface.createFromFile(f).also { faces.put(key,it) }
    }.getOrDefault(Typeface.DEFAULT)
}

@Composable
internal fun AppTheme(colorScheme: ColorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF087F79), background = androidx.compose.ui.graphics.Color(0xFFF3F8FA)), content: @Composable () -> Unit) {
    val c = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(c) {
        val prefs = SettingsStore.prefs(c)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "font-scale" || key == "font-face" || key == "background-preset") revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val typography = remember(revision) {
        val scale = DisplaySettings.scale(c); val family = DisplaySettings.family(c, DisplaySettings.face(c))
        fun TextStyle.adjust() = copy(fontFamily = family, fontSize = fontSize * scale, lineHeight = lineHeight * scale)
        val t = Typography()
        t.copy(displayLarge=t.displayLarge.adjust(), displayMedium=t.displayMedium.adjust(), displaySmall=t.displaySmall.adjust(),
            headlineLarge=t.headlineLarge.adjust(),headlineMedium=t.headlineMedium.adjust(),headlineSmall=t.headlineSmall.adjust(),
            titleLarge=t.titleLarge.adjust(),titleMedium=t.titleMedium.adjust(),titleSmall=t.titleSmall.adjust(),
            bodyLarge=t.bodyLarge.adjust(),bodyMedium=t.bodyMedium.adjust(),bodySmall=t.bodySmall.adjust(),
            labelLarge=t.labelLarge.adjust(),labelMedium=t.labelMedium.adjust(),labelSmall=t.labelSmall.adjust())
    }
    MaterialTheme(colorScheme=colorScheme.copy(background=DisplaySettings.background(c)), typography=typography, content=content)
}

@Composable
internal fun BackgroundSettings() {
    val c=LocalContext.current
    val prefs=SettingsStore.prefs(c)
    var selected by remember { mutableStateOf(prefs.getString("background-preset", "雾蓝") ?: "雾蓝") }
    Text("软件背景颜色", style=MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        DisplaySettings.backgroundChoices.forEach { (name, value) ->
            FilterChip(selected=selected==name, onClick={selected=name;prefs.edit().putString("background-preset",name).apply()}, label={Text(name)}, leadingIcon={
                androidx.compose.foundation.Canvas(Modifier.size(16.dp)) { drawCircle(androidx.compose.ui.graphics.Color(value)) }
            })
        }
    }
}

@Composable
internal fun FontSettings(onImport: () -> Unit) {
    val c=LocalContext.current; val prefs=SettingsStore.prefs(c)
    var scale by remember { mutableFloatStateOf(DisplaySettings.scale(c)) }
    var face by remember { mutableStateOf(DisplaySettings.face(c)) }
    Text("显示与字体",style=MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        listOf("小" to .85f,"标准" to 1f,"大" to 1.15f,"超大" to 1.3f).forEach { (name,value) ->
            TextButton(onClick={ scale=value; prefs.edit().putFloat("font-scale",scale).apply() }) { Text(if (scale==value) "✓ $name" else name) }
        }
    }
    Text("自定义字号：${(scale*100).toInt()}%")
    Slider(value=scale,onValueChange={ scale=it; prefs.edit().putFloat("font-scale",it).apply() },valueRange=.75f..1.75f)
    Row { listOf("黑体","宋体","楷体").forEach { name -> TextButton(onClick={face=name; prefs.edit().putString("font-face",name).apply()}) { Text(if(face==name) "✓ $name" else name) } } }
    OutlinedButton(onClick=onImport) { Text("导入自定义字体（TTF / OTF）") }
    Text("字体预览：磁力播放器 123 ABC", style=MaterialTheme.typography.bodyLarge)
    Text("设置立即生效。黑体采用系统字体，宋体采用思源宋体，楷体采用霞鹜文楷。",style=MaterialTheme.typography.bodySmall)
}
