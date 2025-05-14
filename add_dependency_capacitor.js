#!/usr/bin/env node
const fs   = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const platform = process.env.CAPACITOR_PLATFORM_NAME;
if (platform != "android" && platform != "ios") {
  // the plugin should only be installed on these platforms
  return;
}

// read and parse the app's package.json file
const appDirectory = process.env.CAPACITOR_ROOT_DIR;
const APP_PKG = path.resolve(appDirectory, 'package.json');
if (!fs.existsSync(APP_PKG)) {
  console.warn(`⚠️  No app package.json found at ${APP_PKG}`);
  process.exit(0);
}
const pkg = JSON.parse(fs.readFileSync(APP_PKG, 'utf8'));

pkg.dependencies = pkg.dependencies || {};

// 3) Target package name and where to get it / what version to get.
const NAME = 'com.outsystems.plugins.barcode';
const SPEC = 'github:OutSystems/cordova-outsystems-barcode.git#1.2.0';

// 4) Inject the dependency if missing, re-triggering sync
if (!pkg.dependencies[NAME]) {
  console.log(`➕ Adding ${NAME}@${SPEC} to app package.json`);
  pkg.dependencies[NAME] = SPEC;
  fs.writeFileSync(APP_PKG, JSON.stringify(pkg, null, 2) + '\n');
  console.log(`Re-running capacitor sync for `);
  const syncResult = spawnSync('npx', ['cap', `sync ${platform}`], {
    cwd: appDirectory,
    stdio: 'inherit'
  });
  if (syncResult.status !== 0) process.exit(syncResult.status);
  console.log(`✅ Success - ${NAME} synced.`);
} else {
  console.log(`✅ ${NAME} already present, skipping.`);
}