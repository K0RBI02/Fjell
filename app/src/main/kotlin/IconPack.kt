package dev.korbi.fjell

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Reads an ADW-format icon pack (appfilter.xml / drawable.xml in assets or res/xml). */
@SuppressLint("DiscouragedApi") // Icon packs are addressed by resource name by design.
class IconPack(context: Context, val packageName: String) {
    private val res: Resources = context.packageManager.getResourcesForApplication(packageName)
    private val byComponent = HashMap<String, String>()
    private val byPackage = HashMap<String, String>()
    private val mappedNames = LinkedHashSet<String>()

    init {
        val parsed = withXml("appfilter") { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "item" -> {
                        val component = parser.getAttributeValue(null, "component") ?: continue
                        val drawable = parser.getAttributeValue(null, "drawable") ?: continue
                        mappedNames += drawable
                        val cn = parseComponent(component) ?: continue
                        byComponent.putIfAbsent(cn.flattenToString(), drawable)
                        byPackage.putIfAbsent(cn.packageName, drawable)
                    }
                }
            }
        }
        require(parsed) { "$packageName has no appfilter.xml" }
    }

    /** Every icon the pack offers, for picking one by hand. Prefers the pack's own catalogue (drawable.xml). */
    val iconNames: List<String> by lazy {
        val catalogue = LinkedHashSet<String>()
        withXml("drawable") { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    parser.getAttributeValue(null, "drawable")?.let(catalogue::add)
                }
            }
        }
        (catalogue.ifEmpty { mappedNames }).toList()
    }

    /** The pack's icon for [app], if it has one. */
    fun matched(app: AppEntry): String? = byComponent[app.component.flattenToString()] ?: byPackage[app.component.packageName]

    fun icon(name: String, size: Int): Bitmap? = drawable(name)?.let { IconRender.render(it, size) }

    private fun drawable(name: String): Drawable? {
        val id = res.getIdentifier(name, "drawable", packageName)
        if (id == 0) return null
        return runCatching { res.getDrawable(id, null) }.getOrNull()
    }

    /** Runs [read] on assets/<name>.xml, or res/xml/<name>.xml. False if neither exists. */
    private fun withXml(name: String, read: (XmlPullParser) -> Unit): Boolean {
        val asset = runCatching { res.assets.open("$name.xml") }.getOrNull()
        if (asset != null) {
            asset.use {
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(it, "UTF-8")
                read(parser)
            }
            return true
        }
        val id = res.getIdentifier(name, "xml", packageName)
        if (id == 0) return false
        res.getXml(id).use(read)
        return true
    }

    /** "ComponentInfo{pkg/cls}" -> ComponentName, expanding ".Relative" class names. */
    private fun parseComponent(raw: String): ComponentName? {
        val inner = raw.substringAfter('{', "").substringBefore('}', "")
        val pkg = inner.substringBefore('/', "")
        var cls = inner.substringAfter('/', "")
        if (pkg.isEmpty() || cls.isEmpty()) return null
        if (cls.startsWith('.')) cls = pkg + cls
        return ComponentName(pkg, cls)
    }

    companion object {
        const val NOTHING = "com.nothing.icon.free"

        fun loadOrNull(context: Context, pkg: String): IconPack? = runCatching { IconPack(context, pkg) }.getOrNull()

        /** Package names of installed apps that announce themselves as icon packs. */
        fun installedPacks(context: Context): List<String> {
            val pm = context.packageManager
            return PACK_ACTIONS
                .flatMap { pm.queryIntentActivities(Intent(it), 0) }
                .map { it.activityInfo.packageName }
                .distinct()
        }

        private val PACK_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.gau.go.launcherex.theme",
        )
    }
}
