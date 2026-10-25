package io.github.conorryan.whistletabber.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.music.WhistleKey

@StringRes
fun whistleLabelRes(key: WhistleKey): Int = when (key) {
    WhistleKey.HIGH_G -> R.string.whistle_high_g
    WhistleKey.F -> R.string.whistle_f
    WhistleKey.E_FLAT -> R.string.whistle_e_flat
    WhistleKey.D -> R.string.whistle_d
    WhistleKey.C -> R.string.whistle_c
    WhistleKey.B_FLAT -> R.string.whistle_b_flat
    WhistleKey.A -> R.string.whistle_a
    WhistleKey.LOW_G -> R.string.whistle_low_g
    WhistleKey.LOW_F -> R.string.whistle_low_f
    WhistleKey.LOW_D -> R.string.whistle_low_d
    WhistleKey.LOW_C -> R.string.whistle_low_c
}

@Composable
fun whistleLabel(key: WhistleKey): String = stringResource(whistleLabelRes(key))
