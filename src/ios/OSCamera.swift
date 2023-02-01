import OSCameraLib
import OSCommonPluginLib

@objc(OSCamera)
class OSCamera: CDVPlugin {
    var plugin: OSCAMRActionDelegate?
    var callbackId: String = ""
    
    override func pluginInitialize() {
        self.plugin = OSCAMRFactory.createCameraWrapper(withDelegate: self)
    }
    
    @objc(takePicture:)
    func takePicture(command: CDVInvokedUrlCommand) {
        self.callbackId = command.callbackId
        let options = OSCAMRPictureOptions(command: command)
        
        self.commandDelegate.run { [weak self] in
            guard let self = self else {
                self?.callback(error: .takePictureIssue)
                return
            }
            self.plugin?.takePicture(from: self.viewController, with: options)
        }
    }
}

extension OSCamera: PlatformProtocol {
    func sendResult(result: String? = nil, error: NSError? = nil, callBackID: String) {
        var pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR)

        if let error = error {
            let errorDict = [
                "code": "OS-PLUG-CAMR-\(String(format: "%04d", error.code))",
                "message": error.localizedDescription
            ]
            pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: errorDict);
        } else if let result = result {
            pluginResult = result.isEmpty ? CDVPluginResult(status: CDVCommandStatus_OK) : CDVPluginResult(status: CDVCommandStatus_OK, messageAs: result)
        }

        self.commandDelegate.send(pluginResult, callbackId: callBackID);
    }
}

extension OSCamera: OSCAMRCallbackDelegate {
    func callback(result: String?, error: OSCAMRError?) {
        if let error = error as? NSError {
            self.sendResult(error: error, callBackID: self.callbackId)
        } else if let result = result {
            self.sendResult(result: result, callBackID: self.callbackId)
        }
    }
    
    func callback(error: OSCAMRError) {
        self.callback(result: nil, error: error)
    }
    
    func callback(_ result: String) {
        self.callback(result: result, error: nil)
    }
}
