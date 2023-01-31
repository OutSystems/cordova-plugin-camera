import OSCameraLib

extension OSCAMRPictureOptions {
    private struct TakePictureArgumentIndex {
        static let quality: UInt = 0
        static let encodingType: UInt = 5
        static let allowEdit: UInt = 6
        static let correctOrientation: UInt = 7
        static let cameraDirection: UInt = 9
    }
    
    init(command: CDVInvokedUrlCommand) {
        let quality = command.argument(at: TakePictureArgumentIndex.quality) as? Int ?? 60
        let correctOrientation = command.argument(at: TakePictureArgumentIndex.correctOrientation) as? Bool ?? true
        let encodingType: OSCAMREncodingType
        if let encodingTypeArgument = command.argument(at: TakePictureArgumentIndex.encodingType) as? Int {
            encodingType = OSCAMREncodingType(rawValue: encodingTypeArgument) ?? .jpeg
        } else {
            encodingType = .jpeg
        }
        let direction: OSCAMRDirection
        if let directionArgument = command.argument(at: TakePictureArgumentIndex.cameraDirection) as? Int {
            direction = OSCAMRDirection(rawValue: directionArgument) ?? .back
        } else {
            direction = .back
        }
        let allowEdit = command.argument(at: TakePictureArgumentIndex.allowEdit) as? Bool ?? false
        
        self.init(quality: quality, correctOrientation: correctOrientation, encodingType: encodingType, saveToPhotoAlbum: false, direction: direction, allowEdit: allowEdit)
    }
}
