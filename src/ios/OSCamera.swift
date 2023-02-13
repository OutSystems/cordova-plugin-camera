import OSCameraLib
import OSCommonPluginLib

@objc(OSCamera)
class OSCamera: CDVPlugin {
    var plugin: OSCAMRActionDelegate?
    var callbackId: String = ""
    
    override func pluginInitialize() {
        self.plugin = OSCAMRFactory.createCameraWrapper(withDelegate: self, and: self.viewController)
    }
    
    override func onAppTerminate() {
        self.commandDelegate.run { [weak self] in
            guard let self = self else { return }
            self.plugin?.cleanTemporaryFiles()
        }
    }
    
    @objc(takePicture:)
    func takePicture(command: CDVInvokedUrlCommand) {
        self.callbackId = command.callbackId
        let options = OSCAMRPictureOptions(command: command)
        
        self.commandDelegate.run { [weak self] in
            guard let self = self else { return }
            self.plugin?.captureMedia(with: options)
        }
    }

    @objc(editPicture:)
    func editPicture(command: CDVInvokedUrlCommand) {
        self.callbackId = command.callbackId
        guard let imageBase64 = command.argument(at: 0) as? String, let imageData = Data(base64Encoded: imageBase64), let image = UIImage(data: imageData)
        else {
            self.callback(error: .invalidImageData)
            return
        }
        
        self.commandDelegate.run { [weak self] in
            guard let self = self else { return }
            self.plugin?.editPicture(image)
        }
    }
    
    @objc(captureVideo:)
    func captureVideo(command: CDVInvokedUrlCommand) {
        self.callbackId = command.callbackId
        let options = OSCAMRVideoOptions()
        
        self.commandDelegate.run { [weak self] in
            guard let self = self else { return }
            self.plugin?.captureMedia(with: options)
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
