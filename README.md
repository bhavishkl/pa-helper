# PA Helper

PA Helper is an Expo React Native Android app with a native Kotlin floating button and on-demand screen text recognition.

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
