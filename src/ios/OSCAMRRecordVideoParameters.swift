import OSCameraLib

struct OSCAMRRecordVideoParameters: Decodable {
    let saveToGallery: Bool
    let includeMetadata: Bool
    let isPersistent: Bool
}

extension OSCAMRVideoOptions {
    convenience init(from parameters: OSCAMRRecordVideoParameters) {
        self.init(saveToPhotoAlbum: parameters.saveToGallery, returnMetadata: parameters.includeMetadata, isPersistent: parameters.isPersistent)
    }
}
