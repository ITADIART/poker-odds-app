package it.pokerodds

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.util.Size
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var status: TextView
    private lateinit var vWord: TextView
    private lateinit var vSize: TextView
    private lateinit var vWhy: TextView
    private lateinit var stats: TextView
    private lateinit var boardChips: LinearLayout
    private lateinit var heroChips: LinearLayout
    private lateinit var oppText: TextView
    private lateinit var potField: EditText
    private lateinit var callField: EditText
    private lateinit var heroLockBtn: Button
    private lateinit var boardLockBtn: Button

    private var detector: CardDetector? = null
    private val analysisExec: ExecutorService = Executors.newSingleThreadExecutor()
    private val equityExec: ExecutorService = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger()
    private val tracker = Tracker()

    private var hero = IntArray(2) { -1 }
    private var board = IntArray(5) { -1 }
    /** Confirmed cards stay fixed: the camera no longer changes them. */
    private var heroLocked = false
    /** Confirmed board: existing cards stay, the camera may only add new ones (turn, river). */
    private var boardLocked = false
    private var opponents = 2
    private var lastEquity: EquityResult? = null
    private var lastStreet = "preflop"
    private var lastOuts: Int? = null
    private var lastHandName = ""
    private var lastNote = ""

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCamera() else status.text = "Serve il permesso della fotocamera (puoi inserire le carte a mano)"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.preview)
        overlay = findViewById(R.id.overlay)
        status = findViewById(R.id.status)
        vWord = findViewById(R.id.vWord)
        vSize = findViewById(R.id.vSize)
        vWhy = findViewById(R.id.vWhy)
        stats = findViewById(R.id.stats)
        boardChips = findViewById(R.id.boardChips)
        heroChips = findViewById(R.id.heroChips)
        oppText = findViewById(R.id.oppText)
        potField = findViewById(R.id.pot)
        callField = findViewById(R.id.call)
        heroLockBtn = findViewById(R.id.heroLockBtn)
        boardLockBtn = findViewById(R.id.boardLockBtn)

        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        overlay.onSplitChanged = { tracker.reset() }

        findViewById<Button>(R.id.oppMinus).setOnClickListener { setOpponents(opponents - 1) }
        findViewById<Button>(R.id.oppPlus).setOnClickListener { setOpponents(opponents + 1) }
        heroLockBtn.setOnClickListener { setLocks(!heroLocked, boardLocked) }
        boardLockBtn.setOnClickListener { setLocks(heroLocked, !boardLocked) }
        findViewById<Button>(R.id.newHandBtn).setOnClickListener {
            hero = IntArray(2) { -1 }
            board = IntArray(5) { -1 }
            tracker.reset()
            callField.setText("0")
            setLocks(hero = false, board = false)
            cardsChanged()
        }
        setLocks(hero = false, board = false)
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = showAdvice()
        }
        potField.addTextChangedListener(watcher)
        callField.addTextChangedListener(watcher)

        try {
            detector = CardDetector(this).also {
                if (it.unknownLabels.isNotEmpty()) status.text = "Etichette non riconosciute: ${it.unknownLabels.take(3)}"
            }
        } catch (e: Exception) {
            status.text = "Modello carte non trovato: inserisci le carte a mano"
        }

        renderChips()
        showAdvice()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        equityExec.shutdownNow()
        analysisExec.execute { detector?.close() }
        analysisExec.shutdown()
    }

    // ---------------- Camera ----------------

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val aspect = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            val preview = Preview.Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build())
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(aspect)
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                        )
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(analysisExec) { analyze(it) }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                status.text = "Fotocamera non disponibile: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy) {
        val det = detector
        if (det == null) { image.close(); return }
        val rotation = image.imageInfo.rotationDegrees
        val raw: Bitmap
        try {
            raw = image.toBitmap()
        } finally {
            image.close()
        }
        val frame = if (rotation != 0) {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        } else raw
        val t0 = SystemClock.elapsedRealtime()
        val dets = try { det.detect(frame) } catch (e: Exception) { emptyList() }
        val ms = SystemClock.elapsedRealtime() - t0
        val w = frame.width
        val h = frame.height
        runOnUiThread {
            overlay.update(dets, w, h)
            status.text = "${ms} ms · ${dets.map { it.card }.distinct().size} carte"
            val (heroSeen, boardSeen) = tracker.update(dets, overlay.split)
            applySeen(heroSeen, boardSeen)
        }
    }

    private fun applySeen(heroSeen: List<Int>, boardSeen: List<Int>) {
        if (heroLocked && boardLocked && board.none { it < 0 }) return
        val lockedBoard = if (boardLocked) board.filter { it >= 0 }.toSet() else emptySet()
        val newHero = if (heroLocked) hero.copyOf() else {
            val seen = heroSeen.filter { it !in lockedBoard }
            val h = IntArray(2) { seen.getOrElse(it) { -1 } }
            // Keep your two cards in a stable order so the display doesn't flicker.
            if (h.toSet() == hero.toSet()) hero.copyOf() else h
        }
        val heroSet = newHero.filter { it >= 0 }.toSet()
        val newBoard = if (boardLocked) {
            val b = board.copyOf()
            val have = b.filter { it >= 0 }.toMutableSet()
            for (c in boardSeen) {
                if (c in have || c in heroSet) continue
                val free = b.indexOf(-1)
                if (free < 0) break
                b[free] = c
                have.add(c)
            }
            b
        } else {
            val list = boardSeen.filter { it !in heroSet }
            IntArray(5) { list.getOrElse(it) { -1 } }
        }
        if (!newHero.contentEquals(hero) || !newBoard.contentEquals(board)) {
            hero = newHero
            board = newBoard
            cardsChanged()
        }
    }

    // ---------------- Cards UI ----------------

    private fun setLocks(hero: Boolean, board: Boolean) {
        if (heroLocked && !hero || boardLocked && !board) tracker.reset()
        heroLocked = hero
        boardLocked = board
        heroLockBtn.text = if (heroLocked) "✓ Confermate\n(tocca per rileggere)" else "Conferma le mie carte"
        boardLockBtn.text = if (boardLocked) "✓ Tavolo confermato: aggiungo solo turn e river (tocca per rileggere)" else "Conferma tavolo"
        heroLockBtn.setTextColor(if (heroLocked) 0xFF3FCF8E.toInt() else 0xFFEEF3EC.toInt())
        boardLockBtn.setTextColor(if (boardLocked) 0xFF3FCF8E.toInt() else 0xFFEEF3EC.toInt())
        renderChips()
    }

    private fun setOpponents(n: Int) {
        opponents = n.coerceIn(1, 8)
        oppText.text = opponents.toString()
        startEquity()
    }

    private fun cardsChanged() {
        renderChips()
        startEquity()
    }

    private fun renderChips() {
        fun fill(container: LinearLayout, cards: IntArray, zone: String) {
            container.removeAllViews()
            val confirmed = if (zone == "hero") heroLocked else boardLocked
            val d = resources.displayMetrics.density
            cards.forEachIndexed { i, c ->
                val tv = TextView(this)
                val lp = LinearLayout.LayoutParams(0, (58 * d).toInt(), 1f)
                lp.marginEnd = (6 * d).toInt()
                tv.layoutParams = lp
                tv.gravity = Gravity.CENTER
                tv.textSize = 22f
                tv.paint.isFakeBoldText = true
                val bg = GradientDrawable().apply { cornerRadius = 7 * d }
                if (c >= 0) {
                    bg.setColor(0xFFFBFAF5.toInt())
                    if (confirmed) bg.setStroke((3 * d).toInt(), 0xFF3FCF8E.toInt())
                    tv.text = Cards.display(c)
                    tv.setTextColor(Cards.suitColor(c))
                } else {
                    bg.setColor(0x22FFFFFF)
                    bg.setStroke((1.5f * d).toInt(), 0x55EEF3EC)
                    tv.text = "+"
                    tv.setTextColor(0x88EEF3EC.toInt())
                }
                tv.background = bg
                tv.setOnClickListener { pickCard(zone, i) }
                container.addView(tv)
            }
        }
        fill(boardChips, board, "board")
        fill(heroChips, hero, "hero")
    }

    private fun pickCard(zone: String, index: Int) {
        val arr = if (zone == "hero") hero else board
        val current = arr[index]
        val used = (hero + board).filter { it >= 0 && it != current }.toSet()
        val d = resources.displayMetrics.density
        val grid = GridLayout(this).apply { columnCount = 13; setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), 0) }
        var dialog: AlertDialog? = null
        for (s in 0..3) for (r in 12 downTo 0) {
            val c = Cards.of(r, s)
            val tv = TextView(this).apply {
                text = Cards.RANK_IT[r] + "\n" + "♠♥♦♣"[s]
                gravity = Gravity.CENTER
                textSize = 11f
                setTextColor(Cards.suitColor(c))
                background = GradientDrawable().apply {
                    cornerRadius = 4 * d
                    setColor(if (c == current) 0xFFE3B54A.toInt() else 0xFFFBFAF5.toInt())
                }
                alpha = if (c in used) 0.2f else 1f
                isEnabled = c !in used
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = (40 * d).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(2, 2, 2, 2)
                }
                setOnClickListener {
                    arr[index] = c
                    lockZone(zone)
                    cardsChanged()
                    dialog?.dismiss()
                }
            }
            grid.addView(tv)
        }
        val title = if (zone == "hero") "La tua carta ${index + 1}" else arrayOf("Flop 1", "Flop 2", "Flop 3", "Turn", "River")[index]
        dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(grid)
            .setNegativeButton("Chiudi", null)
            .setNeutralButton("Togli") { _, _ ->
                arr[index] = -1
                lockZone(zone)
                cardsChanged()
            }
            .show()
    }

    /** A card corrected by hand must not be overwritten by the camera. */
    private fun lockZone(zone: String) {
        if (zone == "hero") setLocks(true, boardLocked) else setLocks(heroLocked, true)
    }

    // ---------------- Odds & advice ----------------

    private fun startEquity() {
        val heroC = hero.filter { it >= 0 }
        var boardC = board.filter { it >= 0 }
        lastNote = ""
        if (boardC.size in 1..2) {
            lastNote = "Flop incompleto: calcolo come preflop. "
            boardC = emptyList()
        }
        val g = generation.incrementAndGet()
        if (heroC.size < 2) {
            lastEquity = null
            showAdvice()
            return
        }
        val heroA = heroC.toIntArray()
        val boardA = boardC.toIntArray()
        val opp = opponents
        lastStreet = when (boardA.size) { 0 -> "preflop"; 3 -> "flop"; 4 -> "turn"; else -> "river" }
        vWord.alpha = 0.5f
        equityExec.execute {
            val res = Equity.run(heroA, boardA, opp, 15000) { generation.get() != g } ?: return@execute
            val outs = Equity.outs(heroA, boardA)
            val name = if (boardA.size >= 3) {
                val all = heroA + boardA
                Evaluator.handName(Evaluator().evaluate(all, all.size))
            } else preflopLabel(heroA)
            runOnUiThread {
                if (generation.get() == g) {
                    lastEquity = res
                    lastOuts = outs
                    lastHandName = name
                    vWord.alpha = 1f
                    showAdvice()
                }
            }
        }
    }

    private fun preflopLabel(h: IntArray): String {
        var a = Cards.rank(h[0]); var b = Cards.rank(h[1])
        if (a < b) { val t = a; a = b; b = t }
        val base = Cards.RANK_IT[a] + Cards.RANK_IT[b]
        return if (a == b) "Coppia $base" else base + if (Cards.suit(h[0]) == Cards.suit(h[1])) " dello stesso seme" else " semi diversi"
    }

    private fun showAdvice() {
        val eq = lastEquity
        if (eq == null) {
            vWord.text = "—"
            vWord.setTextColor(getColor(R.color.ink))
            vSize.text = ""
            vWhy.text = "Inquadra il tavolo: le tue carte sotto la linea gialla, le carte comuni sopra. Oppure tocca le caselle per inserirle."
            stats.text = "Equity —   Quota piatto —"
            return
        }
        val pot = potField.text.toString().replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        val call = callField.text.toString().replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        val a = Advisor.advise(eq, opponents, pot, call, lastStreet)
        vWord.text = a.word
        vWord.setTextColor(a.color)
        vSize.text = a.size
        vWhy.text = lastNote + a.why
        val outsTxt = lastOuts?.let { o -> "$o (≈${if (lastStreet == "flop") o * 4 else o * 2}% per migliorare)" } ?: "—"
        stats.text = buildString {
            append("Equity        ").append(Advisor.pct(eq.equity)).append('\n')
            append("Quota piatto  ").append(if (call > 0) Advisor.pct(a.potOdds) else "0% (check)").append('\n')
            append("Vinci/Pareggi ").append(Advisor.pct(eq.win)).append(" / ").append(Advisor.pct(eq.tie)).append('\n')
            if (call > 0) append("Valore call   ").append(if (a.ev >= 0) "+" else "−").append(Advisor.num(kotlin.math.abs(a.ev))).append(" fiches\n")
            append("Mano          ").append(lastHandName).append('\n')
            append("Outs          ").append(outsTxt).append('\n')
            append("Fase          ").append(lastStreet).append(" · ").append(opponents).append(" avversari")
        }
    }
}
