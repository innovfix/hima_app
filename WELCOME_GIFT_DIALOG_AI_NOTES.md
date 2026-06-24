# Welcome-Gift Trial Dialog — AI Notes

Design-only welcome-gift / ₹1 trial-offer dialog. No API or payment wiring —
the CTA shows a Toast and the dialog closes; "Skip Now" closes it.

## What was built
- **Home screen** (`MainActivity`): compact welcome-gift dialog shown on open.
- **Wallet screen** (`WalletActivity`): the same offer shown as a home-sized
  dialog over the coin list (the old inline "Subscribe Now" banner is hidden).
- **Auto-rotating info card** (`ViewFlipper`, 2.5s, fade): cycles
  `Features → How it works → Why auto-pay`.
- **Coin cards**: light drop shadow (6dp elevation).

## Behaviour (design only)
- `Start Your Trial - ₹1` → Toast ("Trial activated for ₹1!") + dismiss.
- `Skip Now` → dismiss.
- No backend / payment calls anywhere.

## Files
- Layouts: `dialog_welcome_gift.xml`, `view_wallet_trial_card.xml`
- Drawables: `wg_*` (bg, pill, card, gradient, button, feature circle,
  num circle, today badge) and `ic_wg_*` (calendar, coins, refresh, shield)
- Styles: `Wg*` row/text/divider styles in `styles.xml`
- Code: `MainActivity.showWelcomeGiftDialog()`,
  `WalletActivity.showWalletTrialDialog()`

## Palette
White + pink (`@color/pink`, `pink_bold`) to match the wallet theme.
