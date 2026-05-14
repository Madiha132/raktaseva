package com.example.raktaseva

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // ── Colour palette ──
    private val C_BG         = Color.parseColor("#F7F8FC")
    private val C_SURFACE    = Color.parseColor("#FFFFFF")
    private val C_INPUT      = Color.parseColor("#F0F2F8")
    private val C_RED        = Color.parseColor("#D90429")
    private val C_RED_SOFT   = Color.parseColor("#FFE5E9")
    private val C_GREEN      = Color.parseColor("#1B7F4F")
    private val C_GREEN_SOFT = Color.parseColor("#E3F7ED")
    private val C_BLUE       = Color.parseColor("#3A56E8")
    private val C_BLUE_SOFT  = Color.parseColor("#E8ECFF")
    private val C_ORANGE     = Color.parseColor("#E07B00")
    private val C_TEXT       = Color.parseColor("#12131A")
    private val C_SUBTEXT    = Color.parseColor("#5C5F7A")
    private val C_BORDER     = Color.parseColor("#E4E6F0")
    private val C_WHITE      = Color.parseColor("#FFFFFF")

    private val CHANNEL_ID   = "jeevabindu_fcm"
    private val BLOOD_GROUPS = arrayOf("A+","A-","B+","B-","O+","O-","AB+","AB-")
    private val FILTER_GROUPS= arrayOf("All","A+","A-","B+","B-","O+","O-","AB+","AB-")
    private val sdf          = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // ── SMS config ── Replace with your actual Fast2SMS key from fast2sms.com
    private val FAST2SMS_KEY = "YOUR_FAST2SMS_API_KEY_HERE"
    private val SMS_PERMISSION_CODE = 1001

    private lateinit var panelHome:     LinearLayout
    private lateinit var panelRegister: LinearLayout
    private lateinit var panelAlert:    LinearLayout
    private lateinit var panelDonors:   LinearLayout
    private lateinit var panelHealth:   LinearLayout

    private lateinit var navHome:    LinearLayout
    private lateinit var navReg:     LinearLayout
    private lateinit var navAlert:   LinearLayout
    private lateinit var navDonors:  LinearLayout
    private lateinit var navHealth:  LinearLayout

    private lateinit var layoutAlerts: LinearLayout
    private lateinit var layoutDonors: LinearLayout

    private lateinit var etName:  EditText
    private lateinit var etPhone: EditText
    private lateinit var etAge:   EditText
    private lateinit var etLoc:   EditText
    private lateinit var etOtp:   EditText
    private lateinit var spBlood: Spinner

    private lateinit var spAlertBlood: Spinner
    private lateinit var etHospital:   EditText
    private lateinit var etUnits:      EditText
    private lateinit var etContact:    EditText

    private lateinit var spFilter:      Spinner
    private lateinit var tvFilterCount: TextView
    private lateinit var tvDonorCount:  TextView
    private lateinit var tvAlertCount:  TextView
    private lateinit var btnPickDateTv: TextView
    private lateinit var tvEligResult:  TextView

    private lateinit var rootFrame: FrameLayout

    private var generatedOtp = ""
    private var otpVerified  = false
    private var selectedDate: Calendar? = null

    // ════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotifChannel()

        try {
            FirebaseMessaging.getInstance().subscribeToTopic("blood_alerts")
                .addOnCompleteListener { task ->
                    android.util.Log.d("FCM", if (task.isSuccessful) "Subscribed" else "Failed")
                }
        } catch (e: Exception) {
            android.util.Log.e("FCM", "Init error: ${e.message}")
        }

        buildUI()
        showTab(0)
        ensureSmsPermission()   // ← request SMS permission on startup
    }

    // ════════════════════════════════════════════════════════
    // SMS PERMISSION
    // ════════════════════════════════════════════════════════
    private fun ensureSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SEND_SMS),
                SMS_PERMISSION_CODE
            )
        }
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            android.util.Log.d("SMS", if (granted) "SMS permission granted" else "SMS permission denied — will use Fast2SMS API")
            toast(if (granted) "SMS permission granted ✅" else "SMS will use internet API 📡")
        }
    }

    // ════════════════════════════════════════════════════════
    // SMART SMS DISPATCHER
    // Tries native Android SMS first → falls back to Fast2SMS API
    // ════════════════════════════════════════════════════════
    private fun sendSms(phone: String, message: String) {
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 10) {
            android.util.Log.w("SMS", "Skipped invalid number: $phone")
            return
        }

        if (hasSmsPermission()) {
            // Try native SMS
            val sent = sendViaNativeSms(phone, message)
            if (!sent) {
                android.util.Log.w("SMS", "Native SMS failed → using Fast2SMS API")
                sendViaFast2Sms(phone, message)
            }
        } else {
            // No permission → use API directly
            android.util.Log.d("SMS", "No SEND_SMS permission → using Fast2SMS API")
            sendViaFast2Sms(phone, message)
        }
    }

    // ── Native Android SMS ────────────────────────────────────
    private fun sendViaNativeSms(phone: String, message: String): Boolean {
        return try {
            val normalised = normalisePhone(phone)
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                applicationContext.getSystemService(android.telephony.SmsManager::class.java)
            else @Suppress("DEPRECATION") android.telephony.SmsManager.getDefault()

            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(normalised, null, parts, null, null)
            android.util.Log.d("SMS_NATIVE", "Sent to $normalised")
            true
        } catch (e: Exception) {
            android.util.Log.e("SMS_NATIVE", "Failed: ${e.message}")
            false
        }
    }

    // ── Fast2SMS API (works without SEND_SMS permission) ─────
    // Free 100 SMS/day at fast2sms.com — supports all Indian numbers
    private fun sendViaFast2Sms(phone: String, message: String) {
        val digits = phone.filter { it.isDigit() }.takeLast(10)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(
                    "https://www.fast2sms.com/dev/bulkV2" +
                            "?authorization=${URLEncoder.encode(FAST2SMS_KEY, "UTF-8")}" +
                            "&route=q" +
                            "&message=${URLEncoder.encode(message, "UTF-8")}" +
                            "&language=english" +
                            "&flash=0" +
                            "&numbers=$digits"
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod  = "GET"
                conn.connectTimeout = 10_000
                conn.readTimeout    = 10_000
                val code = conn.responseCode
                val body = conn.inputStream.bufferedReader().readText()
                android.util.Log.d("SMS_API", "Fast2SMS → $code : $body")
            } catch (e: Exception) {
                android.util.Log.e("SMS_API", "Fast2SMS error: ${e.message}")
            }
        }
    }

    // ── Normalise Indian phone numbers to E.164 ───────────────
    private fun normalisePhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return when {
            phone.startsWith("+")                           -> phone
            digits.startsWith("91") && digits.length == 12 -> "+$digits"
            digits.startsWith("0")  && digits.length == 11 -> "+91${digits.substring(1)}"
            digits.length == 10                             -> "+91$digits"
            else                                            -> "+$digits"
        }
    }

    // ════════════════════════════════════════════════════════
    // ROOT UI
    // ════════════════════════════════════════════════════════
    private fun buildUI() {
        rootFrame = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }
        setContentView(rootFrame)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C_BG)
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        rootFrame.addView(root)

        root.addView(buildHeader())

        val scroll = ScrollView(this).apply {
            layoutParams = llp(MATCH, 0, 1f)
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(90))
        }
        scroll.addView(content)

        panelHome     = buildHomePanel()
        panelRegister = buildRegisterPanel()
        panelAlert    = buildAlertPanel()
        panelDonors   = buildDonorsPanel()
        panelHealth   = buildHealthPanel()

        content.addView(panelHome)
        content.addView(panelRegister)
        content.addView(panelAlert)
        content.addView(panelDonors)
        content.addView(panelHealth)

        root.addView(scroll)
        root.addView(buildBottomNav())
    }

    // ── HEADER ───────────────────────────────────────────────
    private fun buildHeader(): LinearLayout {
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(C_WHITE)
            setPadding(dp(20), dp(50), dp(20), dp(16))
        }
        val logoPill = CardView(this).apply {
            radius = dp(14).toFloat()
            setCardBackgroundColor(Color.parseColor("#FFCCD5"))
            cardElevation = 0f
        }
        logoPill.addView(TextView(this).apply {
            text = "🩸"; textSize = 20f
            setPadding(dp(10), dp(6), dp(10), dp(6))
        })
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = llp(0, WRAP, 1f).also { it.setMargins(dp(12), 0, 0, 0) }
        }
        titleCol.addView(TextView(this).apply {
            text = "JeevaBindu"; textSize = 21f
            setTypeface(null, Typeface.BOLD); setTextColor(C_TEXT)
        })
        titleCol.addView(TextView(this).apply {
            text = "Blood Donor Network"; textSize = 11f; setTextColor(C_SUBTEXT)
        })
        val liveBadge = CardView(this).apply {
            radius = dp(20).toFloat()
            setCardBackgroundColor(C_GREEN_SOFT); cardElevation = 0f
        }
        liveBadge.addView(TextView(this).apply {
            text = "● LIVE"; textSize = 11f
            setTypeface(null, Typeface.BOLD); setTextColor(C_GREEN)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        })
        h.addView(logoPill); h.addView(titleCol); h.addView(liveBadge)
        return h
    }

    // ── BOTTOM NAV ───────────────────────────────────────────
    private fun buildBottomNav(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(C_WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(20))
            layoutParams = llp(MATCH, dp(76))
        }
        navHome   = navItem("🏠", "Home")
        navReg    = navItem("📋", "Register")
        navAlert  = navItem("🚨", "Emergency")
        navDonors = navItem("👥", "Donors")
        navHealth = navItem("💉", "Health")

        nav.addView(navHome);   navHome.setOnClickListener   { showTab(0) }
        nav.addView(navReg);    navReg.setOnClickListener    { showTab(1) }
        nav.addView(navAlert);  navAlert.setOnClickListener  { showTab(2); refreshAlerts() }
        nav.addView(navDonors); navDonors.setOnClickListener { showTab(3); refreshDonors(null) }
        nav.addView(navHealth); navHealth.setOnClickListener { showTab(4) }
        return nav
    }

    private fun navItem(emoji: String, label: String): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = llp(0, MATCH, 1f)
        }
        item.addView(TextView(this).apply {
            text = emoji; textSize = 20f; gravity = Gravity.CENTER
            layoutParams = llp(MATCH, WRAP)
        })
        item.addView(TextView(this).apply {
            text = label; textSize = 10f; gravity = Gravity.CENTER
            setTextColor(C_SUBTEXT); layoutParams = llp(MATCH, WRAP)
        })
        return item
    }

    // ════════════════════════════════════════════════════════
    // HOME PANEL
    // ════════════════════════════════════════════════════════
    private fun buildHomePanel(): LinearLayout {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val hero = CardView(this).apply {
            radius = dp(22).toFloat()
            setCardBackgroundColor(C_RED)
            cardElevation = dp(4).toFloat()
            layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, 0, 0, dp(16)) }
        }
        val heroIn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(26), dp(24), dp(26))
        }
        heroIn.addView(TextView(this).apply {
            text = "Donate Blood,\nSave Lives 🩸"; textSize = 26f
            setTypeface(null, Typeface.BOLD); setTextColor(C_WHITE)
        })
        heroIn.addView(gap(10))
        heroIn.addView(TextView(this).apply {
            text = "Every drop counts. Be someone's hero today."
            textSize = 13f; setTextColor(Color.parseColor("#FFCCCC"))
        })
        heroIn.addView(gap(18))
        val heroBtn = CardView(this).apply {
            radius = dp(12).toFloat()
            setCardBackgroundColor(Color.parseColor("#33FFFFFF"))
            cardElevation = 0f; layoutParams = llp(MATCH, dp(50))
        }
        heroBtn.addView(TextView(this).apply {
            text = "Register as Donor  →"; textSize = 14f
            setTypeface(null, Typeface.BOLD); setTextColor(C_WHITE)
            gravity = Gravity.CENTER; layoutParams = llp(MATCH, MATCH)
        })
        heroBtn.setOnClickListener { showTab(1) }
        heroIn.addView(heroBtn); hero.addView(heroIn); panel.addView(hero)

        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, 0, 0, dp(20)) }
        }
        val (cardDonors, tvD) = statCard("👥", "…", "Total Donors", C_BLUE_SOFT, C_BLUE)
        val (cardAlerts, tvA) = statCard("🚨", "…", "Active SOS",   C_RED_SOFT,  C_RED)
        tvDonorCount = tvD; tvAlertCount = tvA
        statsRow.addView(cardDonors.also {
            it.layoutParams = llp(0, WRAP, 1f).also { m -> m.setMargins(0, 0, dp(8), 0) }
        })
        statsRow.addView(cardAlerts.also { it.layoutParams = llp(0, WRAP, 1f) })
        panel.addView(statsRow)

        panel.addView(sectionLabel("Quick Actions")); panel.addView(gap(10))
        val actRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, 0, 0, dp(20)) }
        }
        actRow.addView(quickCard("🚨", "Emergency", C_RED_SOFT,   C_RED)   { showTab(2) })
        actRow.addView(quickCard("🔍", "Find Donors",C_BLUE_SOFT, C_BLUE)  { showTab(3); refreshDonors(null) })
        actRow.addView(quickCard("💉", "Health",    C_GREEN_SOFT, C_GREEN) { showTab(4) })
        panel.addView(actRow)

        panel.addView(sectionLabel("Blood Compatibility")); panel.addView(gap(10))
        panel.addView(buildCompatCard())
        return panel
    }

    private fun statCard(emoji: String, value: String, label: String, bg: Int, accent: Int): Pair<CardView, TextView> {
        val card = CardView(this).apply {
            radius = dp(18).toFloat(); setCardBackgroundColor(bg); cardElevation = 0f
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(18), dp(16), dp(18))
        }
        inner.addView(TextView(this).apply { text = emoji; textSize = 24f })
        val tv = TextView(this).apply {
            text = value; textSize = 32f; setTypeface(null, Typeface.BOLD); setTextColor(accent)
        }
        inner.addView(tv)
        inner.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(C_SUBTEXT) })
        card.addView(inner)
        return Pair(card, tv)
    }

    private fun quickCard(emoji: String, label: String, bg: Int, accent: Int, action: () -> Unit): CardView {
        val card = CardView(this).apply {
            radius = dp(18).toFloat(); setCardBackgroundColor(bg); cardElevation = 0f
            layoutParams = llp(0, dp(96), 1f).also { it.setMargins(0, 0, dp(8), 0) }
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = llp(MATCH, MATCH)
        }
        inner.addView(TextView(this).apply { text = emoji; textSize = 26f; gravity = Gravity.CENTER })
        inner.addView(gap(4))
        inner.addView(TextView(this).apply {
            text = label; textSize = 11f; gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD); setTextColor(accent)
        })
        card.addView(inner); card.setOnClickListener { action() }
        return card
    }

    private fun buildCompatCard(): CardView {
        val card = CardView(this).apply {
            radius = dp(18).toFloat(); setCardBackgroundColor(C_SURFACE)
            cardElevation = dp(2).toFloat()
            layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, 0, 0, dp(16)) }
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        listOf(
            "O−"  to "Universal Donor — can give to everyone",
            "AB+" to "Universal Recipient — can receive all",
            "O+"  to "Donates to O+, A+, B+, AB+",
            "A+"  to "Donates to A+, AB+"
        ).forEach { (grp, note) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, dp(6), 0, dp(6)) }
            }
            val pill = CardView(this).apply {
                radius = dp(8).toFloat(); setCardBackgroundColor(C_RED_SOFT); cardElevation = 0f
            }
            pill.addView(TextView(this).apply {
                text = grp; textSize = 13f; setTypeface(null, Typeface.BOLD)
                setTextColor(C_RED); setPadding(dp(10), dp(4), dp(10), dp(4))
            })
            row.addView(pill)
            row.addView(TextView(this).apply {
                text = "  $note"; textSize = 12f; setTextColor(C_SUBTEXT)
                layoutParams = llp(0, WRAP, 1f)
            })
            inner.addView(row)
        }
        card.addView(inner); return card
    }

    // ════════════════════════════════════════════════════════
    // REGISTER PANEL
    // ════════════════════════════════════════════════════════
    private fun buildRegisterPanel(): LinearLayout {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(pageTitle("🩸", "Register Donor"))

        val card = whiteCard()
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        etName  = lightInput("Full Name",                InputType.TYPE_CLASS_TEXT)
        etPhone = lightInput("Phone Number (10 digits)", InputType.TYPE_CLASS_PHONE)
        etAge   = lightInput("Age (18–65)",              InputType.TYPE_CLASS_NUMBER)
        etLoc   = lightInput("City / Town",              InputType.TYPE_CLASS_TEXT)
        etOtp   = lightInput("Enter 6-digit OTP",        InputType.TYPE_CLASS_NUMBER)
        spBlood = lightSpinner(BLOOD_GROUPS)

        inner.addView(fLabel("Full Name"));    inner.addView(etName);  inner.addView(gap(12))
        inner.addView(fLabel("Phone Number")); inner.addView(etPhone); inner.addView(gap(8))

        val otpInfoBox = CardView(this).apply {
            radius = dp(10).toFloat(); setCardBackgroundColor(C_BLUE_SOFT); cardElevation = 0f
            layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, dp(4), 0, dp(10)) }
        }
        otpInfoBox.addView(TextView(this).apply {
            text = "ℹ️  Tap 'Send OTP' — a 6-digit code will appear in a popup (simulated verification). Enter it below."
            textSize = 11f; setTextColor(C_BLUE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        })
        inner.addView(otpInfoBox)

        val otpRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, 0, 0, dp(12)) }
        }
        val btnOtp = Button(this).apply {
            text = "Send OTP"; setTextColor(C_WHITE)
            background = GradientDrawable().apply {
                setColor(C_ORANGE); cornerRadius = dp(8).toFloat()
            }
            textSize = 12f; isAllCaps = false; setTypeface(null, Typeface.BOLD)
            layoutParams = llp(WRAP, dp(52)); setPadding(dp(18), 0, dp(18), 0)
        }
        otpRow.addView(etOtp.also {
            it.layoutParams = llp(0, dp(52), 1f).also { m -> m.setMargins(0, 0, dp(10), 0) }
        })
        otpRow.addView(btnOtp)
        inner.addView(fLabel("Phone Verification (OTP)")); inner.addView(otpRow)

        inner.addView(fLabel("Age"));         inner.addView(etAge);   inner.addView(gap(12))
        inner.addView(fLabel("Blood Group")); inner.addView(spBlood); inner.addView(gap(12))
        inner.addView(fLabel("Location"));    inner.addView(etLoc);   inner.addView(gap(22))

        val btnReg = solidBtn("REGISTER NOW 🩸", C_RED)
        inner.addView(btnReg)

        btnOtp.setOnClickListener { sendOtp() }
        btnReg.setOnClickListener { registerDonor() }

        card.addView(inner); panel.addView(card)
        return panel
    }

    private fun sendOtp() {
        val phone = etPhone.text.toString().trim()
        if (phone.length != 10 || !phone.all { it.isDigit() }) {
            toast("Enter a valid 10-digit phone number first"); return
        }
        generatedOtp = (100000 + Random().nextInt(900000)).toString()
        otpVerified  = false

        android.app.AlertDialog.Builder(this)
            .setTitle("📱 OTP for ${phone.substring(0, 5)}XXXXX")
            .setMessage(
                "Your verification code:\n\n" +
                        "🔑   $generatedOtp\n\n" +
                        "Enter this 6-digit code in the OTP field to complete phone verification.\n\n" +
                        "(Simulated — in production, an SMS would be sent.)"
            )
            .setPositiveButton("Copy & Continue") { d, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("OTP", generatedOtp))
                toast("OTP $generatedOtp copied to clipboard!")
                d.dismiss()
            }
            .setCancelable(false)
            .create().show()
    }

    private fun registerDonor() {
        val name  = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val ageS  = etAge.text.toString().trim()
        val loc   = etLoc.text.toString().trim()
        val otp   = etOtp.text.toString().trim()
        val blood = spBlood.selectedItem.toString()

        if (name.isEmpty())              { toast("Enter your full name"); return }
        if (phone.isEmpty())             { toast("Enter phone number"); return }
        if (phone.length != 10)          { toast("Phone must be 10 digits"); return }
        if (!phone.all { it.isDigit() }) { toast("Phone must contain only digits"); return }
        if (ageS.isEmpty())              { toast("Enter your age"); return }
        if (loc.isEmpty())               { toast("Enter your city/town"); return }
        if (generatedOtp.isEmpty())      { toast("Tap 'Send OTP' first to verify phone"); return }
        if (otp.isEmpty())               { toast("Enter the OTP from the popup"); return }

        if (otp != generatedOtp) {
            etOtp.setBackgroundColor(Color.parseColor("#FFCCCC"))
            android.app.AlertDialog.Builder(this)
                .setTitle("❌ Wrong OTP")
                .setMessage("The OTP you entered is incorrect.\n\nTap 'Send OTP' again to get a new code.")
                .setPositiveButton("Try Again") { d, _ ->
                    etOtp.setText("")
                    etOtp.setBackgroundColor(C_INPUT)
                    d.dismiss()
                }
                .create().show()
            return
        }

        otpVerified = true
        etOtp.setBackgroundColor(C_GREEN_SOFT)

        val age = ageS.toIntOrNull() ?: 0
        if (age < 18 || age > 65) { toast("Age must be between 18 and 65"); return }

        FirestoreHelper.addDonor(
            name, phone, blood, loc, age, sdf.format(Date()),
            onSuccess = {
                runOnUiThread {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("🎉 Registration Successful!")
                        .setMessage(
                            "Welcome to JeevaBindu, $name!\n\n" +
                                    "Blood Group : $blood\n" +
                                    "Location    : $loc\n" +
                                    "Phone       : ${phone.substring(0, 6)}XXXX\n\n" +
                                    "You are now part of our donor network.\nThank you for saving lives! ❤️"
                        )
                        .setPositiveButton("View Donors") { d, _ ->
                            d.dismiss(); showTab(3); refreshDonors(null)
                        }
                        .create().show()
                    vibrate()
                    etName.setText(""); etPhone.setText(""); etAge.setText("")
                    etLoc.setText(""); etOtp.setText("")
                    etOtp.setBackgroundColor(C_INPUT)
                    generatedOtp = ""; otpVerified = false
                    updateStats()
                }
            },
            onFailure = { err ->
                runOnUiThread {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("⚠️ Save Failed")
                        .setMessage("Could not save to cloud.\n\nError: $err\n\nCheck your internet connection and try again.")
                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                        .create().show()
                }
            }
        )
    }

    // ════════════════════════════════════════════════════════
    // ALERT PANEL
    // ════════════════════════════════════════════════════════
    private fun buildAlertPanel(): LinearLayout {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(pageTitle("🚨", "Emergency Alert"))

        val infoBanner = CardView(this).apply {
            radius = dp(14).toFloat(); setCardBackgroundColor(C_RED_SOFT); cardElevation = 0f
            layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, 0, 0, dp(14)) }
        }
        infoBanner.addView(TextView(this).apply {
            text = "⚡  Alerts are broadcast to ALL registered donors via SMS & push notification. " +
                    "A simulated phone notification appears within 5 seconds. Use only for real emergencies."
            textSize = 12f; setTextColor(C_RED); setPadding(dp(16), dp(12), dp(16), dp(12))
        })
        panel.addView(infoBanner)

        val formCard = whiteCard()
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        spAlertBlood = lightSpinner(BLOOD_GROUPS)
        etHospital   = lightInput("Hospital Name",                InputType.TYPE_CLASS_TEXT)
        etUnits      = lightInput("Units Needed (1–10)",          InputType.TYPE_CLASS_NUMBER)
        etContact    = lightInput("Emergency Contact (10 digits)", InputType.TYPE_CLASS_PHONE)

        inner.addView(fLabel("Blood Group Required")); inner.addView(spAlertBlood); inner.addView(gap(12))
        inner.addView(fLabel("Hospital Name"));        inner.addView(etHospital);   inner.addView(gap(12))
        inner.addView(fLabel("Units Needed"));         inner.addView(etUnits);      inner.addView(gap(12))
        inner.addView(fLabel("Emergency Contact"));    inner.addView(etContact);    inner.addView(gap(22))

        val btnPost = solidBtn("🚨  SEND EMERGENCY ALERT", C_RED)
        btnPost.setOnClickListener { postAlert() }
        inner.addView(btnPost)

        formCard.addView(inner); panel.addView(formCard); panel.addView(gap(4))

        val alertHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, dp(4), 0, dp(10)) }
        }
        alertHeaderRow.addView(sectionLabel("Active Alerts").also { it.layoutParams = llp(0, WRAP, 1f) })
        val btnRefresh = TextView(this).apply {
            text = "↻ Refresh"; textSize = 12f; setTextColor(C_BLUE)
            setTypeface(null, Typeface.BOLD)
        }
        btnRefresh.setOnClickListener { refreshAlerts() }
        alertHeaderRow.addView(btnRefresh)
        panel.addView(alertHeaderRow)

        layoutAlerts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(layoutAlerts)
        return panel
    }

    private fun postAlert() {
        val hospital = etHospital.text.toString().trim()
        val unitsStr = etUnits.text.toString().trim()
        val contact  = etContact.text.toString().trim()
        val blood    = spAlertBlood.selectedItem.toString()

        if (hospital.isEmpty()) { toast("Enter hospital name"); return }
        if (unitsStr.isEmpty()) { toast("Enter units needed"); return }
        if (contact.isEmpty())  { toast("Enter emergency contact number"); return }
        if (contact.length != 10) { toast("Contact must be 10 digits"); return }

        val units = unitsStr.toIntOrNull()
        if (units == null || units < 1 || units > 10) {
            toast("Units must be between 1 and 10"); return
        }

        FirestoreHelper.getNextAlertNumber { alertNum ->
            FirestoreHelper.addAlert(
                blood, hospital, units, contact, sdf.format(Date()), alertNum,
                onSuccess = {
                    runOnUiThread {
                        updateStats()
                        refreshAlerts()
                        fireNotification(alertNum, blood, hospital, units)
                        vibrate()

                        etHospital.setText("")
                        etUnits.setText("")
                        etContact.setText("")

                        val smsMsg =
                            "🚨 JeevaBindu Alert #$alertNum: $blood blood needed " +
                                    "($units unit(s)) at $hospital. " +
                                    "Emergency contact: $contact. Open JeevaBindu app to respond."

                        // ── SMS to emergency contact immediately ──────────────
                        sendSms(contact, smsMsg)

                        // ── SMS to ALL registered donors ──────────────────────
                        FirestoreHelper.getAllDonorPhones(bloodGroup = null) { phones ->
                            phones.forEach { phone ->
                                if (phone != contact) sendSms(phone, smsMsg)
                            }

                            runOnUiThread {
                                // Show simulated phone alert overlay after 5 seconds
                                Handler(Looper.getMainLooper()).postDelayed({
                                    simulatePhoneAlert(alertNum, blood, hospital, units, phones.size)
                                }, 5000)

                                android.app.AlertDialog.Builder(this)
                                    .setTitle("🚨 Alert #$alertNum Sent!")
                                    .setMessage(
                                        "Emergency alert posted successfully.\n\n" +
                                                "Alert No. : #$alertNum\n" +
                                                "Blood     : $blood\n" +
                                                "Hospital  : $hospital\n" +
                                                "Units     : $units\n\n" +
                                                "📲 SMS sent to emergency contact & all ${phones.size} registered donor(s).\n" +
                                                "   Method: ${if (hasSmsPermission()) "Native SMS + API backup" else "Fast2SMS API"}\n" +
                                                "Push notification sent to all app users.\n\n" +
                                                "📱 Simulated donor phone alert will appear in 5 seconds."
                                    )
                                    .setPositiveButton("View Alerts") { d, _ ->
                                        d.dismiss(); showTab(2); refreshAlerts()
                                    }
                                    .create().show()
                            }
                        }
                    }
                },
                onFailure = { err ->
                    runOnUiThread { toast("Failed to send alert: $err") }
                }
            )
        }
    }

    // ════════════════════════════════════════════════════════
    // SIMULATED DONOR PHONE POPUP
    // ════════════════════════════════════════════════════════
    private fun simulatePhoneAlert(
        alertNum: Int,
        blood: String,
        hospital: String,
        units: Int,
        donorCount: Int
    ) {
        val dimOverlay = View(this).apply {
            setBackgroundColor(Color.parseColor("#AA000000"))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            alpha = 0f
        }
        rootFrame.addView(dimOverlay)
        ObjectAnimator.ofFloat(dimOverlay, "alpha", 0f, 1f).apply { duration = 300 }.start()

        val phonePadH = dp(28)
        val phonePadV = dp(60)
        val phoneParams = FrameLayout.LayoutParams(MATCH, MATCH).apply {
            setMargins(phonePadH, phonePadV, phonePadH, phonePadV)
        }
        val phone = CardView(this).apply {
            radius = dp(36).toFloat()
            setCardBackgroundColor(Color.parseColor("#1A1A2E"))
            cardElevation = dp(24).toFloat()
            layoutParams = phoneParams
            translationY = 1200f
        }
        rootFrame.addView(phone)

        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F0F1A"))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH).apply {
                setMargins(dp(6), dp(20), dp(6), dp(20))
            }
        }

        val statusBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(6))
            layoutParams = llp(MATCH, WRAP)
        }
        statusBar.addView(TextView(this).apply {
            text = "9:41 AM"; textSize = 13f; setTextColor(C_WHITE)
            setTypeface(null, Typeface.BOLD); layoutParams = llp(0, WRAP, 1f)
        })
        statusBar.addView(TextView(this).apply {
            text = "📶 🔋"; textSize = 13f; setTextColor(C_WHITE)
        })
        screen.addView(statusBar)

        val wallpaper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0D1B2A"))
            setPadding(0, dp(8), 0, dp(8))
            layoutParams = llp(MATCH, WRAP)
        }
        wallpaper.addView(TextView(this).apply {
            text = "🩸"; textSize = 40f; gravity = Gravity.CENTER
        })
        wallpaper.addView(TextView(this).apply {
            text = "JeevaBindu"; textSize = 11f; setTextColor(Color.parseColor("#AAAACC"))
            gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
        })
        screen.addView(wallpaper)
        screen.addView(gap(6))

        val notifCard = CardView(this).apply {
            radius = dp(20).toFloat()
            setCardBackgroundColor(Color.parseColor("#22222E"))
            cardElevation = 0f
            layoutParams = llp(MATCH, WRAP).also {
                it.setMargins(dp(12), dp(4), dp(12), dp(4))
            }
        }
        val notifInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        val notifHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = llp(MATCH, WRAP)
        }
        val iconPill = CardView(this).apply {
            radius = dp(10).toFloat(); setCardBackgroundColor(C_RED); cardElevation = 0f
        }
        iconPill.addView(TextView(this).apply {
            text = "🚨"; textSize = 14f; setPadding(dp(6), dp(4), dp(6), dp(4))
        })
        notifHeader.addView(iconPill)
        notifHeader.addView(TextView(this).apply {
            text = "  JeevaBindu"; textSize = 12f; setTextColor(Color.parseColor("#CCCCDD"))
            setTypeface(null, Typeface.BOLD); layoutParams = llp(0, WRAP, 1f)
        })
        notifHeader.addView(TextView(this).apply {
            text = "now"; textSize = 11f; setTextColor(Color.parseColor("#888899"))
        })
        notifInner.addView(notifHeader)
        notifInner.addView(gap(8))

        notifInner.addView(TextView(this).apply {
            text = "🚨 ALERT #$alertNum — $blood Blood URGENT!"; textSize = 15f
            setTypeface(null, Typeface.BOLD); setTextColor(C_RED)
        })
        notifInner.addView(gap(4))
        notifInner.addView(TextView(this).apply {
            text = "$units unit(s) needed at $hospital\nAll $donorCount registered donors notified"
            textSize = 12f; setTextColor(Color.parseColor("#BBBBCC"))
        })
        notifInner.addView(gap(4))
        notifInner.addView(TextView(this).apply {
            text = "📲 SMS sent via ${if (hasSmsPermission()) "Native SMS" else "Fast2SMS API"}"
            textSize = 11f; setTextColor(Color.parseColor("#77CC99"))
        })
        notifInner.addView(gap(12))

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            layoutParams = llp(MATCH, WRAP)
        }
        val btnComing = CardView(this).apply {
            radius = dp(12).toFloat(); setCardBackgroundColor(C_GREEN); cardElevation = 0f
            layoutParams = llp(0, dp(42), 1f).also { it.setMargins(0, 0, dp(6), 0) }
        }
        btnComing.addView(TextView(this).apply {
            text = "✅ I'm Coming"; textSize = 12f
            setTypeface(null, Typeface.BOLD); setTextColor(C_WHITE)
            gravity = Gravity.CENTER; layoutParams = llp(MATCH, MATCH)
        })
        val btnDismiss = CardView(this).apply {
            radius = dp(12).toFloat(); setCardBackgroundColor(Color.parseColor("#444455")); cardElevation = 0f
            layoutParams = llp(0, dp(42), 1f)
        }
        btnDismiss.addView(TextView(this).apply {
            text = "✕ Dismiss"; textSize = 12f
            setTextColor(Color.parseColor("#AAAACC"))
            gravity = Gravity.CENTER; layoutParams = llp(MATCH, MATCH)
        })
        actionRow.addView(btnComing); actionRow.addView(btnDismiss)
        notifInner.addView(actionRow)
        notifCard.addView(notifInner)
        screen.addView(notifCard)

        screen.addView(gap(8))
        screen.addView(TextView(this).apply {
            text = "📲  SMS sent to all $donorCount donor number(s)"
            textSize = 11f; setTextColor(Color.parseColor("#77CC99"))
            gravity = Gravity.CENTER; setPadding(dp(16), dp(4), dp(16), dp(12))
        })

        phone.addView(screen)

        ObjectAnimator.ofFloat(phone, "translationY", 1200f, 0f).apply {
            duration = 500
            interpolator = OvershootInterpolator(0.8f)
        }.start()

        val pulseAnim = ValueAnimator.ofFloat(1f, 1.03f, 1f).apply {
            duration = 800; repeatCount = 4
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                notifCard.scaleX = it.animatedValue as Float
                notifCard.scaleY = it.animatedValue as Float
            }
        }
        Handler(Looper.getMainLooper()).postDelayed({ pulseAnim.start() }, 600)

        val handler = Handler(Looper.getMainLooper())
        val autoRemove = Runnable {
            ObjectAnimator.ofFloat(phone, "translationY", 0f, 1200f).apply {
                duration = 400
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: android.animation.Animator) {
                        if (phone.parent != null) rootFrame.removeView(phone)
                        if (dimOverlay.parent != null) rootFrame.removeView(dimOverlay)
                    }
                })
            }.start()
            ObjectAnimator.ofFloat(dimOverlay, "alpha", 1f, 0f).apply { duration = 400 }.start()
        }
        handler.postDelayed(autoRemove, 8000)

        val dismiss: () -> Unit = {
            handler.removeCallbacksAndMessages(null)
            ObjectAnimator.ofFloat(phone, "translationY", 0f, 1200f).apply {
                duration = 350
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: android.animation.Animator) {
                        if (phone.parent != null) rootFrame.removeView(phone)
                        if (dimOverlay.parent != null) rootFrame.removeView(dimOverlay)
                    }
                })
            }.start()
            ObjectAnimator.ofFloat(dimOverlay, "alpha", 1f, 0f).apply { duration = 350 }.start()
        }

        btnDismiss.setOnClickListener { dismiss() }
        btnComing.setOnClickListener {
            toast("✅ Response recorded — heading to $hospital!")
            vibrate()
            dismiss()
        }
        dimOverlay.setOnClickListener { dismiss() }
    }

    // ════════════════════════════════════════════════════════
    // ALERT LIST
    // ════════════════════════════════════════════════════════
    private fun refreshAlerts() {
        layoutAlerts.removeAllViews()
        layoutAlerts.addView(loadingView("Loading alerts…"))

        FirestoreHelper.getAlerts(
            onResult = { alerts ->
                runOnUiThread {
                    layoutAlerts.removeAllViews()
                    if (alerts.isEmpty()) {
                        layoutAlerts.addView(emptyState("No active alerts 🟢", "All clear — no emergencies right now"))
                        return@runOnUiThread
                    }
                    alerts.forEach { alert -> buildAlertCard(alert) }
                }
            },
            onFailure = { err ->
                runOnUiThread {
                    layoutAlerts.removeAllViews()
                    layoutAlerts.addView(emptyState("Could not load alerts", "📡 Check internet connection"))
                    android.util.Log.e("Firestore", "getAlerts: $err")
                }
            }
        )
    }

    private fun buildAlertCard(alert: AlertModel) {
        val isCovered = alert.donorComing
        val card = CardView(this).apply {
            radius = dp(18).toFloat()
            setCardBackgroundColor(if (!isCovered) C_RED_SOFT else C_GREEN_SOFT)
            cardElevation = dp(2).toFloat()
            layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, 0, 0, dp(12)) }
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = llp(MATCH, WRAP)
        }
        val numBadge = CardView(this).apply {
            radius = dp(8).toFloat(); cardElevation = 0f
            setCardBackgroundColor(if (!isCovered) C_RED else C_GREEN)
        }
        numBadge.addView(TextView(this).apply {
            text = "#${alert.alertNumber}"; textSize = 11f
            setTypeface(null, Typeface.BOLD); setTextColor(C_WHITE)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        })
        titleRow.addView(numBadge)
        titleRow.addView(TextView(this).apply {
            text = "  🚨 ${alert.bloodGroup} Needed"
            textSize = 17f; setTypeface(null, Typeface.BOLD)
            setTextColor(if (!isCovered) C_RED else C_GREEN)
            layoutParams = llp(0, WRAP, 1f)
        })
        val statusPill = CardView(this).apply {
            radius = dp(10).toFloat(); cardElevation = 0f
            setCardBackgroundColor(if (!isCovered) C_RED else C_GREEN)
        }
        statusPill.addView(TextView(this).apply {
            text = if (!isCovered) "ACTIVE" else "COVERED"
            textSize = 10f; setTypeface(null, Typeface.BOLD)
            setTextColor(C_WHITE); setPadding(dp(10), dp(4), dp(10), dp(4))
        })
        titleRow.addView(statusPill)
        inner.addView(titleRow); inner.addView(gap(12))

        inner.addView(View(this).apply { setBackgroundColor(C_BORDER); layoutParams = llp(MATCH, 1) })
        inner.addView(gap(10))

        inner.addView(alertInfoRow("🏥  Hospital",  alert.hospital))
        inner.addView(alertInfoRow("🩸  Units",     "${alert.units} unit(s) needed"))
        inner.addView(alertInfoRow("📞  Contact",   alert.contact))
        inner.addView(alertInfoRow("⏰  Posted",    alert.timestamp))
        inner.addView(gap(14))

        if (!isCovered) {
            val btnCome = solidBtn("🚗  I'M ON MY WAY", C_GREEN)
            btnCome.setOnClickListener {
                FirestoreHelper.markDonorComing(
                    alertId = alert.id,
                    onSuccess = {
                        runOnUiThread {
                            updateStats(); refreshAlerts()
                            android.app.AlertDialog.Builder(this)
                                .setTitle("🚑 Thank You!")
                                .setMessage(
                                    "You've accepted Alert #${alert.alertNumber}.\n\n" +
                                            "Please head to:\n📍 ${alert.hospital}\n\n" +
                                            "Emergency Contact: ${alert.contact}\n\n" +
                                            "You are saving a life! ❤️ God bless you."
                                )
                                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                                .create().show()
                            vibrate()
                        }
                    },
                    onFailure = { runOnUiThread { toast("Update failed — check internet") } }
                )
            }
            inner.addView(btnCome)
        } else {
            inner.addView(TextView(this).apply {
                text = "✅  A donor has accepted this request"
                textSize = 13f; setTextColor(C_GREEN); setTypeface(null, Typeface.BOLD)
            })
        }

        card.addView(inner)
        layoutAlerts.addView(card)
    }

    private fun alertInfoRow(label: String, value: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, dp(3), 0, dp(3)) }
        }
        row.addView(TextView(this).apply {
            text = label; textSize = 12f; setTextColor(C_SUBTEXT)
            layoutParams = llp(dp(120), WRAP)
        })
        row.addView(TextView(this).apply {
            text = value; textSize = 13f; setTypeface(null, Typeface.BOLD); setTextColor(C_TEXT)
            layoutParams = llp(0, WRAP, 1f)
        })
        return row
    }

    // ════════════════════════════════════════════════════════
    // DONORS PANEL
    // ════════════════════════════════════════════════════════
    private fun buildDonorsPanel(): LinearLayout {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(pageTitle("👥", "Available Donors"))

        val filterCard = whiteCard()
        val fRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        spFilter = lightSpinner(FILTER_GROUPS)
        val btnFilter = Button(this).apply {
            text = "Filter"; setTextColor(C_WHITE)
            background = GradientDrawable().apply {
                setColor(C_BLUE); cornerRadius = dp(8).toFloat()
            }
            isAllCaps = false; textSize = 13f; setTypeface(null, Typeface.BOLD)
            layoutParams = llp(WRAP, dp(48)); setPadding(dp(20), 0, dp(20), 0)
        }
        fRow.addView(spFilter.also {
            it.layoutParams = llp(0, dp(52), 1f).also { m -> m.setMargins(0, 0, dp(10), 0) }
        })
        fRow.addView(btnFilter)
        filterCard.addView(fRow); panel.addView(filterCard)

        tvFilterCount = TextView(this).apply {
            text = ""; textSize = 12f; setTextColor(C_SUBTEXT)
            layoutParams = llp(MATCH, WRAP).also { it.setMargins(dp(4), dp(4), 0, dp(10)) }
        }
        panel.addView(tvFilterCount)

        layoutDonors = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(layoutDonors)

        btnFilter.setOnClickListener {
            val sel = spFilter.selectedItem.toString()
            refreshDonors(if (sel == "All") null else sel)
        }
        return panel
    }

    private fun refreshDonors(filter: String?) {
        layoutDonors.removeAllViews()
        tvFilterCount.text = "Loading…"
        layoutDonors.addView(loadingView("Fetching donors from cloud…"))

        FirestoreHelper.getDonors(
            bloodFilter = filter,
            onResult = { list ->
                runOnUiThread {
                    layoutDonors.removeAllViews()
                    val label = filter ?: "All groups"
                    tvFilterCount.text = "${list.size} donor${if (list.size != 1) "s" else ""} found  ($label)"
                    if (list.isEmpty()) {
                        layoutDonors.addView(emptyState("No donors found for $label", "Try selecting a different blood group"))
                        return@runOnUiThread
                    }
                    list.forEach { donor -> buildDonorCard(donor) }
                }
            },
            onFailure = { err ->
                runOnUiThread {
                    layoutDonors.removeAllViews()
                    tvFilterCount.text = "Failed to load"
                    layoutDonors.addView(emptyState("Could not load donors", "📡 Check internet connection"))
                    android.util.Log.e("Firestore", "getDonors: $err")
                }
            }
        )
    }

    private fun buildDonorCard(donor: DonorModel) {
        val card = CardView(this).apply {
            radius = dp(18).toFloat(); setCardBackgroundColor(C_SURFACE)
            cardElevation = dp(2).toFloat()
            layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, 0, 0, dp(12)) }
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val avatar = CardView(this).apply {
            radius = dp(28).toFloat(); setCardBackgroundColor(C_RED_SOFT); cardElevation = 0f
        }
        val initial = if (donor.name.isNotEmpty()) donor.name.first().toString().uppercase() else "?"
        avatar.addView(TextView(this).apply {
            text = initial; textSize = 20f; setTypeface(null, Typeface.BOLD)
            setTextColor(C_RED); gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
        })
        val nameCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = llp(0, WRAP, 1f).also { it.setMargins(dp(12), 0, 0, 0) }
        }
        nameCol.addView(TextView(this).apply {
            text = donor.name; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(C_TEXT)
        })
        nameCol.addView(TextView(this).apply {
            text = "${donor.location}  •  Age ${donor.age}"
            textSize = 12f; setTextColor(C_SUBTEXT)
        })
        val bloodBadge = CardView(this).apply {
            radius = dp(10).toFloat(); setCardBackgroundColor(C_RED_SOFT); cardElevation = 0f
        }
        bloodBadge.addView(TextView(this).apply {
            text = donor.bloodGroup; textSize = 15f
            setTypeface(null, Typeface.BOLD); setTextColor(C_RED)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        })
        topRow.addView(avatar); topRow.addView(nameCol); topRow.addView(bloodBadge)
        inner.addView(topRow); inner.addView(gap(12))

        inner.addView(View(this).apply { setBackgroundColor(C_BORDER); layoutParams = llp(MATCH, 1) })
        inner.addView(gap(10))

        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; layoutParams = llp(MATCH, WRAP)
        }
        chipRow.addView(chip("📱 ${donor.phone}",          C_BLUE_SOFT, C_BLUE))
        chipRow.addView(chip("📅 ${donor.registeredDate}", C_INPUT,     C_SUBTEXT))
        inner.addView(chipRow); inner.addView(gap(8))
        inner.addView(chip("✅  Ready to Donate", C_GREEN_SOFT, C_GREEN))

        card.addView(inner)
        layoutDonors.addView(card)
    }

    // ════════════════════════════════════════════════════════
    // HEALTH PANEL
    // ════════════════════════════════════════════════════════
    private fun buildHealthPanel(): LinearLayout {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(pageTitle("💉", "Health Tracker"))

        val card = whiteCard()
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        inner.addView(sectionLabel("Eligibility Checker (90-Day Rule)")); inner.addView(gap(12))

        val infoBox = CardView(this).apply {
            radius = dp(10).toFloat(); setCardBackgroundColor(C_BLUE_SOFT); cardElevation = 0f
            layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, 0, 0, dp(14)) }
        }
        infoBox.addView(TextView(this).apply {
            text = "💡  You must wait 90 days (3 months) after your last blood donation before donating again. " +
                    "Select your last donation date below to instantly see your eligibility date and status."
            textSize = 11f; setTextColor(C_BLUE); setPadding(dp(14), dp(10), dp(14), dp(10))
        })
        inner.addView(infoBox)

        btnPickDateTv = TextView(this).apply {
            text = "📅   Tap to Select Last Donation Date"
            textSize = 14f; setTextColor(C_SUBTEXT)
            setBackgroundColor(C_INPUT)
            setPadding(dp(16), dp(18), dp(16), dp(18))
        }
        inner.addView(btnPickDateTv); inner.addView(gap(14))
        val btnCheck = solidBtn("CHECK MY ELIGIBILITY →", C_BLUE)
        inner.addView(btnCheck); inner.addView(gap(16))

        tvEligResult = TextView(this).apply {
            text = ""; textSize = 14f; setTextColor(C_TEXT)
            visibility = View.GONE
            setPadding(dp(16), dp(18), dp(16), dp(18))
        }
        inner.addView(tvEligResult)

        btnPickDateTv.setOnClickListener { pickDate() }
        btnCheck.setOnClickListener { checkEligibility() }
        card.addView(inner); panel.addView(card)

        panel.addView(gap(8))
        panel.addView(sectionLabel("Donation Tips")); panel.addView(gap(10))
        val tipsCard = whiteCard()
        val tInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        listOf(
            "💧" to "Drink 500ml water before donation",
            "🍎" to "Eat iron-rich foods 2 hours before",
            "😴" to "Get 6+ hours of sleep the night before",
            "🚫" to "Avoid fatty food & alcohol 24 hrs prior",
            "⏱" to "Donation only takes 8–10 minutes",
            "🩺" to "Rest 10–15 min after donation",
            "✅" to "Age requirement: 18–65 years old",
            "⚖️" to "Minimum weight: 45 kg required"
        ).forEach { (em, tip) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, dp(7), 0, dp(7)) }
            }
            row.addView(TextView(this).apply { text = em; textSize = 18f })
            row.addView(TextView(this).apply {
                text = "  $tip"; textSize = 13f; setTextColor(C_SUBTEXT)
                layoutParams = llp(0, WRAP, 1f)
            })
            tInner.addView(row)
        }
        tipsCard.addView(tInner); panel.addView(tipsCard)
        return panel
    }

    private fun pickDate() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            selectedDate = Calendar.getInstance().also { it.set(y, m, d) }
            btnPickDateTv.text = "📅   ${sdf.format(selectedDate!!.time)}"
            btnPickDateTv.setTextColor(C_TEXT)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun checkEligibility() {
        if (selectedDate == null) {
            toast("Please tap the date field and select your last donation date"); return
        }
        if (selectedDate!!.after(Calendar.getInstance())) {
            toast("Last donation date cannot be in the future"); return
        }

        val eligibleDate = selectedDate!!.clone() as Calendar
        eligibleDate.add(Calendar.DAY_OF_YEAR, 90)

        val today     = Calendar.getInstance()
        val daysLeft  = ((eligibleDate.timeInMillis - today.timeInMillis) / (1000L * 60 * 60 * 24)).toInt()
        val daysSince = ((today.timeInMillis - selectedDate!!.timeInMillis) / (1000L * 60 * 60 * 24)).toInt()

        tvEligResult.visibility = View.VISIBLE

        if (daysLeft <= 0) {
            tvEligResult.setBackgroundColor(C_GREEN_SOFT)
            tvEligResult.text =
                "✅  YOU ARE ELIGIBLE TO DONATE!\n\n" +
                        "Last Donated     : ${sdf.format(selectedDate!!.time)}\n" +
                        "Days Completed   : $daysSince days ✓ (≥ 90)\n" +
                        "Eligibility Date : ${sdf.format(eligibleDate.time)}\n" +
                        "Status           : ✅ READY TO DONATE\n\n" +
                        "🩸 Contact your nearest blood bank today!"
            tvEligResult.setTextColor(C_GREEN)
        } else {
            tvEligResult.setBackgroundColor(Color.parseColor("#FFF8E7"))
            tvEligResult.text =
                "⏳  NOT YET ELIGIBLE\n\n" +
                        "Last Donated     : ${sdf.format(selectedDate!!.time)}\n" +
                        "Days Completed   : $daysSince / 90 days\n" +
                        "Days Remaining   : $daysLeft more days to go\n" +
                        "Eligibility Date : 📅 ${sdf.format(eligibleDate.time)}\n" +
                        "Status           : ⏳ PLEASE WAIT"
            tvEligResult.setTextColor(C_ORANGE)
        }
    }

    // ════════════════════════════════════════════════════════
    // TAB MANAGEMENT
    // ════════════════════════════════════════════════════════
    private fun showTab(index: Int) {
        panelHome.visibility     = if (index == 0) View.VISIBLE else View.GONE
        panelRegister.visibility = if (index == 1) View.VISIBLE else View.GONE
        panelAlert.visibility    = if (index == 2) View.VISIBLE else View.GONE
        panelDonors.visibility   = if (index == 3) View.VISIBLE else View.GONE
        panelHealth.visibility   = if (index == 4) View.VISIBLE else View.GONE

        listOf(navHome, navReg, navAlert, navDonors, navHealth).forEachIndexed { i, nav ->
            val label = nav.getChildAt(1) as? TextView
            if (i == index) {
                nav.setBackgroundColor(C_RED_SOFT)
                label?.setTextColor(C_RED); label?.setTypeface(null, Typeface.BOLD)
            } else {
                nav.setBackgroundColor(Color.TRANSPARENT)
                label?.setTextColor(C_SUBTEXT); label?.setTypeface(null, Typeface.NORMAL)
            }
        }
        if (index == 0) updateStats()
    }

    private fun updateStats() {
        FirestoreHelper.getDonorCount { count ->
            runOnUiThread { tvDonorCount.text = count.toString() }
        }
        FirestoreHelper.getActiveAlertCount { count ->
            runOnUiThread { tvAlertCount.text = count.toString() }
        }
    }

    // ════════════════════════════════════════════════════════
    // NOTIFICATIONS
    // ════════════════════════════════════════════════════════
    private fun fireNotification(alertNum: Int, blood: String, hospital: String, units: Int) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🚨 Alert #$alertNum — $blood Blood Needed!")
                .setContentText("$units unit(s) at $hospital. Can you help?")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Emergency Alert #$alertNum\n" +
                                "Blood Group : $blood\n" +
                                "Hospital    : $hospital\n" +
                                "Units Needed: $units\n\n" +
                                "Open JeevaBindu to respond."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .build()
            manager.notify(alertNum, notification)

            RingtoneManager.getRingtone(
                applicationContext,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ).play()
        } catch (e: Exception) {
            android.util.Log.e("Notif", "Error: ${e.message}")
        }
    }

    private fun createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "JeevaBindu Blood Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Emergency blood request notifications"
                enableLights(true); lightColor = Color.RED
                enableVibration(true)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    // ════════════════════════════════════════════════════════
    // UI HELPERS
    // ════════════════════════════════════════════════════════
    private fun loadingView(text: String): LinearLayout {
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(0, dp(30), 0, dp(30)); layoutParams = llp(MATCH, WRAP)
        }
        ll.addView(TextView(this).apply {
            this.text = text; textSize = 13f
            setTextColor(C_SUBTEXT); gravity = Gravity.CENTER
        })
        return ll
    }

    private fun pageTitle(emoji: String, title: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, dp(4), 0, dp(16)) }
        }
        row.addView(TextView(this).apply { text = emoji; textSize = 24f })
        row.addView(TextView(this).apply {
            text = "  $title"; textSize = 22f
            setTypeface(null, Typeface.BOLD); setTextColor(C_TEXT)
        })
        return row
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f
        setTypeface(null, Typeface.BOLD); setTextColor(C_SUBTEXT)
        layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, dp(2), 0, dp(2)) }
    }

    private fun fLabel(text: String) = TextView(this).apply {
        this.text = text; textSize = 12f; setTextColor(C_SUBTEXT)
        setTypeface(null, Typeface.BOLD)
        layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, dp(6), 0, dp(4)) }
    }

    private fun whiteCard() = CardView(this).apply {
        radius = dp(20).toFloat(); setCardBackgroundColor(C_SURFACE)
        cardElevation = dp(2).toFloat()
        layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, 0, 0, dp(16)) }
    }

    private fun lightInput(hint: String, type: Int) = EditText(this).apply {
        this.hint = hint; this.inputType = type
        setTextColor(C_TEXT); setHintTextColor(C_SUBTEXT)
        setBackgroundColor(C_INPUT)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = llp(MATCH, dp(52))
    }

    private fun lightSpinner(items: Array<String>) = Spinner(this).apply {
        val adp = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, items)
        adp.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        adapter = adp; setBackgroundColor(C_INPUT); layoutParams = llp(MATCH, dp(52))
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                (v as? TextView)?.setTextColor(C_TEXT)
                (v as? TextView)?.setPadding(dp(12), 0, 0, 0)
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
    }

    private fun solidBtn(text: String, color: Int) = Button(this).apply {
        this.text = text; setTextColor(C_WHITE)
        background = GradientDrawable().apply {
            setColor(color); cornerRadius = dp(10).toFloat()
        }
        textSize = 14f; isAllCaps = false; setTypeface(null, Typeface.BOLD)
        layoutParams = llp(MATCH, dp(54))
    }

    private fun chip(text: String, bg: Int, textColor: Int): CardView {
        val card = CardView(this).apply {
            radius = dp(8).toFloat(); setCardBackgroundColor(bg); cardElevation = 0f
            layoutParams = llp(WRAP, WRAP).also { it.setMargins(0, 0, dp(8), dp(4)) }
        }
        card.addView(TextView(this).apply {
            this.text = text; textSize = 12f; setTextColor(textColor)
            setPadding(dp(10), dp(5), dp(10), dp(5))
        })
        return card
    }

    private fun emptyState(title: String, subtitle: String): LinearLayout {
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(0, dp(40), 0, dp(40)); layoutParams = llp(MATCH, WRAP)
        }
        ll.addView(TextView(this).apply {
            text = title; textSize = 15f; setTextColor(C_SUBTEXT)
            gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
        })
        ll.addView(TextView(this).apply {
            text = subtitle; textSize = 12f; setTextColor(C_SUBTEXT); gravity = Gravity.CENTER
            layoutParams = llp(MATCH, WRAP).also { it.setMargins(0, dp(6), 0, 0) }
        })
        return ll
    }

    private fun gap(dp: Int) = View(this).apply { layoutParams = llp(MATCH, dp(dp)) }

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP  = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun llp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    private fun flp(w: Int, h: Int) = FrameLayout.LayoutParams(w, h)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun vibrate() {
        try {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(200)
        } catch (e: Exception) {
            android.util.Log.e("Vibrate", "Error: ${e.message}")
        }
    }
}