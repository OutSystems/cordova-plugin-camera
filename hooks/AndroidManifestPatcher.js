var fs = require('fs'), path = require('path');
var utils = require("./utils");

module.exports = function(context) {

    let platform = context.opts.plugin.platform;

    if(platform === "android") {
      let platformVersion = utils.getPlatformVersion(context);
      if (platformVersion >= "9") {
          let configXML = path.join(context.opts.projectRoot, 'config.xml');

          if (fs.existsSync(configXML)) {
            fs.readFile(configXML, 'utf8', function (err,data) {
              if (err) {
                console.error(err);
                throw new Error('OUTSYSTEMS_PLUGIN_ERROR: Error in Camera Plugin - Unable to read config.xml. Please check the logs for more information.');
              }

              if (!data.includes("android.media.action.IMAGE_CAPTURE")){
                let result = data.replace(/<platform name="android">/g, '<platform name="android"><config-file target="AndroidManifest.xml" parent="/*"><queries><intent><action android:name="android.media.action.IMAGE_CAPTURE" /></intent><intent><action android:name="android.intent.action.GET_CONTENT" /></intent></queries></config-file>');
                fs.writeFile(configXML, result, 'utf8', function (err) {
                  if (err) {
                    console.error(err);
                    throw new Error(`OUTSYSTEMS_PLUGIN_ERROR: Error in Camera Plugin - Unable to write into config.xml. Please check the logs for more information.`);
                  }
                  else {
                    console.log("Camera Plugin: config.xml patched for using MABS 7 successfuly!");
                  }})
              } else {
                console.log("WARNING(Camera Plugin): config.xml was already patched for using Android 11 SDK (Image Capture)");
              }
            });
          } else {
            throw new Error("OUTSYSTEMS_PLUGIN_ERROR: Error in Camera Plugin -  config.xml was not found!");
          }
      } else {
          console.log("Camera Plugin: config.xml not patched for MABS 7. MABS 6.3 or below detected.");
      }
    }
}