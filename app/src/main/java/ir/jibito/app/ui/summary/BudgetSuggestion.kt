package ir.jibito.app.ui.summary

/**
 * بودجه‌ی پیشنهادی از میانگین خرج: به تومان تا دو رقم اول گرد می‌شود (۲۲٫۴ میلیون ← ۲۲ میلیون، ۹۵۶ هزار ← ۹۶۰ هزار)
 * تا عددی باشد که آدم خودش هم می‌گوید. null اگر میانگینی نیست.
 */
internal fun suggestedBudgetRial(averageRial: Long): Long? {
    val toman = averageRial / 10
    if (toman <= 0) return null
    var unit = 1L
    while (toman / unit >= 100) unit *= 10
    return (toman + unit / 2) / unit * unit * 10
}
