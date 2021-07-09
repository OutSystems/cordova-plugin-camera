import UIKit

// Remove the S it was just so there was no conflicts withe the legacy class
// Class name should be CDVCamera
@objc(CDVSCamera)
class CDVSCamera: CDVPlugin {
    
    struct SomethingWentWrong: Error {}
    
    static let shared = CDVSCamera()
    private var plugin: CameraPickerInterface?
    private var callbackId: String?
    
    static func getInstance() -> CDVSCamera {
        return shared
    }
    
    override func pluginInitialize() {
        self.plugin = CameraPickerInterface()
    }
    
    @objc(takePicture:)
    func takePicture(command: CDVInvokedUrlCommand) {
        self.callbackId = command.callbackId
        let options = PictureOptions(command: command)
        guard let plugin = plugin else {
            returnError(SomethingWentWrong())
            return
        }
        do {
            try plugin.setOptions(options: options)
            plugin.viewController = self.viewController
            switch options.sourceType {
            case .photoLibrary:
                plugin.choosePicture { [weak self] in
                    switch $0 {
                    case let .success(media):
                        guard let media = media else {
                            self?.returnNoResult()
                            return
                        }
                        self?.returnSuccess(media)
                    case let .failure(error):
                        self?.returnError(error)
                    }
                }
            case .camera:
                plugin.takePicture { [weak self] in
                    switch $0 {
                    case let .success(media):
                        guard let media = media else {
                            self?.returnNoResult()
                            return
                        }
                        self?.returnSuccess(media)
                    case let .failure(error):
                        self?.returnError(error)
                    }
                }
            }
           
        } catch {
            returnError(error)
        }
    }
    
    private func returnError(_ error: Error) {
        let result = CDVPluginResult(status: .error, messageAs: error.localizedDescription)
        commandDelegate.send(result, callbackId: callbackId)
    }
    
    private func returnNoResult() {
        let result = CDVPluginResult(status: .noResult)
        commandDelegate.send(result, callbackId: callbackId)
    }
    
    private func returnSuccess(_ media: MediaWrapper) {
        let result: CDVPluginResult
        switch media {
        case let .video(url):
            result = CDVPluginResult(status: .ok, messageAs: url.absoluteString)
        case let .pictureURL(url):
            result = CDVPluginResult(status: .ok, messageAs: url.absoluteString)
        case let .pictureData(data):
            result = CDVPluginResult(status: .ok, messageAs: data.base64EncodedString())
        case let .picture(image):
            result = CDVPluginResult(status: .ok, messageAs: image.pngData()!.base64EncodedString())
        }
        commandDelegate.send(result, callbackId: callbackId)
    }
    
}

private extension PictureOptions {
    
    init(command: CDVInvokedUrlCommand) {
        self.quality = (command.argument(at: 0) as? NSNumber)?.floatValue ?? 50
        self.destination = (command.argument(at: 1) as? NSNumber).flatMap { DestinationType(rawValue: $0.intValue) } ?? .fileUri
        self.sourceType = (command.argument(at: 2) as? NSNumber).flatMap { SourceType(rawValue: $0.intValue) } ?? .camera
        
        if let width = command.argument(at: 3) as? Double, let height = command.argument(at: 4) as? Double {
            self.targetSize = CGSize(width: width, height: height)
        } else {
            self.targetSize = .zero
        }
        
        self.encodingType = (command.argument(at: 5) as? NSNumber).flatMap { EncodingType(rawValue: $0.intValue) } ?? .jpeg
        self.mediaType = (command.argument(at: 6) as? NSNumber).flatMap { MediaType(rawValue: $0.intValue) } ?? .picture
        self.allowEdit = command.argument(at: 7) as? Bool ?? false
        self.correctOrientation = command.argument(at: 8) as? Bool ?? false
        self.saveToPhotoAlbum = command.argument(at: 9) as? Bool ?? false
        self.cameraDirection = (command.argument(at: 11) as? NSNumber).flatMap { CameraDirection(rawValue: $0.intValue) } ?? .back
    }
}
