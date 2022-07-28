@objc enum CDVCameraErrorEnum: Int {
    case cameraAccess = 3
    case imageSelected = 5
    case photoLibraryAccess = 6
    case assetAccess = 7
    case pictureTaken = 8
    case cameraAvailable = 9
    case invalidImageData = 10
    case editingImage = 11
    
    case unknown = 0
    
    var code: String {
        "OS-PLUG-CAMR-\(String(format: "%04d", self.rawValue))"
    }
    
    var message: String {
        switch self {
        case .cameraAccess:
            return "You need to provide access to your camera."
        case .imageSelected:
            return "No Image Selected."
        case .photoLibraryAccess:
            return "You need to provide access to your photo library."
        case .assetAccess:
            return "You need to provide access to your assets."
        case .pictureTaken:
            return "No Picture Taken."
        case .cameraAvailable:
            return "No camera is available."
        case .invalidImageData:
            return "The image contains invalid data."
        case .editingImage:
            return "There was an issue with editing the image."
            
        case .unknown:
            return "Unknown error."
        }
    }
}

@objc class CDVCameraError: NSObject {
    @objc static func dictionary(for error: CDVCameraErrorEnum) -> [String: String] {
        return ["code": error.code, "message": error.message]
    }
}
