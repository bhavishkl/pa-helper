const { withAndroidManifest, withSettingsGradle } = require('@expo/config-plugins');

const permissions = [
  'android.permission.SYSTEM_ALERT_WINDOW',
  'android.permission.FOREGROUND_SERVICE',
  'android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION',
  'android.permission.POST_NOTIFICATIONS'
];

module.exports = function withPAHelperAndroid(config) {
  config = withSettingsGradle(config, (config) => {
    // Keep the Android settings aligned with the Expo SDK 54 template.
    // Expo's settings plugin is required to discover and link local Expo modules.
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
    if (!application.service.some((item) => item.$['android:name'] === '.PAHelperCaptureService')) {
      application.service.push({
        $: {
          'android:name': '.PAHelperCaptureService',
          'android:exported': 'false',
          'android:foregroundServiceType': 'mediaProjection'
        }
      });
    }

    application.activity = application.activity || [];
    if (!application.activity.some((item) => item.$['android:name'] === '.PAHelperCapturePermissionActivity')) {
      application.activity.push({
        $: {
          'android:name': '.PAHelperCapturePermissionActivity',
          'android:exported': 'false',
          'android:excludeFromRecents': 'true',
          'android:theme': '@android:style/Theme.Translucent.NoTitleBar'
        }
      });
    }
    return config;
  });
};
