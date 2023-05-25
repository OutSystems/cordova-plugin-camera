import OSCameraLib

struct OSCAMRChooseGalleryParameters {
    let mediaType: OSCAMRMediaType
    let allowMultipleSelection: Bool
    let includeMetadata: Bool
    let allowEdit: Bool
}

extension OSCAMRChooseGalleryParameters: Decodable {
    enum CodingKeys: String, CodingKey {
        case mediaType
        case allowMultipleSelection
        case includeMetadata
        case allowEdit
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let mediaTypeValue = try container.decode(Int.self, forKey: .mediaType)
        let allowMultipleSelection = try container.decode(Bool.self, forKey: .allowMultipleSelection)
        let includeMetadata = try container.decode(Bool.self, forKey: .includeMetadata)
        let allowEdit = try container.decode(Bool.self, forKey: .allowEdit)
                
        let mediaType = try OSCAMRMediaType(from: mediaTypeValue)
        self.init(mediaType: mediaType, allowMultipleSelection: allowMultipleSelection, includeMetadata: includeMetadata, allowEdit: allowEdit)
    }
}
