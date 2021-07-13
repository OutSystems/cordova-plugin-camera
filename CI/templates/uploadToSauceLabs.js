var fetch = require('node-fetch');
var btoa = require('btoa');
var fs = require("fs");
const { exec } = require('child_process');
var configurations = require('./configurations.json');
const SauceLabs = require('outsystems-e2e-reactive-mobile-test-framework/dist/framework').Saucelabs;

if(configurations.sauceLabsInfo == null) {
    throw new Error("Missing sauceLabsInfo object configuration in package.json");
}

if(process.env.npm_config_buildsPath == null) {
    throw new Error("Missing builds folder path argument \"buildsPath\"");
}

if(process.env.SAUCELABS_USER_NAME == null) {
    throw new Error("Missing Sauce Labs User");
}

if(process.env.SAUCELABS_USER_KEY == null) {
    throw new Error("Missing Sauce Labs User Key");
}

var dir = process.env.npm_config_buildsPath;
var sauceLabsInfo = configurations.sauceLabsInfo;

console.log("Start process")

var uploads = [];
var result = [];

fs.readdirSync(dir).forEach(function(appDir) {
    console.log("Start reading directorys")
    fs.stat(dir + appDir, (err, stats) => {
      if (err) { 
        throw new Error(err);
      } else if (stats.isDirectory()) {
        console.log("Directory " + stats)
        console.log("Read files on" + dir + appDir)
        fs.readdirSync(dir + appDir).forEach(function(appFile) {
            console.log("File " + appFile)
            var sauceAppDetails = sauceLabsInfo[appDir];
            if (sauceAppDetails != undefined) {
                console.log("Start uploading to Sauce Labs app " + appDir);
                if (appFile.includes("ios")) {
                    console.log("Uploading iOS");
                    uploadApplication(sdir + appDir + "/" + appFile)
                        .then(function(appID) {
                            console.log("App " + appDir + " for ios platform was uploaded. With Storage ID: " + appID);
                            result.push({"storageID": appID, "platform": "ios" });
                            var jsonStringBase64 = btoa(JSON.stringify(result));
                            setVariable("mSauceLabsAppsID", jsonStringBase64);
                        })
                        .catch(error => console.log("An error ocurred while uploading app " + appDir + " for ios platform with error: " + error))
                } else if (appFile.includes("android")) {
                    console.log("Uploading Android");
                     uploadApplication(dir + appDir + "/" + appFile)
                        .then(function(appID) { 
                            console.log("App " + appDir + " for android platform was uploaded. With Storage ID: " + appID)
                            result.push({"storageID": appID, "platform": "android" });
                            var jsonStringBase64 = btoa(JSON.stringify(result));
                            setVariable("mSauceLabsAppsID", jsonStringBase64);
                        })
                        .catch(error => console.log("An error ocurred while uploading app " + appDir + " for android platform with error: " + error))
                }
            }
        });
      }
    });
});

async function uploadApplication(applicationFilePath) {
    const sauselabs = new SauceLabs(process.env.SAUCELABS_USER_NAME, process.env.SAUCELABS_USER_KEY);
    const result = await saucelabs.uploadApplication(applicationFilePath);
    return result.item.id;
}

function setVariable(variableName, variableValue) {
    exec(`echo "##vso[task.setvariable variable=${variableName};isOutput=true]${variableValue}"`,
        (err, stdout, stderr) => {
            if (err) {
                //some err occurred
                console.info(err)
                throw new Error(err);
            } else {
                // the *entire* stdout and stderr (buffered)
                console.info(`${stdout}`);
                console.info(`Set Value ${variableName}=${variableValue}`)
            }
        }
    )
}