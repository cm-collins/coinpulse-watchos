package com.example.coinpulse.presentation.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.coinpulse.AppContainer
import com.example.coinpulse.domain.FailureReason
import com.example.coinpulse.domain.PriceHistoryRepository
import com.example.coinpulse.domain.PriceHistoryResult
import com.example.coinpulse.domain.PricePoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
sealed interface ChartUiState {
    data object Loading : ChartUiState

    /** At least two points, oldest first. */
    data class Ready(val points: List<PricePoint>) : ChartUiState

    data class Unavailable(val reason: FailureReason) : ChartUiState
}

/** Loads the 24h chart for one coin. Price, change and range come from the watchlist, not here. */
class CoinDetailViewModel(
    private val baseAsset: String,
    private val quoteAsset: String,
    private val repository: PriceHistoryRepository,
) : ViewModel() {

    private val _chart = MutableStateFlow<ChartUiState>(ChartUiState.Loading)
    val chart: StateFlow<ChartUiState> = _chart.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onRetry() = load()

    private fun load() {
        if (loadJob?.isActive == true) return
        _chart.value = ChartUiState.Loading
        loadJob =
            viewModelScope.launch {
                _chart.value =
                    when (val result = repository.get24hHistory(baseAsset, quoteAsset)) {
                        is PriceHistoryResult.Failure -> ChartUiState.Unavailable(result.reason)
                        is PriceHistoryResult.Success ->
                            if (result.points.size >= MIN_POINTS) {
                                ChartUiState.Ready(result.points)
                            } else {
                                ChartUiState.Unavailable(FailureReason.Unknown)
                            }
                    }
            }
    }

    companion object {
        private const val MIN_POINTS = 2

        fun factory(container: AppContainer, baseAsset: String, quoteAsset: String) = viewModelFactory {
            initializer { CoinDetailViewModel(baseAsset, quoteAsset, container.priceHistoryRepository) }
        }
    }
}
