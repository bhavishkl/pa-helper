# PA Helper

PA Helper is an Expo React Native Android app with a native Kotlin screen-context capture module.

## Run the app

```bash
npm install
npx expo prebuild --platform android
npx expo run:android
```

Use a development build or native Android build; Expo Go cannot load the custom Kotlin module.

## Floating screen capture (Android)

1. Open PA Helper and tap **Enable floating assistant**.
2. Grant **Display over other apps** permission in Android settings and return to PA Helper.
3. Tap **Enable floating assistant** again.
4. Approve Android's screen-capture consent dialog.
5. Switch to another app. The PA bubble stays above apps. Tap it to capture the visible screen.
6. Return to PA Helper. The latest OCR text refreshes when the app becomes active, or tap **Refresh captured text**.

The capture service uses Android MediaProjection and on-device Google ML Kit text recognition. The screenshot is processed in memory; only extracted text, status and capture time are retained locally. No screenshot or extracted text is uploaded.

## Important limitations

- Android requires explicit user consent for MediaProjection. Consent may need to be granted again after the service or process is stopped.
- Apps that mark their windows secure (including some banking/payment screens) can block screen capture. PA Helper cannot bypass that protection.
- OCR is not guaranteed to identify a payment correctly. Verify amount, recipient, transaction reference and status before saving.
- The foreground notification is required while the floating capture service runs.
- This is an Android-only native feature. It is not available in Expo Go or on iOS.
