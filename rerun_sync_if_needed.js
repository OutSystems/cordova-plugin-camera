#!/usr/bin/env node
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const platform = process.env.CAPACITOR_PLATFORM_NAME;
let pluginInstalled = true;
const NAME = 'com.outsystems.plugins.barcode';
// read and parse the app's package.json file
const appDirectory = process.env.CAPACITOR_ROOT_DIR;

if (platform == "android") {
  const androidFileToCheck = path.resolve(appDirectory, 'android/capacitor-cordova-android-plugins/build.gradle');
  if (!fs.existsSync(androidFileToCheck)) {
    console.warn('⚠️  No android code detected, make sure this script runs after sync');
    process.exit(0);
  }
  const androidFileContent = fs.readFileSync(androidFileToCheck, 'utf8');
  pluginInstalled = androidFileContent.includes(NAME);
} else if (platform == "ios") {
  const iosFileToCheck = path.resolve(appDirectory, 'ios/App/App/public/cordova_plugins.js');
  if (!fs.existsSync(iosFileToCheck)) {
    console.warn('⚠️  No ios code detected, make sure this script runs after sync');
    process.exit(0);
  }
  const iOSFileContent = fs.readFileSync(iosFileToCheck, 'utf8');
  pluginInstalled = iOSFileContent.includes(NAME);
} else {
  // the plugin should only be installed on the aformentioned platforms
  return;
}

// Re-trigger sync to install dependency
if (!pluginInstalled) {
  console.log(`Re-running capacitor sync for ${platform}`);
  const syncResult = spawnSync(
    'npx',
    ['cap', 'sync', platform],
    {
      cwd: appDirectory,
      shell: true,
      stdio: 'inherit',
      env: process.env
    }
  );
  if (syncResult.status !== 0) process.exit(syncResult.status);
  console.log(`✅ Success - ${NAME} installed.`);
} else {
  console.log(`✅ ${NAME} already present, skipping.`);
}