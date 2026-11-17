const { withAndroidManifest } = require('@expo/config-plugins');

const permissions = [
  'android.permission.SYSTEM_ALERT_WINDOW',
  'android.permission.FOREGROUND_SERVICE',
  'android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION',
  'android.permission.POST_NOTIFICATIONS'
];

module.exports = function withPAHelperAndroid(config) {
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
