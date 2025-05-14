#!/usr/bin/env node
const fs   = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const APP_PKG = path.resolve(process.cwd(), 'package.json');
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
  /*console.log('Run npm install');
  const res = spawnSync('npm', ['install'], { stdio: 'inherit' });
  if (res.status !== 0) {
    console.error(`❌ Failed to install ${NAME}@${SPEC}`);
    process.exit(res.status);
  }*/
  console.log(`✅ Success - ${NAME} should be installed now.`);
} else {
  console.log(`✅ ${NAME} already present, skipping.`);
}