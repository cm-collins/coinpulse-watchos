package com.example.coinpulse.presentation.watchlist

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BrushPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.placeholder
import androidx.wear.compose.material3.placeholderShimmer
import androidx.wear.compose.material3.rememberPlaceholderState
import com.example.coinpulse.R
import com.example.coinpulse.domain.FailureReason
import com.example.coinpulse.domain.PriceDirection
import com.example.coinpulse.domain.WatchlistItem
import com.example.coinpulse.presentation.theme.LocalCoinPulseColors
import com.example.coinpulse.presentation.theme.Spacing
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Stateful entry point: observes the ViewModel and forwards user actions. */
@Composable
fun WatchlistRoute(viewModel: WatchlistViewModel, onCoinClick: (WatchlistItem) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WatchlistScreen(state = state, onRefresh = viewModel::onRefresh, onCoinClick = onCoinClick)
}

/** Stateless content: renders [state]; previewed and tested without a ViewModel. */
@Composable
fun WatchlistScreen(state: WatchlistUiState, onRefresh: () -> Unit, onCoinClick: (WatchlistItem) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val actionLabel =
        when (state) {
            is WatchlistUiState.Content -> R.string.action_refresh
            is WatchlistUiState.Error -> R.string.action_retry
            else -> null
        }

    if (actionLabel != null) {
        ScreenScaffold(
            scrollState = listState,
            edgeButton = {
                EdgeButton(
                    onClick = onRefresh,
                    enabled = !(state is WatchlistUiState.Content && state.isRefreshing),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                ) {
                    Text(stringResource(actionLabel))
                }
            },
        ) { contentPadding ->
            WatchlistList(state, listState, transformationSpec, contentPadding, onCoinClick)
        }
    } else {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            WatchlistList(state, listState, transformationSpec, contentPadding, onCoinClick)
        }
    }
}

@Composable
private fun WatchlistList(
    state: WatchlistUiState,
    listState: TransformingLazyColumnState,
    spec: TransformationSpec,
    contentPadding: PaddingValues,
    onCoinClick: (WatchlistItem) -> Unit,
) {
    TransformingLazyColumn(
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
    ) {
        item {
            // Brand + freshness in one compact item so the first coin stays near the top.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).padding(bottom = Spacing.Xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    PulseMark()
                    Spacer(Modifier.width(Spacing.Sm))
                    Text(
                        text = stringResource(R.string.brand_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.Xs))
                FreshnessLabel(state)
            }
        }
        when (state) {
            WatchlistUiState.Loading ->
                item { SkeletonCard(Modifier.fillMaxWidth().transformedHeight(this, spec)) }
            WatchlistUiState.Empty ->
                item {
                    StateMessage(
                        title = stringResource(R.string.empty_title),
                        message = stringResource(R.string.empty_message),
                        isError = false,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    )
                }
            is WatchlistUiState.Error ->
                item {
                    val (title, message) = errorText(state.reason)
                    StateMessage(
                        title = title,
                        message = message,
                        isError = true,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    )
                }
            is WatchlistUiState.Content -> {
                if (state.items.size > 1) {
                    item {
                        BreadthChips(
                            up = state.upCount,
                            down = state.downCount,
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        )
                    }
                }
                items(state.items, key = { it.symbol }) { coin ->
                    WatchlistCard(
                        item = coin,
                        onClick = { onCoinClick(coin) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }
            }
        }
    }
}

@Composable
internal fun errorText(reason: FailureReason): Pair<String, String> =
    when (reason) {
        FailureReason.Offline ->
            stringResource(R.string.error_offline_title) to stringResource(R.string.error_offline_message)
        FailureReason.RateLimited ->
            stringResource(R.string.error_rate_limited_title) to
                stringResource(R.string.error_rate_limited_message)
        FailureReason.Unknown ->
            stringResource(R.string.error_unknown_title) to stringResource(R.string.error_unknown_message)
    }

@Composable
private fun FreshnessLabel(state: WatchlistUiState, modifier: Modifier = Modifier) {
    val formatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()) }
    val colors = LocalCoinPulseColors.current
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val (text, color) =
        when (state) {
            WatchlistUiState.Loading -> stringResource(R.string.freshness_loading) to neutral
            WatchlistUiState.Empty,
            is WatchlistUiState.Error -> return
            is WatchlistUiState.Content -> {
                val time = formatter.format(state.updatedAt)
                when {
                    state.refreshFailed -> stringResource(R.string.freshness_refresh_failed, time) to colors.stale
                    state.isStale && state.isSample -> stringResource(R.string.freshness_sample_stale, time) to colors.stale
                    state.isStale -> stringResource(R.string.freshness_stale, time) to colors.stale
                    state.isRefreshing -> stringResource(R.string.freshness_refreshing) to neutral
                    state.isSample -> stringResource(R.string.freshness_sample, time) to neutral
                    else -> stringResource(R.string.freshness_updated, time) to neutral
                }
            }
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyExtraSmall,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun WatchlistCard(
    item: WatchlistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation,
) {
    val locale = Locale.getDefault()
    val price = MarketFormatters.price(item.price, locale)
    val percent = MarketFormatters.signedPercent(item.changePercent, item.direction, locale)
    val magnitude = MarketFormatters.percentMagnitude(item.changePercent, locale)
    val range = item.rangePosition
    val baseDescription =
        when (item.direction) {
            PriceDirection.Up ->
                stringResource(R.string.row_description_up, item.baseAsset, item.quoteAsset, price, magnitude)
            PriceDirection.Down ->
                stringResource(R.string.row_description_down, item.baseAsset, item.quoteAsset, price, magnitude)
            PriceDirection.Flat ->
                stringResource(R.string.row_description_flat, item.baseAsset, item.quoteAsset, price)
        }
    val description =
        if (range == null) {
            baseDescription
        } else {
            "$baseDescription, ${stringResource(R.string.range_description, (range * 100).toInt())}"
        }
    val changeColor = directionColor(item.direction)

    // Faint directional glow on the leading edge; it only reinforces the sign, arrow and label.
    val base = MaterialTheme.colorScheme.surfaceContainer
    val glow =
        remember(changeColor, base, item.direction) {
            val tint = if (item.direction == PriceDirection.Flat) base else changeColor.copy(alpha = 0.16f).compositeOver(base)
            BrushPainter(Brush.horizontalGradient(0f to tint, 0.8f to base, 1f to base))
        }

    // The card is a button that opens the coin detail; its children are merged into one spoken
    // description (symbol, price, change, range) instead of being read piece by piece.
    Card(
        onClick = onClick,
        containerPainter = glow,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        contentPadding = PaddingValues(horizontal = Spacing.Md, vertical = Spacing.Sm),
        transformation = transformation,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Monogram(item.baseAsset, item.direction)
            Spacer(Modifier.width(Spacing.Sm))
            Text(
                text = item.baseAsset,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ChangePill(item.direction, percent, changeColor)
        }
        Spacer(Modifier.height(Spacing.Xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = price,
                style = MaterialTheme.typography.numeralExtraSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.width(Spacing.Xs))
            Text(
                text = item.quoteAsset,
                style = MaterialTheme.typography.bodyExtraSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(bottom = Spacing.Xs),
            )
        }
        if (range != null) {
            Spacer(Modifier.height(Spacing.Xs))
            RangeBar(range)
        }
    }
}

@Composable
internal fun directionColor(direction: PriceDirection): Color {
    val colors = LocalCoinPulseColors.current
    return when (direction) {
        PriceDirection.Up -> colors.gain
        PriceDirection.Down -> colors.loss
        PriceDirection.Flat -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** Neutral monogram with a thin direction-tinted ring; no third-party coin logos. */
@Composable
internal fun Monogram(symbol: String, direction: PriceDirection) {
    val ring = directionColor(direction).copy(alpha = 0.45f)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .drawRing(ring),
    ) {
        Text(
            text = symbol.take(1),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun Modifier.drawRing(color: Color): Modifier =
    drawBehind {
        val stroke = 1.5.dp.toPx()
        drawCircle(color, radius = size.minDimension / 2 - stroke / 2, style = Stroke(width = stroke))
    }

/** Tinted capsule with the arrow and signed percent; direction is also in the text itself. */
@Composable
internal fun ChangePill(direction: PriceDirection, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.clip(CircleShape)
                .background(color.copy(alpha = 0.16f))
                .padding(horizontal = Spacing.Sm, vertical = Spacing.Xs),
    ) {
        DirectionArrow(direction, color)
        Spacer(Modifier.width(Spacing.Xs))
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

/** Decorative triangle (up/down) or dash (flat); the card's semantics carry the meaning. */
@Composable
internal fun DirectionArrow(direction: PriceDirection, color: Color) {
    Canvas(Modifier.size(10.dp)) {
        when (direction) {
            PriceDirection.Up ->
                drawPath(
                    Path().apply {
                        moveTo(size.width / 2, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    },
                    color,
                )
            PriceDirection.Down ->
                drawPath(
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width / 2, size.height)
                        close()
                    },
                    color,
                )
            PriceDirection.Flat ->
                drawRect(
                    color,
                    topLeft = Offset(0f, size.height * 0.4f),
                    size = Size(size.width, size.height * 0.2f),
                )
        }
    }
}

/** 24h range: a quiet track, filled up to a marker showing where the price sits between low and high. */
@Composable
private fun RangeBar(position: Float) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val fill = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
    val marker = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val barHeight = 4.dp.toPx()
        val radius = 4.dp.toPx()
        val centerY = size.height / 2
        val markerX = radius + (size.width - 2 * radius) * position
        val corner = CornerRadius(barHeight / 2)
        drawRoundRect(track, Offset(0f, centerY - barHeight / 2), Size(size.width, barHeight), corner)
        drawRoundRect(fill, Offset(0f, centerY - barHeight / 2), Size(markerX, barHeight), corner)
        drawCircle(marker, radius, Offset(markerX, centerY))
    }
}

/** How many coins are up and down, e.g. "▲ 3  ▼ 2". */
@Composable
private fun BreadthChips(up: Int, down: Int, modifier: Modifier = Modifier) {
    val colors = LocalCoinPulseColors.current
    val description = stringResource(R.string.breadth_description, up, down)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        if (up > 0) BreadthChip(PriceDirection.Up, up, colors.gain)
        if (down > 0) BreadthChip(PriceDirection.Down, down, colors.loss)
    }
}

@Composable
private fun BreadthChip(direction: PriceDirection, count: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.clip(CircleShape)
                .background(color.copy(alpha = 0.16f))
                .padding(horizontal = Spacing.Sm, vertical = Spacing.Xs),
    ) {
        DirectionArrow(direction, color)
        Spacer(Modifier.width(Spacing.Xs))
        Text(text = count.toString(), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Restrained brand mark: a tiny pulse line. Decorative. */
@Composable
private fun PulseMark() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(width = 18.dp, height = 12.dp)) {
        val w = size.width
        val h = size.height
        val path =
            Path().apply {
                moveTo(0f, h / 2)
                lineTo(w * 0.28f, h / 2)
                lineTo(w * 0.44f, 0f)
                lineTo(w * 0.60f, h)
                lineTo(w * 0.72f, h / 2)
                lineTo(w, h / 2)
            }
        drawPath(path, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun SkeletonCard(modifier: Modifier = Modifier) {
    val placeholderState = rememberPlaceholderState(isVisible = true)
    val loadingDescription = stringResource(R.string.loading_description)
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
        modifier =
            modifier
                .semantics { contentDescription = loadingDescription }
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(Spacing.Md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
            Box(Modifier.size(32.dp).placeholder(placeholderState).placeholderShimmer(placeholderState))
            Box(Modifier.height(Spacing.Lg).width(64.dp).placeholder(placeholderState).placeholderShimmer(placeholderState))
        }
        Box(Modifier.height(Spacing.Xl).fillMaxWidth(0.8f).placeholder(placeholderState).placeholderShimmer(placeholderState))
        Box(Modifier.height(Spacing.Sm).fillMaxWidth().placeholder(placeholderState).placeholderShimmer(placeholderState))
    }
}

@Composable
internal fun StateMessage(title: String, message: String, isError: Boolean, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
