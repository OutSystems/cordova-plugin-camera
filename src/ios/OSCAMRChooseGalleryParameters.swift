import OSCameraLib

struct OSCAMRChooseGalleryParameters {
    let mediaType: OSCAMRMediaType
    let allowMultipleSelection: Bool
    
    init(mediaType: OSCAMRMediaType, allowMultipleSelection: Bool) {
        self.mediaType = mediaType
        self.allowMultipleSelection = allowMultipleSelection
    }
}

extension OSCAMRChooseGalleryParameters: Decodable {
    enum CodingKeys: String, CodingKey {
        case mediaType
        case allowMultipleSelection
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let mediaTypeValue = try container.decode(Int.self, forKey: .mediaType)
        let allowMultipleSelection = try container.decode(Bool.self, forKey: .allowMultipleSelection)
                
        let mediaType = try OSCAMRMediaType(from: mediaTypeValue)
        self.init(mediaType: mediaType, allowMultipleSelection: allowMultipleSelection)
    }
}