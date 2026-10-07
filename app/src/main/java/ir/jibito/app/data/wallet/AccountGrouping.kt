package ir.jibito.app.data.wallet

/*
 * چند حساب در یک بانک («موجودی همه‌ی حساب‌ها»).
 *
 * هر پیامک شماره حساب/کارت خودش را دارد (اگر بانک بنویسد). حساب‌های یک بانک تا کاربر چیزی نگفته جدا حساب می‌شوند؛
 * اپ یک بار می‌پرسد «جدا هستند یا یکی؟» (یک بانک ممکن است گاهی شماره کارت و گاهی شماره حساب بفرستد).
 * جواب‌ها «قانون» جدا هستند ([AccountLink]) و به تراکنش‌ها دست نمی‌زنند، پس هر تصمیمی برگشت‌پذیر است.
 *
 * این فایل به اندروید وابسته نیست (تست روی JVM).
 */

/** آخرین مانده‌ی هر (بانک، شماره حساب)؛ account = null یعنی پیامک شماره حساب نداشت */
data class AccountBalanceRow(
    val bankId: Int,
    val account: String?,
    val remainAfter: Long,
    val dateEpoch: Long,
)

/** شماره حسابی که در پیامک‌های یک بانک دیده شده، با زمان آخرین پیامکش */
data class KnownAccountRow(
    val bankId: Int,
    val account: String,
    val lastSeen: Long,
)

/** تصمیم کاربر درباره‌ی یک حساب (همان AccountLinkEntity، بدون وابستگی به دیتابیس) */
data class AccountLink(
    val bankId: Int,
    val account: String,
    /** نماینده‌ی گروه؛ برابر [account] یعنی حساب جداست */
    val groupAccount: String,
    val name: String?,
    val decided: Boolean,
)

/** مانده‌ی یک حساب (یا گروهی از شماره‌هایی که کاربر گفته یکی‌اند) */
data class AccountBalance(
    val bankId: Int,
    /** نماینده‌ی گروه؛ null یعنی پیامک‌های این بانک شماره حساب ندارند (یک مانده برای کل بانک) */
    val account: String?,
    val name: String?,
    val balanceRial: Long,
    val dateMillis: Long,
)

/** یک حساب برای صفحه‌ی «حساب‌های من»: نماینده، همه‌ی شماره‌هایش، اسم و آخرین مانده (اگر باشد) */
data class AccountGroup(
    val bankId: Int,
    val account: String?,
    val members: List<String>,
    val name: String?,
    val balanceRial: Long?,
    val dateMillis: Long?,
)

/** «در این بانک دو حساب دیدم: [account] و [other]. جدا هستند یا یکی؟» */
data class AccountQuestion(val bankId: Int, val account: String, val other: String)

object AccountGrouping {

    private fun index(links: List<AccountLink>) = links.associateBy { it.bankId to it.account }

    /** نماینده‌ی گروهِ یک حساب (خودش، اگر کاربر چیزی نگفته) */
    fun repOf(bankId: Int, account: String, links: List<AccountLink>): String =
        index(links)[bankId to account]?.groupAccount ?: account

    /**
     * مانده‌ی هر حساب، تازه‌ترین اول.
     * بانکی که هیچ پیامکش شماره حساب ندارد ← یک مانده برای کل بانک (مثل قبل).
     * بانکی که شماره حساب دارد ← پیامک‌های بی‌شماره در مانده حساب نمی‌شوند (معلوم نیست مال کدام حساب‌اند).
     */
    fun balances(rows: List<AccountBalanceRow>, links: List<AccountLink>): List<AccountBalance> {
        val byKey = index(links)
        return rows.groupBy { it.bankId }.flatMap { (bankId, bankRows) ->
            val withAccount = bankRows.filter { it.account != null }
            if (withAccount.isEmpty()) {
                val latest = bankRows.maxBy { it.dateEpoch }
                listOf(AccountBalance(bankId, null, null, latest.remainAfter, latest.dateEpoch))
            } else {
                withAccount
                    .groupBy { byKey[bankId to it.account!!]?.groupAccount ?: it.account }
                    .map { (rep, groupRows) ->
                        val latest = groupRows.maxBy { it.dateEpoch }
                        AccountBalance(bankId, rep, byKey[bankId to rep]?.name, latest.remainAfter, latest.dateEpoch)
                    }
            }
        }.sortedByDescending { it.dateMillis }
    }

    /** همه‌ی حساب‌ها برای صفحه‌ی «حساب‌های من»، بانک‌به‌بانک (تازه‌ترین بانک اول) */
    fun groups(known: List<KnownAccountRow>, rows: List<AccountBalanceRow>, links: List<AccountLink>): List<AccountGroup> {
        val byKey = index(links)
        val balances = balances(rows, links).associateBy { it.bankId to it.account }
        val banks = (known.map { it.bankId } + rows.map { it.bankId }).distinct()
        return banks.flatMap { bankId ->
            val accounts = known.filter { it.bankId == bankId }
            if (accounts.isEmpty()) {
                val b = balances[bankId to null] ?: return@flatMap emptyList()
                listOf(AccountGroup(bankId, null, emptyList(), null, b.balanceRial, b.dateMillis))
            } else {
                accounts
                    .groupBy { byKey[bankId to it.account]?.groupAccount ?: it.account }
                    .map { (rep, members) ->
                        val b = balances[bankId to rep]
                        AccountGroup(
                            bankId, rep,
                            members.sortedByDescending { it.lastSeen }.map { it.account },
                            byKey[bankId to rep]?.name,
                            b?.balanceRial, b?.dateMillis ?: members.maxOf { it.lastSeen },
                        )
                    }
                    .sortedByDescending { it.dateMillis }
            }
        }.sortedWith(
            compareByDescending<AccountGroup> { g -> known.filter { it.bankId == g.bankId }.maxOfOrNull { it.lastSeen } ?: g.dateMillis ?: 0L }
                .thenBy { it.bankId }
        )
    }

    /**
     * سؤال بعدی، اگر هست: در بانکی که دست‌کم دو شماره حساب دارد، تازه‌ترین شماره‌ای که کاربر درباره‌اش جواب نداده،
     * در برابر تازه‌ترین شماره‌ی دیگرِ همان بانک.
     */
    fun question(known: List<KnownAccountRow>, links: List<AccountLink>): AccountQuestion? {
        val byKey = index(links)
        return known.groupBy { it.bankId }
            .entries
            .sortedByDescending { (_, accounts) -> accounts.maxOf { it.lastSeen } }
            .firstNotNullOfOrNull { (bankId, accounts) ->
                if (accounts.size < 2) return@firstNotNullOfOrNull null
                val sorted = accounts.sortedByDescending { it.lastSeen }
                val undecided = sorted.firstOrNull { byKey[bankId to it.account]?.decided != true } ?: return@firstNotNullOfOrNull null
                val other = sorted.first { it.account != undecided.account }
                AccountQuestion(bankId, undecided.account, other.account)
            }
    }

    /** جواب سؤال: ردیف‌هایی که باید ذخیره شوند. [same] = «یکی‌اند» */
    fun answer(q: AccountQuestion, same: Boolean, links: List<AccountLink>): List<AccountLink> {
        val byKey = index(links)
        val out = mutableListOf<AccountLink>()
        val otherLink = byKey[q.bankId to q.other]
        if (otherLink == null || !otherLink.decided) {
            out += AccountLink(q.bankId, q.other, otherLink?.groupAccount ?: q.other, otherLink?.name, decided = true)
        }
        val target = if (same) (otherLink?.groupAccount ?: q.other) else q.account
        val own = byKey[q.bankId to q.account]
        out += AccountLink(q.bankId, q.account, target, own?.name, decided = true)
        return out
    }

    /** «این حساب با آن یکی است»: کل گروهِ [rep] به گروهِ [intoRep] می‌رود (اسم گروه مقصد می‌ماند، اگر نداشت اسم این) */
    fun merge(bankId: Int, rep: String, intoRep: String, known: List<KnownAccountRow>, links: List<AccountLink>): List<AccountLink> {
        if (rep == intoRep) return emptyList()
        val byKey = index(links)
        val target = byKey[bankId to intoRep]
        val sourceName = byKey[bankId to rep]?.name
        val members = membersOf(bankId, rep, known, links)
        return listOf(AccountLink(bankId, intoRep, intoRep, target?.name ?: sourceName, decided = true)) +
            members.map { AccountLink(bankId, it, intoRep, null, decided = true) }
    }

    /** «این شماره جداست»: [account] از گروهش بیرون می‌آید؛ اگر نماینده بود، بقیه‌ی گروه با اسمش با هم می‌مانند */
    fun split(bankId: Int, account: String, known: List<KnownAccountRow>, links: List<AccountLink>): List<AccountLink> {
        val byKey = index(links)
        val rep = byKey[bankId to account]?.groupAccount ?: account
        val others = membersOf(bankId, rep, known, links) - account
        if (others.isEmpty()) return emptyList()
        val out = mutableListOf(AccountLink(bankId, account, account, null, decided = true))
        if (rep == account) {
            val newRep = others.first()
            val name = byKey[bankId to account]?.name
            out += others.map { AccountLink(bankId, it, newRep, if (it == newRep) name else null, decided = true) }
        }
        return out
    }

    /** اسم حساب (روی نماینده)؛ خالی یعنی برگشت به «اسم بانک + چهار رقم آخر» */
    fun rename(bankId: Int, rep: String, name: String, links: List<AccountLink>): AccountLink {
        val own = index(links)[bankId to rep]
        return AccountLink(bankId, rep, own?.groupAccount ?: rep, name.trim().takeIf { it.isNotEmpty() }?.take(MAX_NAME), own?.decided ?: false)
    }

    /** همه‌ی شماره‌های یک گروه (شماره‌های دیده‌شده و آن‌هایی که قانون دارند) */
    private fun membersOf(bankId: Int, rep: String, known: List<KnownAccountRow>, links: List<AccountLink>): List<String> {
        val byKey = index(links)
        val accounts = (known.filter { it.bankId == bankId }.map { it.account } +
            links.filter { it.bankId == bankId }.map { it.account } + rep).distinct()
        return accounts.filter { (byKey[bankId to it]?.groupAccount ?: it) == rep }
    }

    /** چهار رقم آخر شماره حساب/کارت، برای نمایش («۵۶۷۸»)؛ اگر رقمی نداشت، خود متن */
    fun shortNumber(account: String): String = account.filter { it.isDigit() }.takeLast(4).ifEmpty { account }

    const val MAX_NAME = 24
}

/**
 * کدام حساب‌ها از «موجودی همه‌ی حساب‌ها» بیرون‌اند: کلید هر حساب «بانک:شماره»؛
 * «بانک:*» یعنی کل بانک (همان چیزی که کاربر قبل از این قابلیت، برای یک بانک خاموش کرده بود).
 */
object WalletExclusion {

    fun key(bankId: Int, account: String?): String = "$bankId:${account.orEmpty()}"

    fun wholeBank(bankId: Int): String = "$bankId:*"

    fun isExcluded(bankId: Int, account: String?, excluded: Set<String>): Boolean =
        key(bankId, account) in excluded || wholeBank(bankId) in excluded

    /**
     * روشن/خاموش کردن یک حساب. اگر کل بانکش خاموش بود و این یکی روشن شد،
     * بقیه‌ی حساب‌های همان بانک ([sameBankAccounts]) خاموش می‌مانند.
     */
    fun toggle(bankId: Int, account: String?, include: Boolean, excluded: Set<String>, sameBankAccounts: List<String?>): Set<String> {
        val k = key(bankId, account)
        if (!include) return excluded + k
        var next = excluded - k
        if (wholeBank(bankId) in next) {
            next = next - wholeBank(bankId) + sameBankAccounts.map { key(bankId, it) }.filter { it != k }
        }
        return next
    }
}
