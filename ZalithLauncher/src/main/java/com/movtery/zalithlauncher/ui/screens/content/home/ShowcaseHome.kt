package com.movtery.zalithlauncher.ui.screens.content.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.account.getAccountTypeName
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.ui.screens.content.elements.PlayerFace
import com.movtery.zalithlauncher.ui.screens.content.elements.VersionIconImage

/**
 * Главный экран «Витрина сборок»: аккаунт, листаемые карточки версий и большая кнопка «Играть»
 * под правым большим пальцем.
 */
@Composable
fun ShowcaseHome(
    onLaunchGame: (Version?) -> Unit,
    toAccountManage: () -> Unit,
    toVersionManage: () -> Unit,
    toVersionSettings: (Version) -> Unit,
    modifier: Modifier = Modifier,
) {
    val versions by VersionsManager.versions.collectAsStateWithLifecycle()
    val current by VersionsManager.currentVersion.collectAsStateWithLifecycle()
    val account by AccountsManager.currentAccountFlow.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // выбранная сборка всегда видна, даже если она в конце списка
    LaunchedEffect(current?.getVersionName(), versions.size) {
        val index = versions.indexOfFirst { it.getVersionName() == current?.getVersionName() }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Box(modifier = modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = toAccountManage)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val acc = account
                if (acc != null) {
                    PlayerFace(account = acc, avatarSize = 40.dp)
                } else {
                    Icon(
                        modifier = Modifier.size(40.dp),
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = null
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = acc?.username ?: stringResource(R.string.account_add_new_account),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                    if (acc != null) {
                        Text(
                            text = getAccountTypeName(acc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.showcase_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(12.dp))

            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(end = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(versions, key = { it.getVersionName() }) { version ->
                    val selected = version.getVersionName() == current?.getVersionName()
                    VersionShowcaseCard(
                        version = version,
                        selected = selected,
                        onClick = {
                            if (selected) toVersionSettings(version)
                            else VersionsManager.saveCurrentVersion(version.getVersionName())
                        }
                    )
                }
                item(key = "new") {
                    NewVersionCard(onClick = toVersionManage)
                }
            }
        }

        PlayButton(
            modifier = Modifier.align(Alignment.BottomEnd),
            enabled = current != null,
            onClick = { onLaunchGame(current) }
        )
    }
}

/** Свой цвет у каждой сборки: из имени, чтобы не прыгал между запусками */
private val CardPalette = listOf(
    Color(0xFF3F8F4A) to Color(0xFF1D3F22),
    Color(0xFF8A4FD1) to Color(0xFF2D1A52),
    Color(0xFFD1784F) to Color(0xFF52281A),
    Color(0xFF4F9AD1) to Color(0xFF1A3552),
    Color(0xFFC9A23A) to Color(0xFF4A3A10),
    Color(0xFFD14F7E) to Color(0xFF521A33),
)

@Composable
private fun VersionShowcaseCard(
    version: Version,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val width by animateDpAsState(if (selected) 250.dp else 196.dp, label = "cardWidth")
    val (top, bottom) = CardPalette[Math.floorMod(version.getVersionName().hashCode(), CardPalette.size)]
    val info = version.getVersionInfo()
    val loader = info?.primaryLoader?.loader?.displayName ?: stringResource(R.string.showcase_vanilla)

    Box(
        modifier = Modifier
            .width(width)
            .height(158.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(top, bottom)))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Text(
            modifier = Modifier
                .align(Alignment.TopStart)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 9.dp, vertical = 3.dp),
            text = loader,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1
        )
        VersionIconImage(
            version = version,
            modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
        )
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                text = version.getVersionName(),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (version.isValid()) version.getVersionSummary() else stringResource(R.string.showcase_broken),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NewVersionCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .height(158.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            modifier = Modifier.size(32.dp),
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null
        )
        Spacer(Modifier.height(6.dp))
        Text(text = stringResource(R.string.showcase_new), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PlayButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.45f, label = "playAlpha")
    Row(
        modifier = modifier
            .height(64.dp)
            .clip(CircleShape)
            .background(Color(0xFF5EE07A).copy(alpha = alpha))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 34.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(28.dp),
            painter = painterResource(R.drawable.ic_play_arrow_filled),
            contentDescription = null,
            tint = Color(0xFF06210D)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.main_launch_game),
            color = Color(0xFF06210D),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp
        )
    }
}
