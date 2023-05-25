import OSCameraLib

struct OSCAMRTakePictureParameters: Decodable {
    let quality: Int
    let targetWidth: Int
    let targetHeight: Int
    let encodingType: Int
    let sourceType: Int
    let allowEdit: Bool
    let correctOrientation: Bool
    let saveToPhotoAlbum: Bool
    let cameraDirection: Int 
    let includeMetadata: Bool?
    let latestVersion: Bool?
}

extension OSCAMRPictureOptions {
    convenience init(from parameters: OSCAMRTakePictureParameters) {
        let targetSize = OSCAMRSize(width: parameters.targetWidth, height: parameters.targetHeight)
        let encodingType = OSCAMREncodingType(rawValue: parameters.encodingType) ?? .jpeg
        let direction = OSCAMRDirection(rawValue: parameters.cameraDirection) ?? .back

        self.init(
            quality: parameters.quality, 
            size: targetSize, 
            correctOrientation: parameters.correctOrientation, 
            encodingType: encodingType, 
            saveToPhotoAlbum: parameters.saveToPhotoAlbum, 
            direction: direction, 
            allowEdit: parameters.allowEdit, 
            returnMetadata: parameters.includeMetadata ?? false,
            latestVersion: parameters.latestVersion ?? false
        )
    }
}