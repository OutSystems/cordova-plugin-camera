
console.log("Running hook to install plugin node libs requirements");

module.exports = function (context) {
  var child_process = require('child_process'),
      deferral = require('q').defer();

  var output = child_process.exec('npm install', {cwd: __dirname}, function (error) {
    if (error !== null) {
      console.log('exec error: ' + error);
      throw new Error(`OUTSYSTEMS_PLUGIN_ERROR: Error when installing Camera Plugin - npm installation failed`)
    }
    else {
      deferral.resolve();
    }
  });

  return deferral.promise;
};
