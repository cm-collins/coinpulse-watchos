package com.example.coinpulse.presentation.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.placeholder
import androidx.wear.compose.material3.placeholderShimmer
import androidx.wear.compose.material3.rememberPlaceholderState
import com.example.coinpulse.R
import com.example.coinpulse.domain.PricePoint
import com.example.coinpulse.domain.WatchlistItem
import com.example.coinpulse.presentation.theme.Spacing
import com.example.coinpulse.presentation.watchlist.ChangePill
import com.example.coinpulse.presentation.watchlist.MarketFormatters
import com.example.coinpulse.presentation.watchlist.Monogram
import com.example.coinpulse.presentation.watchlist.StateMessage
import com.example.coinpulse.presentation.watchlist.directionColor
import com.example.coinpulse.presentation.watchlist.errorText
import java.util.Locale

/** Stateful entry point: observes the chart state and forwards the retry action. */
@Composable
fun CoinDetailRoute(item: WatchlistItem, viewModel: CoinDetailViewModel) {
    val chart by viewModel.chart.collectAsStateWithLifecycle()
    CoinDetailScreen(item = item, chart = chart, onRetry = viewModel::onRetry)
}

/** Stateless content for one coin: header, price, 24h sparkline, and 24h low/high. */
@Composable
fun CoinDetailScreen(item: WatchlistItem, chart: ChartUiState, onRetry: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val content: @Composable (PaddingValues) -> Unit = { contentPadding ->
        DetailList(item, chart, listState, spec, contentPadding)
    }

    if (chart is ChartUiState.Unavailable) {
        ScreenScaffold(
            scrollState = listState,
            edgeButton = {
                EdgeButton(
                    onClick = onRetry,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                ) {
                    Text(stringResource(R.string.action_retry))
                }
            },
        ) { contentPadding -> content(contentPadding) }
    } else {
        ScreenScaffold(scrollState = listState) { contentPadding -> content(contentPadding) }
    }
}

@Composable
private fun DetailList(
    item: WatchlistItem,
    chart: ChartUiState,
    listState: TransformingLazyColumnState,
    spec: TransformationSpec,
    contentPadding: PaddingValues,
) {
    val locale = Locale.getDefault()
    val color = directionColor(item.direction)
    val price = MarketFormatters.price(item.price, locale)
    val percent = MarketFormatters.signedPercent(item.changePercent, item.direction, locale)

    TransformingLazyColumn(
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
    ) {
        item {
            // Centered and content-sized: full-width rows get clipped where the round screen is narrow.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
            ) {
                Monogram(item.baseAsset, item.direction)
                Spacer(Modifier.width(Spacing.Sm))
                Text(
                    text = item.baseAsset,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(Spacing.Sm))
                ChangePill(item.direction, percent, color)
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
            ) {
                Text(
                    text = price,
                    style = MaterialTheme.typography.numeralSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.width(Spacing.Xs))
                Text(
                    text = item.quoteAsset,
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.Xs),
                )
            }
        }
        item {
            val chartModifier = Modifier.fillMaxWidth().height(CHART_HEIGHT).transformedHeight(this, spec)
            when (chart) {
                ChartUiState.Loading -> ChartPlaceholder(chartModifier)
                is ChartUiState.Ready -> {
                    val description =
                        stringResource(
                            R.string.chart_description,
                            MarketFormatters.price(chart.points.first().price, locale),
                            price,
                        )
                    Sparkline(
                        points = chart.points,
                        color = color,
                        modifier = chartModifier.semantics { contentDescription = description },
                    )
                }
                is ChartUiState.Unavailable ->
                    StateMessage(
                        title = stringResource(R.string.chart_unavailable_title),
                        message = errorText(chart.reason).second,
                        isError = false,
                        // Content-sized, not chart-sized, so the Retry edge button has room to show.
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    )
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Lg, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
            ) {
                Stat(stringResource(R.string.detail_low), MarketFormatters.price(item.lowPrice, locale))
                Stat(stringResource(R.string.detail_high), MarketFormatters.price(item.highPrice, locale))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyExtraSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChartPlaceholder(modifier: Modifier) {
    val placeholderState = rememberPlaceholderState(isVisible = true)
    val description = stringResource(R.string.chart_loading_description)
    Box(
        modifier =
            modifier
                .semantics { contentDescription = description }
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .placeholder(placeholderState)
                .placeholderShimmer(placeholderState),
    )
}

/**
 * 24h price line with a soft fill, a dashed "price 24h ago" reference, and a marker on the latest
 * point. Drawn on a Canvas; plotting values come from [normalizePrices].
 */
@Composable
private fun Sparkline(points: List<PricePoint>, color: Color, modifier: Modifier = Modifier) {
    val values = remember(points) { normalizePrices(points) }
    val reference = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    Canvas(modifier) {
        val padX = 6.dp.toPx()
        val padY = 10.dp.toPx()
        val width = size.width - 2 * padX
        val height = size.height - 2 * padY
        fun x(index: Int) = padX + width * index / (values.size - 1)
        fun y(value: Float) = padY + height * (1f - value)

        val openY = y(values.first())
        drawLine(
            color = reference,
            start = Offset(0f, openY),
            end = Offset(size.width, openY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )

        val line = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1 until values.size) lineTo(x(i), y(values[i]))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(values.lastIndex), size.height)
            lineTo(x(0), size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent)))
        drawPath(
            line,
            color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        val last = Offset(x(values.lastIndex), y(values.last()))
        drawCircle(color.copy(alpha = 0.25f), radius = 7.dp.toPx(), center = last)
        drawCircle(color, radius = 3.5.dp.toPx(), center = last)
    }
}

private val CHART_HEIGHT = 76.dp
