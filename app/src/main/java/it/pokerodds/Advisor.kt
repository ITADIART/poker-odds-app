package it.pokerodds

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class Advice(
    val word: String,
    val color: Int,
    val why: String,
    val size: String,
    val potOdds: Double,
    val ev: Double,
)

object Advisor {
    const val FOLD = 0xFFE2574C.toInt()
    const val CALL = 0xFFE3B54A.toInt()
    const val RAISE = 0xFF3FCF8E.toInt()

    fun pct(x: Double) = String.format(Locale.ITALY, "%.1f%%", x * 100)
    fun num(x: Double): String {
        val r = (x * 10).roundToInt() / 10.0
        return if (r == r.toLong().toDouble()) r.toLong().toString() else String.format(Locale.ITALY, "%.1f", r)
    }

    fun advise(r: EquityResult, opponents: Int, pot: Double, call: Double, street: String): Advice {
        val eq = r.equity
        val fair = 1.0 / (opponents + 1)
        val raiseAt = fair + (1 - fair) * 0.25
        val potOdds = if (call > 0) call / (pot + call) else 0.0
        val ev = eq * (pot + call) - call
        val vs = if (opponents == 1) "contro 1 avversario" else "contro $opponents avversari"

        if (call <= 0) {
            return if (eq >= raiseAt) {
                val bet = if (pot > 0) "Punta circa " + num(pot * if (street == "preflop") 0.5 else 0.66) else "Punta ⅔ del piatto"
                Advice("PUNTA", RAISE, "Vinci il ${pct(eq)} $vs, più della tua parte giusta (${pct(fair)}). Nessuno ha puntato: metti fiches nel piatto finché sei avanti.", bet, 0.0, 0.0)
            } else {
                Advice("CHECK", CALL, "Vinci il ${pct(eq)} $vs: non abbastanza per puntare. Passa gratis e guarda la prossima carta.", "", 0.0, 0.0)
            }
        }
        if (eq < potOdds) {
            var why = "Per chiamare ti serve vincere almeno il ${pct(potOdds)} delle volte, ma vinci il ${pct(eq)}. Ogni chiamata qui ti costa in media ${num(abs(ev))} fiches."
            if (potOdds - eq < 0.04 && street != "river") why += " È vicino: se l'avversario paga molto quando chiudi il progetto, una chiamata si può valutare."
            return Advice("FOLD", FOLD, why, "", potOdds, ev)
        }
        if (eq >= raiseAt) {
            return Advice(
                "RILANCIA", RAISE,
                "Vinci il ${pct(eq)} $vs, ben sopra il ${pct(potOdds)} che serve per chiamare. Sei favorito: fai pagare di più chi resta.",
                "Rilancia a circa " + num(call * 3), potOdds, ev,
            )
        }
        return Advice(
            "CHIAMA", CALL,
            "Ti serve vincere il ${pct(potOdds)} delle volte e vinci il ${pct(eq)}: chiamare rende in media ${num(ev)} fiches. Non sei abbastanza avanti per rilanciare.",
            "", potOdds, ev,
        )
    }
}
