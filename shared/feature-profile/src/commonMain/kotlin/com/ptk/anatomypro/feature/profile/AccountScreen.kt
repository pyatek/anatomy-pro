package com.ptk.anatomypro.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anatomypro.shared.feature_profile.generated.resources.Res
import anatomypro.shared.feature_profile.generated.resources.account_back
import anatomypro.shared.feature_profile.generated.resources.account_date
import anatomypro.shared.feature_profile.generated.resources.account_delete
import anatomypro.shared.feature_profile.generated.resources.account_delete_about
import anatomypro.shared.feature_profile.generated.resources.account_delete_confirm
import anatomypro.shared.feature_profile.generated.resources.account_delete_failed
import anatomypro.shared.feature_profile.generated.resources.account_delete_heading
import anatomypro.shared.feature_profile.generated.resources.account_delete_kept
import anatomypro.shared.feature_profile.generated.resources.account_delete_subscription
import anatomypro.shared.feature_profile.generated.resources.account_delete_what
import anatomypro.shared.feature_profile.generated.resources.account_delete_yes
import anatomypro.shared.feature_profile.generated.resources.account_failed
import anatomypro.shared.feature_profile.generated.resources.account_leave_no
import anatomypro.shared.feature_profile.generated.resources.account_left_back
import anatomypro.shared.feature_profile.generated.resources.account_left_deleted
import anatomypro.shared.feature_profile.generated.resources.account_left_deleted_about
import anatomypro.shared.feature_profile.generated.resources.account_left_signed_out
import anatomypro.shared.feature_profile.generated.resources.account_left_signed_out_about
import anatomypro.shared.feature_profile.generated.resources.account_left_subscription
import anatomypro.shared.feature_profile.generated.resources.account_loading
import anatomypro.shared.feature_profile.generated.resources.account_nickname_about
import anatomypro.shared.feature_profile.generated.resources.account_nickname_failed
import anatomypro.shared.feature_profile.generated.resources.account_nickname_field
import anatomypro.shared.feature_profile.generated.resources.account_nickname_heading
import anatomypro.shared.feature_profile.generated.resources.account_nickname_invalid
import anatomypro.shared.feature_profile.generated.resources.account_nickname_locked
import anatomypro.shared.feature_profile.generated.resources.account_nickname_locked_until
import anatomypro.shared.feature_profile.generated.resources.account_nickname_rule
import anatomypro.shared.feature_profile.generated.resources.account_nickname_save
import anatomypro.shared.feature_profile.generated.resources.account_nickname_saved
import anatomypro.shared.feature_profile.generated.resources.account_nickname_taken
import anatomypro.shared.feature_profile.generated.resources.account_provider_apple
import anatomypro.shared.feature_profile.generated.resources.account_provider_google
import anatomypro.shared.feature_profile.generated.resources.account_provider_linked
import anatomypro.shared.feature_profile.generated.resources.account_provider_linked_badge
import anatomypro.shared.feature_profile.generated.resources.account_provider_not_linked
import anatomypro.shared.feature_profile.generated.resources.account_retry
import anatomypro.shared.feature_profile.generated.resources.account_sign_in
import anatomypro.shared.feature_profile.generated.resources.account_sign_in_about
import anatomypro.shared.feature_profile.generated.resources.account_sign_in_failed
import anatomypro.shared.feature_profile.generated.resources.account_sign_in_heading
import anatomypro.shared.feature_profile.generated.resources.account_sign_in_history
import anatomypro.shared.feature_profile.generated.resources.account_sign_in_rejected
import anatomypro.shared.feature_profile.generated.resources.account_sign_in_with
import anatomypro.shared.feature_profile.generated.resources.account_sign_out
import anatomypro.shared.feature_profile.generated.resources.account_sign_out_confirm
import anatomypro.shared.feature_profile.generated.resources.account_sign_out_failed
import anatomypro.shared.feature_profile.generated.resources.account_sign_out_yes
import anatomypro.shared.feature_profile.generated.resources.account_signed_in
import anatomypro.shared.feature_profile.generated.resources.account_signed_in_merged
import anatomypro.shared.feature_profile.generated.resources.account_title
import com.ptk.anatomypro.core.data.model.SignInProvider
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.AccentMuted
import com.ptk.anatomypro.core.designsystem.Badge
import com.ptk.anatomypro.core.designsystem.CorrectText
import com.ptk.anatomypro.core.designsystem.Hairline
import com.ptk.anatomypro.core.designsystem.IncorrectAmber
import com.ptk.anatomypro.core.designsystem.IncorrectOutline
import com.ptk.anatomypro.core.designsystem.IncorrectText
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
import com.ptk.anatomypro.core.designsystem.NavSurface
import com.ptk.anatomypro.core.designsystem.OutlineButton
import com.ptk.anatomypro.core.designsystem.Overline
import com.ptk.anatomypro.core.designsystem.Panel
import com.ptk.anatomypro.core.designsystem.PrimaryButton
import com.ptk.anatomypro.core.designsystem.RowAction
import com.ptk.anatomypro.core.designsystem.TextPrimary
import com.ptk.anatomypro.core.designsystem.TextSecondary
import com.ptk.anatomypro.core.designsystem.TextStrong
import com.ptk.anatomypro.core.designsystem.TextTertiary
import com.ptk.anatomypro.core.designsystem.TopBar
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The account: nickname, signing in, signing out, deleting (backend spec §5).
 *
 * The prototype has no screen for this; it is a draft for the owner to review, laid out as
 * the pack manager is — a pushed screen of headed panels, each action on the row it
 * concerns and each question asked there rather than in a dialog (plan decision 5).
 *
 * Once the account has been left there is nothing to edit, and the screen says only what
 * happened. That is also where a subscription still running is said a second time: it is
 * the one thing deleting an account does not undo (§5.4).
 */
@Composable
fun AccountScreen(
    state: AccountUiState,
    onNicknameChanged: (String) -> Unit,
    onSaveNickname: () -> Unit,
    onSignIn: (SignInProvider) -> Unit,
    onLeaveRequested: (Leaving) -> Unit,
    onLeaveConfirmed: () -> Unit,
    onLeaveDismissed: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalAnatomyType.current

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(stringResource(Res.string.account_title).uppercase(), backLabel = stringResource(Res.string.account_back), onBack = onBack)

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                state.left != null -> LeftAccount(state.left, onBack)
                state.isLoading -> Text(stringResource(Res.string.account_loading), style = type.body, color = TextTertiary)
                state.failed || state.account == null -> {
                    Text(stringResource(Res.string.account_failed), style = type.body, color = TextPrimary)
                    OutlineButton(stringResource(Res.string.account_retry), onClick = onRetry)
                }
                else -> {
                    Overline(stringResource(Res.string.account_nickname_heading), color = TextSecondary)
                    NicknamePanel(state, onNicknameChanged, onSaveNickname)

                    // With nothing to sign in with and nothing signed in to, there is nothing to say here.
                    if (state.providers.isNotEmpty()) {
                        Overline(stringResource(Res.string.account_sign_in_heading), color = TextSecondary, modifier = Modifier.padding(top = 6.dp))
                        SignInPanel(state, onSignIn, onLeaveRequested, onLeaveConfirmed, onLeaveDismissed)
                    }

                    Overline(stringResource(Res.string.account_delete_heading), color = TextSecondary, modifier = Modifier.padding(top = 6.dp))
                    DeletePanel(state, onLeaveRequested, onLeaveConfirmed, onLeaveDismissed)
                }
            }
        }
    }
}

@Composable
private fun NicknamePanel(state: AccountUiState, onNicknameChanged: (String) -> Unit, onSaveNickname: () -> Unit) {
    val type = LocalAnatomyType.current
    val locked = state.lockedUntil
    Panel {
        Text(stringResource(Res.string.account_nickname_about), style = type.note, color = TextSecondary)

        val fieldShape = RoundedCornerShape(12.dp)
        val fieldLabel = stringResource(Res.string.account_nickname_field)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(fieldShape)
                .background(NavSurface)
                .border(BorderStroke(1.dp, Hairline), fieldShape)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = state.draft,
                onValueChange = onNicknameChanged,
                singleLine = true,
                // A locked nickname is shown, not offered for editing.
                readOnly = locked != null,
                textStyle = type.latin.copy(fontSize = 17.sp, color = if (locked == null) TextPrimary else TextSecondary),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSaveNickname() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = fieldLabel },
            )
        }

        if (locked != null) {
            Text(stringResource(Res.string.account_nickname_locked_until, dateText(locked)), style = type.caption, color = IncorrectText)
        } else {
            Text(stringResource(Res.string.account_nickname_rule), style = type.caption, color = TextTertiary)
        }

        // Announced as it arrives: a refusal must not be something only sighted users learn.
        Notice(
            when (state.nicknameNotice) {
                NicknameNotice.SAVED -> Res.string.account_nickname_saved
                NicknameNotice.INVALID -> Res.string.account_nickname_invalid
                NicknameNotice.TAKEN -> Res.string.account_nickname_taken
                // The date, once the account has been read again, is in the line above.
                NicknameNotice.LOCKED -> Res.string.account_nickname_locked.takeIf { locked == null }
                NicknameNotice.FAILED -> Res.string.account_nickname_failed
                null -> null
            },
            good = state.nicknameNotice == NicknameNotice.SAVED,
        )

        if (locked == null) {
            PrimaryButton(stringResource(Res.string.account_nickname_save), onClick = onSaveNickname, enabled = state.canSave)
        }
    }
}

@Composable
private fun SignInPanel(
    state: AccountUiState,
    onSignIn: (SignInProvider) -> Unit,
    onLeaveRequested: (Leaving) -> Unit,
    onLeaveConfirmed: () -> Unit,
    onLeaveDismissed: () -> Unit,
) {
    val type = LocalAnatomyType.current
    Panel {
        Text(stringResource(Res.string.account_sign_in_about), style = type.note, color = TextSecondary)
        Text(stringResource(Res.string.account_sign_in_history), style = type.note, color = TextSecondary)

        for (provider in state.providers) {
            val name = stringResource(
                when (provider) {
                    SignInProvider.GOOGLE -> Res.string.account_provider_google
                    SignInProvider.APPLE -> Res.string.account_provider_apple
                },
            )
            val linked = state.account?.linked?.contains(provider) == true
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = type.bodyStrong.copy(fontSize = 14.sp), color = TextPrimary)
                    Text(
                        stringResource(if (linked) Res.string.account_provider_linked else Res.string.account_provider_not_linked),
                        style = type.note,
                        color = TextSecondary,
                    )
                }
                when {
                    // In words as well as in the accent: never colour alone (§12).
                    linked -> Badge(stringResource(Res.string.account_provider_linked_badge), color = Accent, outline = AccentMuted)
                    state.canSignInWith(provider) -> {
                        // Two rows say "Sign in"; aloud, each says with what.
                        val spoken = stringResource(Res.string.account_sign_in_with, name)
                        RowAction(
                            stringResource(Res.string.account_sign_in),
                            onClick = { onSignIn(provider) },
                            modifier = Modifier.semantics { contentDescription = spoken },
                        )
                    }
                }
            }
        }

        Notice(
            when (state.signInNotice) {
                SignInNotice.SIGNED_IN -> Res.string.account_signed_in
                SignInNotice.MERGED -> Res.string.account_signed_in_merged
                SignInNotice.REJECTED -> Res.string.account_sign_in_rejected
                SignInNotice.FAILED -> Res.string.account_sign_in_failed
                null -> null
            },
            good = state.signInNotice == SignInNotice.SIGNED_IN || state.signInNotice == SignInNotice.MERGED,
        )

        if (state.canSignOut) {
            if (state.confirming == Leaving.SIGN_OUT) {
                Text(stringResource(Res.string.account_sign_out_confirm), style = type.caption, color = TextPrimary)
                Notice(Res.string.account_sign_out_failed.takeIf { state.leavingFailed }, good = false)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RowAction(stringResource(Res.string.account_sign_out_yes), onLeaveConfirmed, color = IncorrectAmber)
                    RowAction(stringResource(Res.string.account_leave_no), onLeaveDismissed)
                }
            } else {
                Row { RowAction(stringResource(Res.string.account_sign_out), { onLeaveRequested(Leaving.SIGN_OUT) }, color = TextSecondary) }
            }
        }
    }
}

@Composable
private fun DeletePanel(
    state: AccountUiState,
    onLeaveRequested: (Leaving) -> Unit,
    onLeaveConfirmed: () -> Unit,
    onLeaveDismissed: () -> Unit,
) {
    val type = LocalAnatomyType.current
    val confirming = state.confirming == Leaving.DELETE
    Panel(outline = if (confirming) IncorrectOutline else Hairline) {
        if (!confirming) {
            Text(stringResource(Res.string.account_delete_about), style = type.note, color = TextSecondary)
            Row { RowAction(stringResource(Res.string.account_delete), { onLeaveRequested(Leaving.DELETE) }, color = TextSecondary) }
        } else {
            Text(stringResource(Res.string.account_delete_confirm), style = type.bodyStrong, color = TextPrimary)
            Text(stringResource(Res.string.account_delete_what), style = type.caption, color = TextStrong)
            Text(stringResource(Res.string.account_delete_kept), style = type.caption, color = TextStrong)
            // Said before the yes, not only after it: a subscription that goes on charging
            // for an account that no longer exists is the one surprise deleting can hold (§5.4).
            Text(stringResource(Res.string.account_delete_subscription), style = type.caption, color = IncorrectText)
            Notice(Res.string.account_delete_failed.takeIf { state.leavingFailed }, good = false)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RowAction(stringResource(Res.string.account_delete_yes), onLeaveConfirmed, color = IncorrectAmber)
                RowAction(stringResource(Res.string.account_leave_no), onLeaveDismissed)
            }
        }
    }
}

/** What is left to say once the account is no longer this device's. */
@Composable
private fun LeftAccount(left: Left, onBack: () -> Unit) {
    val type = LocalAnatomyType.current
    Column(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (left) {
            Left.SignedOut -> {
                Text(stringResource(Res.string.account_left_signed_out), style = type.title.copy(fontSize = 23.sp), color = TextPrimary)
                Text(stringResource(Res.string.account_left_signed_out_about), style = type.body, color = TextStrong)
            }
            is Left.Deleted -> {
                Text(stringResource(Res.string.account_left_deleted), style = type.title.copy(fontSize = 23.sp), color = TextPrimary)
                Text(stringResource(Res.string.account_left_deleted_about), style = type.body, color = TextStrong)
                if (left.subscriptionStillActive) {
                    Panel(outline = IncorrectOutline) {
                        Text(stringResource(Res.string.account_left_subscription), style = type.body, color = IncorrectText)
                    }
                }
            }
        }
    }
    OutlineButton(stringResource(Res.string.account_left_back), onClick = onBack, modifier = Modifier.padding(top = 10.dp))
}

/**
 * One line that says how the last thing asked for went, read out as it appears. Good news
 * and bad are told apart by the mark in front of the words, not only by their colour (§12).
 * The region stays in the tree when it is empty, so that what arrives in it is announced.
 */
@Composable
private fun Notice(message: StringResource?, good: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        if (message != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (good) "✓" else "!",
                    style = LocalAnatomyType.current.bodyStrong.copy(fontSize = 14.sp),
                    color = if (good) CorrectText else IncorrectAmber,
                    modifier = Modifier.clearAndSetSemantics { },
                )
                Text(
                    stringResource(message),
                    style = LocalAnatomyType.current.caption,
                    color = if (good) CorrectText else IncorrectText,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A day as this language writes one. The pieces are padded here: the resource only orders them. */
@Composable
private fun dateText(date: LocalDate): String = stringResource(
    Res.string.account_date,
    date.day.toString().padStart(2, '0'),
    date.month.number.toString().padStart(2, '0'),
    date.year.toString(),
)
