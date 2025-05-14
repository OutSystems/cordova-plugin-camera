#!/usr/bin/env node
const fs   = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const appDirectory = process.env.CAPACITOR_ROOT_DIR;
const APP_PKG = path.resolve(appDirectory, 'package.json');
if (!fs.existsSync(APP_PKG)) {
  console.warn(`⚠️  No app package.json found at ${APP_PKG}`);
  process.exit(0);
}

// 1) Read & parse the app's package.json
const pkg = JSON.parse(fs.readFileSync(APP_PKG, 'utf8'));

// 2) Ensure dependencies exists
pkg.dependencies = pkg.dependencies || {};

// 3) Target name & SSH URL spec
const NAME = 'com.outsystems.plugins.barcode';
const SPEC = 'github:OutSystems/cordova-outsystems-barcode.git#1.2.0';

// 4) Inject if missing
if (!pkg.dependencies[NAME]) {
  console.log(`➕ Adding ${NAME}@${SPEC} to app package.json`);
  pkg.dependencies[NAME] = SPEC;
  fs.writeFileSync(APP_PKG, JSON.stringify(pkg, null, 2) + '\n');
  console.log(`✅ Success - ${NAME} should be detected now.`);
} else {
  console.log(`✅ ${NAME} already present, skipping.`);
}