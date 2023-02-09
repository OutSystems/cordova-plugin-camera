import OSCameraLib

extension OSCAMRPictureOptions {
    private struct TakePictureArgumentIndex {
        static let quality: UInt = 0
        static let targetWidth: UInt = 1
        static let targetHeight: UInt = 2
        static let encodingType: UInt = 3
        static let allowEdit: UInt = 4
        static let correctOrientation: UInt = 5
        static let saveToPhotoAlbum: UInt = 6
        static let cameraDirection: UInt = 7
    }
    
    init(command: CDVInvokedUrlCommand) {
        let quality = command.argument(at: TakePictureArgumentIndex.quality) as? Int ?? 60
        
        var targetSize: OSCAMRSize?
        if let targetWidth = command.argument(at: TakePictureArgumentIndex.targetWidth) as? Int, let targetHeight = command.argument(at: TakePictureArgumentIndex.targetHeight) as? Int {
            targetSize = .init(width: targetWidth, height: targetHeight)
        }
        
        let encodingType: OSCAMREncodingType
        if let encodingTypeArgument = command.argument(at: TakePictureArgumentIndex.encodingType) as? Int {
            encodingType = OSCAMREncodingType(rawValue: encodingTypeArgument) ?? .jpeg
        } else {
            encodingType = .jpeg
        }
        
        let allowEdit = command.argument(at: TakePictureArgumentIndex.allowEdit) as? Bool ?? false
        
        let correctOrientation = command.argument(at: TakePictureArgumentIndex.correctOrientation) as? Bool ?? true
        
        let saveToPhotoAlbum = command.argument(at: TakePictureArgumentIndex.saveToPhotoAlbum) as? Bool ?? false
        
        let direction: OSCAMRDirection
        if let directionArgument = command.argument(at: TakePictureArgumentIndex.cameraDirection) as? Int {
            direction = OSCAMRDirection(rawValue: directionArgument) ?? .back
        } else {
            direction = .back
        }
        
        self.init(quality: quality, size: targetSize, correctOrientation: correctOrientation, encodingType: encodingType, saveToPhotoAlbum: saveToPhotoAlbum, direction: direction, allowEdit: allowEdit)
    }
}
