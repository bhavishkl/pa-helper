# PA Helper

PA Helper is an Expo React Native Android app with an Android accessibility shortcut for on-demand screen text recognition.

## Run the app

```bash
npm install
npx expo prebuild --platform android
npx expo run:android
```

Use a development build or native Android build; Expo Go cannot load the custom Kotlin module.

## Floating button and one-tap capture

1. Open PA Helper and tap **Enable floating assistant**.
2. Grant **Display over other apps** permission if Android asks, then return to PA Helper and tap the button again.
3. Enable **PA Helper screen capture** in Android Accessibility settings. Android displays its standard warning for accessibility services.
4. Return to PA Helper and tap **Enable floating assistant** again. The floating **PA** button should appear above other apps.
5. Switch to another app and tap **PA** once. PA Helper requests exactly one screenshot and runs on-device ML Kit OCR on that image.
6. Return to PA Helper. The latest extracted text refreshes when the app becomes active, or tap **Refresh captured text**.

**Capture is user-triggered only.** PA Helper does not create a MediaProjection session, keep a virtual display open, continuously take screenshots, or process accessibility events. The accessibility service's screenshot method is called only after the floating button is tapped. The bubble is hidden briefly so it does not cover the captured screen.

Only extracted text, status and capture time are retained locally. The screenshot is processed in memory and is not saved or uploaded.

## Important limitations

- The one-shot AccessibilityService screenshot API requires Android 11 (API 30) or newer.
- Android secure windows (including some banking/payment screens) can block screenshots. PA Helper cannot bypass that protection.
- Android may briefly throttle repeated screenshot requests; wait a moment and tap again if it reports that the screenshot interval is too short.
- OCR is not guaranteed to identify a payment correctly. Verify amount, recipient, transaction reference and status before saving.
- The foreground notification is required to keep the floating button available while PA Helper is in the background.
- Accessibility permission is powerful. PA Helper deliberately does not inspect accessibility events or read window contents; it requests a screenshot only after your tap.
- This is an Android-only native feature. It is not available in Expo Go or on iOS.


## Volume-button screenshot shortcut

- Enable the PA Helper accessibility service in Android Accessibility settings.
- When no media audio is playing, press **Volume Up three times within 1.2 seconds** to request exactly one screenshot and run on-device OCR.
- If only one or two presses occur, PA Helper replays the normal volume increases after the 1.2-second shortcut window.
- If media playback is active, volume keys pass through normally and never trigger the shortcut.
- This shortcut uses Android accessibility key-event filtering. Secure/protected screens may still prevent screenshots, and Android/OEM restrictions can affect global key delivery.


## Volume Up double-press shortcut

- When media is not playing, press **Volume Up twice within 1.2 seconds** to capture exactly one screenshot and run on-device OCR.
- If only one press occurs, PA Helper replays the normal volume increase after the 1.2-second shortcut window.
- If media playback is active, volume keys pass through normally and do not trigger a screenshot.
- Android accessibility key-event filtering is used for the global shortcut. Secure screens may still block screenshots.

## Accounts

- Create multiple accounts with a name and opening balance.
- Select an account and record an amount using **Add amount** or **Minus amount**.
- Each entry records its amount, optional note, timestamp and resulting balance.
- Account data is stored locally in the app's private Android SharedPreferences. It is not synced to a server. Clearing app data or uninstalling the app removes the local records.


## Volume shortcut and account ledger

Every Volume Up press is passed through to Android immediately, so volume changes are not delayed or suppressed. A second press within 1.2 seconds independently triggers a one-shot screenshot, regardless of media playback.

The Accounts screen supports creating, viewing, renaming and deleting accounts, plus adding, viewing, editing and deleting ledger transactions. Account balances are recalculated from the transaction history after edits or deletions. Data is stored locally on the device.


After the double-press OCR completes, PA Helper opens automatically and presents a review card for recognized PhonePe received/paid transactions. Verify the amount, choose the destination account, then confirm; OCR never posts a transaction without confirmation. Account editing includes the account name and opening balance.
