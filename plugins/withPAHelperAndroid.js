const { withAndroidManifest, withSettingsGradle } = require('@expo/config-plugins');

const permissions = [
  'android.permission.SYSTEM_ALERT_WINDOW',
  'android.permission.FOREGROUND_SERVICE',
  'android.permission.FOREGROUND_SERVICE_SPECIAL_USE',
  'android.permission.POST_NOTIFICATIONS',
  'android.permission.SEND_SMS'
];

module.exports = function withPAHelperAndroid(config) {
  config = withSettingsGradle(config, (config) => {
    config.modResults.contents = `pluginManagement {
  def reactNativeGradlePlugin = new File(
    providers.exec {
      workingDir(rootDir)
      commandLine("node", "--print", "require.resolve('@react-native/gradle-plugin/package.json', { paths: [require.resolve('react-native/package.json')] })")
    }.standardOutput.asText.get().trim()
  ).getParentFile().absolutePath
  includeBuild(reactNativeGradlePlugin)

  def expoPluginsPath = new File(
    providers.exec {
      workingDir(rootDir)
      commandLine("node", "--print", "require.resolve('expo-modules-autolinking/package.json', { paths: [require.resolve('expo/package.json')] })")
    }.standardOutput.asText.get().trim(),
    "../android/expo-gradle-plugin"
  ).absolutePath
  includeBuild(expoPluginsPath)
}

plugins {
  id("com.facebook.react.settings")
  id("expo-autolinking-settings")
}

extensions.configure(com.facebook.react.ReactSettingsExtension) { ex ->
  if (System.getenv('EXPO_USE_COMMUNITY_AUTOLINKING') == '1') {
    ex.autolinkLibrariesFromCommand()
  } else {
    ex.autolinkLibrariesFromCommand(expoAutolinking.rnConfigCommand)
  }
}
expoAutolinking.useExpoModules()

rootProject.name = 'PAHelper'

expoAutolinking.useExpoVersionCatalog()

include ':app'
includeBuild(expoAutolinking.reactNativeGradlePlugin)
`;
    return config;
  });

  return withAndroidManifest(config, (config) => {
    const manifest = config.modResults.manifest;
    manifest['uses-permission'] = manifest['uses-permission'] || [];
    for (const name of permissions) {
      if (!manifest['uses-permission'].some((item) => item.$['android:name'] === name)) {
        manifest['uses-permission'].push({ $: { 'android:name': name } });
      }
    }

    const application = manifest.application[0];
    application.service = application.service || [];

    const overlayService = {
      $: {
        'android:name': '.PAHelperOverlayService',
        'android:exported': 'false',
        'android:foregroundServiceType': 'specialUse'
      },
      property: [{
        $: {
          'android:name': 'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE',
          'android:value': 'User-started floating shortcut that captures a screenshot only when tapped'
        }
      }]
    };
    if (!application.service.some((item) => item.$['android:name'] === '.PAHelperOverlayService')) {
      application.service.push(overlayService);
    }

    const accessibilityService = {
      $: {
        'android:name': '.PAHelperAccessibilityService',
        'android:label': '@string/pa_helper_accessibility_label',
        'android:permission': 'android.permission.BIND_ACCESSIBILITY_SERVICE',
        'android:exported': 'true'
      },
      'intent-filter': [{
        action: [{ $: { 'android:name': 'android.accessibilityservice.AccessibilityService' } }]
      }],
      'meta-data': [{
        $: {
          'android:name': 'android.accessibilityservice',
          'android:resource': '@xml/pa_helper_accessibility_service'
        }
      }]
    };
    if (!application.service.some((item) => item.$['android:name'] === '.PAHelperAccessibilityService')) {
      application.service.push(accessibilityService);
    }

    const otpNotificationListener = {
      $: {
        'android:name': '.PAHelperOtpNotificationListener',
        'android:label': '@string/pa_helper_otp_listener_label',
        'android:permission': 'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE',
        'android:exported': 'true'
      },
      'intent-filter': [{
        action: [{ $: { 'android:name': 'android.service.notification.NotificationListenerService' } }]
      }]
    };
    if (!application.service.some((item) => item.$['android:name'] === '.PAHelperOtpNotificationListener')) {
      application.service.push(otpNotificationListener);
    }

    return config;
  });
};
