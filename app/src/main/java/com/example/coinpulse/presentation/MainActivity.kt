package com.example.coinpulse.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.SwipeToDismissValue
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.SwipeToDismissBox
import com.example.coinpulse.AppContainer
import com.example.coinpulse.CoinPulseApplication
import com.example.coinpulse.presentation.detail.CoinDetailRoute
import com.example.coinpulse.presentation.detail.CoinDetailViewModel
import com.example.coinpulse.presentation.theme.CoinpulseTheme
import com.example.coinpulse.presentation.watchlist.WatchlistRoute
import com.example.coinpulse.presentation.watchlist.WatchlistUiState
import com.example.coinpulse.presentation.watchlist.WatchlistViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as CoinPulseApplication).container
        setContent {
            WearApp(container)
        }
    }
}

private const val LIST_KEY = "list"
private const val DETAIL_KEY = "detail"

/**
 * Two-level navigation (watchlist -> coin detail) with the official stateful
 * [SwipeToDismissBox] pattern, so swipe-to-go-back works without a navigation library.
 * Only the selected coin's symbol is saved state; its details come from the live watchlist.
 */
@Composable
fun WearApp(container: AppContainer) {
    CoinpulseTheme {
        AppScaffold {
            val watchlistViewModel: WatchlistViewModel = viewModel(factory = WatchlistViewModel.factory(container))
            val watchlistState by watchlistViewModel.uiState.collectAsStateWithLifecycle()

            var selectedSymbol by rememberSaveable { mutableStateOf<String?>(null) }
            // Gives each opening of a detail screen its own ViewModel, so the chart reloads.
            var openCount by rememberSaveable { mutableIntStateOf(0) }

            val selectedItem =
                (watchlistState as? WatchlistUiState.Content)?.items?.firstOrNull { it.symbol == selectedSymbol }
            val showList = selectedItem == null

            val saveableStateHolder = rememberSaveableStateHolder()
            val swipeState = rememberSwipeToDismissBoxState()
            LaunchedEffect(swipeState.currentValue) {
                if (swipeState.currentValue == SwipeToDismissValue.Dismissed) {
                    swipeState.snapTo(SwipeToDismissValue.Default)
                    selectedSymbol = null
                }
            }

            SwipeToDismissBox(
                state = swipeState,
                userSwipeEnabled = !showList,
                backgroundKey = if (!showList) LIST_KEY else "none",
                contentKey = if (showList) LIST_KEY else DETAIL_KEY,
            ) { isBackground ->
                if (isBackground || showList) {
                    // The list stays composed behind the detail, so it keeps its scroll position.
                    saveableStateHolder.SaveableStateProvider(LIST_KEY) {
                        WatchlistRoute(
                            viewModel = watchlistViewModel,
                            onCoinClick = { coin ->
                                openCount++
                                selectedSymbol = coin.symbol
                            },
                        )
                    }
                } else if (selectedItem != null) {
                    CoinDetailRoute(
                        item = selectedItem,
                        viewModel =
                            viewModel(
                                key = "detail-${selectedItem.symbol}-$openCount",
                                factory =
                                    CoinDetailViewModel.factory(
                                        container,
                                        selectedItem.baseAsset,
                                        selectedItem.quoteAsset,
                                    ),
                            ),
                    )
                }
            }
        }
    }
}
