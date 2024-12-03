import OSCameraLib

struct OSCAMRTakePictureParameters: Decodable {
    let quality: Int
    let targetWidth: Int?
    let targetHeight: Int?
    let encodingType: Int
    let sourceType: Int
    let allowEdit: Bool
    let correctOrientation: Bool
    let saveToPhotoAlbum: Bool
    let cameraDirection: Int 
    let includeMetadata: Bool
    let latestVersion: Bool
}

private enum OSCAMRTakePictureParametersError: Error {
    case invalid(field: String)
}

extension OSCAMRPictureOptions {
    convenience init(from parameters: OSCAMRTakePictureParameters) throws {
        func throwError(field: String) -> OSCAMRTakePictureParametersError {
            OSCAMRTakePictureParametersError.invalid(field: field)
        }

        if parameters.quality < 0 || parameters.quality > 100 { throw throwError(field: "quality") }
        guard let encodingType = OSCAMREncodingType(rawValue: parameters.encodingType) else { throw throwError(field: "encodingType") }
        guard let cameraDirection = OSCAMRDirection(rawValue: parameters.cameraDirection) else { throw throwError(field: "cameraDirection") }

        var targetSize: OSCAMRSize?
        if let targetWidth = parameters.targetWidth, let targetHeight = parameters.targetHeight {
            guard targetWidth > 0 else { throw throwError(field: "targetWidth") }
            guard targetHeight > 0 else { throw throwError(field: "targetHeight") }

            targetSize = try OSCAMRSize(width: targetWidth, height: targetHeight)
        }

        try self.init(
            quality: parameters.quality,
            size: targetSize,
            correctOrientation: parameters.correctOrientation, 
            encodingType: encodingType, 
            saveToPhotoAlbum: parameters.saveToPhotoAlbum, 
            direction: cameraDirection, 
            allowEdit: parameters.allowEdit, 
            returnMetadata: parameters.includeMetadata,
            latestVersion: parameters.latestVersion
        )
    }
}
