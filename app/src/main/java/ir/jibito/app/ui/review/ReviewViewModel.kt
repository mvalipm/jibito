package ir.jibito.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.repository.ReviewItem
import ir.jibito.app.data.review.NumberToken
import ir.jibito.app.data.repository.ReviewRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReviewViewModel(
    private val repository: ReviewRepository,
    private val onLearned: suspend () -> Unit,
) : ViewModel() {

    /** null یعنی «هنوز بارگذاری نشده» */
    val pending: StateFlow<List<ReviewItem>?> = repository.observePending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun confirm(item: ReviewItem, type: FlowType, amount: NumberToken, balance: NumberToken?, bankId: Int?) {
        viewModelScope.launch {
            repository.confirm(item, type, amount, balance, bankId)
            // قالب تازه یاد گرفته شد ← پیامک‌های هم‌قالبِ دیگر (در صندوق) همین الان خودکار خوانده می‌شوند
            onLearned()
        }
    }

    fun dismiss(item: ReviewItem, ignoreSender: Boolean) {
        viewModelScope.launch { repository.dismiss(item, ignoreSender) }
    }

    fun shareText(item: ReviewItem): String = repository.shareText(item)

    companion object {
        fun factory(repository: ReviewRepository, onLearned: suspend () -> Unit): ViewModelProvider.Factory =
            viewModelFactory { initializer { ReviewViewModel(repository, onLearned) } }
    }
}
