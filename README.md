# PA Helper

A starter React Native Android app built with Expo. The home screen includes a welcome header, a primary call to action, quick-access cards for tasks, notes, reminders and settings, and a small getting-started panel.

## Run locally

1. Install Node.js (LTS).
2. Install dependencies: `npm install`
3. Start Expo: `npx expo start`
4. Install Expo Go on an Android phone and scan the QR code, or press `a` when an Android emulator is running.

## Build an Android APK

For a cloud Android build using EAS:

```bash
npx eas-cli build:configure
npx eas-cli build --platform android --profile preview
```

This is a starter UI. The quick-access cards and primary button are visual entry points; task, notes, reminder and settings functionality can be added in later iterations.
