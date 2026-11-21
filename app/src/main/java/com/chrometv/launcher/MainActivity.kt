package com.chrometv.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView

data class LauncherCard(
    val id: String,
    val indexText: String,
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val candidatePackages: List<String>,
    val isMediaHub: Boolean = false,
    val isSettings: Boolean = false
)

data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

class MainActivity : AppCompatActivity() {

    private lateinit var tvTopIndex: TextView
    private lateinit var tvTopSection: TextView
    private lateinit var ivStatusWifi: ImageView
    private lateinit var tvStatusWifi: TextView
    private lateinit var ivStatusVpn: ImageView
    private lateinit var tvStatusVpn: TextView
    private lateinit var rvCarousel: RecyclerView
    private lateinit var appsOverlay: LinearLayout
    private lateinit var tvOverlayTitle: TextView
    private lateinit var rvAppsGrid: RecyclerView

    private lateinit var soundPool: SoundPool
    private var sndNav = 0
    private var sndConfirm = 0
    private var sndBack = 0

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var bindingCardId: String? = null
    private var lastFocusedIndex = 3 // Start centered on MEDIA HUB!

    private val prefs by lazy { getSharedPreferences("chrome_tv_prefs", Context.MODE_PRIVATE) }

    private val cards = listOf(
        LauncherCard(
            id = "youtube",
            indexText = "01",
            title = "YOUTUBE",
            subtitle = "STREAM VIDEO",
            iconRes = R.drawable.ic_youtube,
            candidatePackages = listOf(
                "com.teamsmart.videomanager.tv",
                "com.liskovsoft.smarttubetv.beta",
                "com.liskovsoft.smarttubetv",
                "com.google.android.youtube.tv"
            )
        ),
        LauncherCard(
            id = "vk_video",
            indexText = "02",
            title = "VK VIDEO",
            subtitle = "CHANNELS & SHOWS",
            iconRes = R.drawable.ic_vk_video,
            candidatePackages = listOf(
                "com.vk.vkvideo",
                "com.vk.tv",
                "com.vkontakte.android"
            )
        ),
        LauncherCard(
            id = "vpn",
            indexText = "03",
            title = "VPN",
            subtitle = "SECURE TUNNEL",
            iconRes = R.drawable.ic_vpn,
            candidatePackages = listOf(
                "com.browsec.vpn",
                "org.amnezia.vpn",
                "com.wireguard.android",
                "com.v2ray.ang"
            )
        ),
        LauncherCard(
            id = "media_hub",
            indexText = "04",
            title = "MEDIA HUB",
            subtitle = "SYSTEM ENTER",
            iconRes = R.drawable.ic_media_hub,
            candidatePackages = emptyList(),
            isMediaHub = true
        ),
        LauncherCard(
            id = "browser",
            indexText = "05",
            title = "BROWSER",
            subtitle = "WEB ACCESS",
            iconRes = R.drawable.ic_browser,
            candidatePackages = listOf(
                "com.phlox.tvwebbrowser",
                "com.yandex.browser",
                "com.android.chrome",
                "com.seraphic.openinet.pre"
            )
        ),
        LauncherCard(
            id = "settings",
            indexText = "06",
            title = "SETTINGS",
            subtitle = "SYSTEM CONFIG",
            iconRes = R.drawable.ic_settings,
            candidatePackages = emptyList(),
            isSettings = true
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTopIndex = findViewById(R.id.tvTopIndex)
        tvTopSection = findViewById(R.id.tvTopSection)
        ivStatusWifi = findViewById(R.id.ivStatusWifi)
        tvStatusWifi = findViewById(R.id.tvStatusWifi)
        ivStatusVpn = findViewById(R.id.ivStatusVpn)
        tvStatusVpn = findViewById(R.id.tvStatusVpn)
        rvCarousel = findViewById(R.id.rvCarousel)
        appsOverlay = findViewById(R.id.appsOverlay)
        tvOverlayTitle = findViewById(R.id.tvOverlayTitle)
        rvAppsGrid = findViewById(R.id.rvAppsGrid)

        initSounds()
        setupCarousel()
        setupNetworkMonitor()
    }

    private fun initSounds() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()
        sndNav = soundPool.load(this, R.raw.snd_nav, 1)
        sndConfirm = soundPool.load(this, R.raw.snd_confirm, 1)
        sndBack = soundPool.load(this, R.raw.snd_back, 1)
    }

    private fun playSound(id: Int) {
        if (id != 0) soundPool.play(id, 0.80f, 0.80f, 1, 0, 1.0f)
    }

    private fun setupCarousel() {
        val dm = resources.displayMetrics
        val cardPx = (236 * dm.density).toInt()
        val sidePad = ((dm.widthPixels - cardPx) / 2).coerceAtLeast(80)
        rvCarousel.setPadding(sidePad, 0, sidePad, 0)

        val lm = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvCarousel.layoutManager = lm
        rvCarousel.adapter = CarouselAdapter()
        rvCarousel.scrollToPosition(lastFocusedIndex)
        rvCarousel.post {
            centerScrollTo(lastFocusedIndex)
            rvCarousel.postDelayed({
                rvCarousel.findViewHolderForAdapterPosition(lastFocusedIndex)?.itemView?.requestFocus()
            }, 80)
        }
    }

    private fun centerScrollTo(position: Int) {
        val lm = rvCarousel.layoutManager as? LinearLayoutManager ?: return
        val scroller = object : LinearSmoothScroller(this) {
            override fun calculateDtToFit(
                viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int
            ): Int {
                return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
            }
            override fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float {
                return 85f / displayMetrics.densityDpi
            }
        }
        scroller.targetPosition = position
        lm.startSmoothScroll(scroller)
    }

    private fun setupNetworkMonitor() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        updateNetworkIcons()
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = runOnUiThread { updateNetworkIcons() }
            override fun onLost(network: Network) = runOnUiThread { updateNetworkIcons() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                runOnUiThread { updateNetworkIcons() }
        }
        networkCallback = cb
        try {
            connectivityManager?.registerNetworkCallback(req, cb)
        } catch (_: Exception) {}
    }

    private fun updateNetworkIcons() {
        val cm = connectivityManager ?: return
        var wifiOn = false
        var vpnOn = false
        for (net in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(net) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
                wifiOn = true
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                vpnOn = true
            }
        }
        val activeColor = Color.parseColor("#7CF2FF")
        val dimColor = Color.parseColor("#667A8C")

        ivStatusWifi.setColorFilter(if (wifiOn) activeColor else dimColor)
        tvStatusWifi.setTextColor(if (wifiOn) activeColor else dimColor)
        ivStatusWifi.alpha = if (wifiOn) 1.0f else 0.45f

        ivStatusVpn.setColorFilter(if (vpnOn) activeColor else dimColor)
        tvStatusVpn.setTextColor(if (vpnOn) activeColor else dimColor)
        ivStatusVpn.alpha = if (vpnOn) 1.0f else 0.45f
    }

    private fun handleCardClick(card: LauncherCard) {
        playSound(sndConfirm)
        if (card.isSettings) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
            return
        }
        if (card.isMediaHub) {
            openAppsOverlay(null)
            return
        }
        val savedPkg = prefs.getString("bind_${card.id}", null)
        val pkgToLaunch = savedPkg ?: card.candidatePackages.firstOrNull { isInstalled(it) }
        if (pkgToLaunch != null && launchPackage(pkgToLaunch)) {
            return
        }
        Toast.makeText(this, "Выберите приложение для «${card.title}»", Toast.LENGTH_SHORT).show()
        openAppsOverlay(card.id)
    }

    private fun isInstalled(pkg: String): Boolean {
        return try {
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun launchPackage(pkg: String): Boolean {
        val leanback = packageManager.getLeanbackLaunchIntentForPackage(pkg)
        val regular = packageManager.getLaunchIntentForPackage(pkg)
        val intent = leanback ?: regular ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return true
    }

    private fun loadInstalledApps(): List<InstalledApp> {
        val pm = packageManager
        val seen = LinkedHashMap<String, InstalledApp>()
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        )
        for (intent in intents) {
            val list = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            for (ri in list) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || seen.containsKey(pkg)) continue
                seen[pkg] = InstalledApp(
                    label = ri.loadLabel(pm).toString(),
                    packageName = pkg,
                    icon = ri.loadIcon(pm)
                )
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    private fun openAppsOverlay(bindForCardId: String?) {
        bindingCardId = bindForCardId
        tvOverlayTitle.text = if (bindForCardId == null) {
            "ALL APPLICATIONS"
        } else {
            val cardName = cards.firstOrNull { it.id == bindForCardId }?.title ?: "CARD"
            "BIND APP FOR $cardName"
        }
        val apps = loadInstalledApps()
        rvAppsGrid.layoutManager = GridLayoutManager(this, 3)
        rvAppsGrid.adapter = AppsGridAdapter(apps) { app ->
            playSound(sndConfirm)
            if (bindingCardId != null) {
                prefs.edit().putString("bind_$bindingCardId", app.packageName).apply()
                Toast.makeText(this, "Привязано: ${app.label}", Toast.LENGTH_SHORT).show()
                closeAppsOverlay()
            } else {
                closeAppsOverlay()
                launchPackage(app.packageName)
            }
        }
        appsOverlay.alpha = 0f
        appsOverlay.scaleX = 0.96f
        appsOverlay.scaleY = 0.96f
        appsOverlay.visibility = View.VISIBLE
        appsOverlay.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
        rvAppsGrid.post {
            rvAppsGrid.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
        }
    }

    private fun closeAppsOverlay() {
        appsOverlay.visibility = View.GONE
        bindingCardId = null
        rvCarousel.post {
            rvCarousel.findViewHolderForAdapterPosition(lastFocusedIndex)?.itemView?.requestFocus()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (appsOverlay.visibility == View.VISIBLE) {
                playSound(sndBack)
                closeAppsOverlay()
                return true
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        updateNetworkIcons()
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            try { connectivityManager?.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        soundPool.release()
    }

    inner class CarouselAdapter : RecyclerView.Adapter<CarouselAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val container: LinearLayout = v.findViewById(R.id.cardContainer)
            val iconPlate: View = v.findViewById(R.id.iconPlate)
            val ivIcon: ImageView = v.findViewById(R.id.ivCardIcon)
            val ivHeroChrome: ImageView = v.findViewById(R.id.ivHeroChrome)
            val tvTitle: TextView = v.findViewById(R.id.tvCardTitle)
            val tvSub: TextView = v.findViewById(R.id.tvCardSub)
            val vDot: View = v.findViewById(R.id.vActiveDot)
            val ivGlintTop: ImageView = v.findViewById(R.id.ivGlintTop)
            val ivGlintBottom: ImageView = v.findViewById(R.id.ivGlintBottom)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_glass_card, parent, false)
            return VH(v)
        }

        override fun getItemCount() = cards.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val card = cards[position]
            holder.tvTitle.text = card.title
            holder.tvSub.text = card.subtitle

            if (card.isMediaHub) {
                holder.ivIcon.visibility = View.GONE
                holder.ivHeroChrome.visibility = View.VISIBLE
            } else {
                holder.ivHeroChrome.visibility = View.GONE
                holder.ivIcon.visibility = View.VISIBLE
                holder.ivIcon.setImageResource(card.iconRes)
            }

            // Initial unfocused depth state
            holder.itemView.scaleX = 0.90f
            holder.itemView.scaleY = 0.90f
            holder.itemView.alpha = 0.62f

            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnFocusChangeListener
                if (hasFocus) {
                    if (lastFocusedIndex != pos) {
                        playSound(sndNav)
                    }
                    lastFocusedIndex = pos
                    tvTopIndex.text = card.indexText
                    tvTopSection.text = card.title
                    holder.container.background =
                        ContextCompat.getDrawable(this@MainActivity, R.drawable.card_glass_focused)
                    holder.tvSub.visibility = View.VISIBLE
                    holder.vDot.visibility = View.VISIBLE
                    holder.ivGlintTop.visibility = View.VISIBLE
                    holder.ivGlintBottom.visibility = View.VISIBLE
                    holder.iconPlate.alpha = 1.0f
                    v.animate()
                        .scaleX(1.18f)
                        .scaleY(1.18f)
                        .alpha(1.0f)
                        .setDuration(190)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                    v.elevation = 24f
                    centerScrollTo(pos)
                } else {
                    holder.container.background =
                        ContextCompat.getDrawable(this@MainActivity, R.drawable.card_glass_normal)
                    holder.tvSub.visibility = View.INVISIBLE
                    holder.vDot.visibility = View.INVISIBLE
                    holder.ivGlintTop.visibility = View.INVISIBLE
                    holder.ivGlintBottom.visibility = View.INVISIBLE
                    holder.iconPlate.alpha = 0.72f
                    v.animate()
                        .scaleX(0.90f)
                        .scaleY(0.90f)
                        .alpha(0.62f)
                        .setDuration(190)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                    v.elevation = 0f
                }
            }

            holder.itemView.setOnClickListener { handleCardClick(card) }
            holder.itemView.setOnLongClickListener {
                if (!card.isMediaHub && !card.isSettings) {
                    playSound(sndConfirm)
                    openAppsOverlay(card.id)
                    true
                } else {
                    false
                }
            }
        }
    }

    inner class AppsGridAdapter(
        private val items: List<InstalledApp>,
        private val onPick: (InstalledApp) -> Unit
    ) : RecyclerView.Adapter<AppsGridAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val iv: ImageView = v.findViewById(R.id.ivAppIcon)
            val tv: TextView = v.findViewById(R.id.tvAppName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_drawer, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = items[position]
            holder.iv.setImageDrawable(app.icon)
            holder.tv.text = app.label
            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) playSound(sndNav)
            }
            holder.itemView.setOnClickListener { onPick(app) }
        }
    }
}
