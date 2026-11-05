package org.wit.vitasense.ui.insights

import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.wit.vitasense.ui.theme.ThemeAttrColorResolver

fun LinearLayout.action(label: String, onClick: () -> Unit): MaterialButton = MaterialButton(context).also {
    it.text = label; it.contentDescription = label
    it.setOnClickListener { onClick() }; addView(it, LinearLayout.LayoutParams(-1, -2))
}
fun LinearLayout.card(title: String, body: String): LinearLayout {
    val card = MaterialCardView(context)
    card.radius = 20 * resources.displayMetrics.density
    val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; val p = (18 * resources.displayMetrics.density).toInt(); setPadding(p,p,p,p) }
    fun text(value: String, size: Float, bold: Boolean) = TextView(context).apply {
        text = value; textSize = size; setTextColor(ThemeAttrColorResolver.color(context, android.R.attr.textColorPrimary))
        if (bold) { setTypeface(typeface, android.graphics.Typeface.BOLD); isAccessibilityHeading = true }
        setPadding(0, 8, 0, 8); setTextIsSelectable(true)
    }
    box.addView(text(title, 18f, true)); box.addView(text(body, 15f, false)); card.addView(box)
    addView(card, LinearLayout.LayoutParams(-1,-2).apply { topMargin = (12 * resources.displayMetrics.density).toInt() })
    return box
}
