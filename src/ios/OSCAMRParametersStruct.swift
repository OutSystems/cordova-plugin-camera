import OSCameraLib

// MARK: - Choose from Gallery Parameters
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

// MARK: - Play Video Parameters
struct OSCAMRPlayVideoParameters {
    let url: URL
    
    init(url: URL) {
        self.url = url
    }
}

extension OSCAMRPlayVideoParameters: Decodable {
    enum DecodeError: Error {
        case invalidURL
    }
    
    enum CodingKeys: String, CodingKey {
        case url = "videoURI"
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let urlString = try container.decode(String.self, forKey: .url)
        
        guard let url = URL(string: urlString) else { throw DecodeError.invalidURL }
        self.init(url: url)
    }
}
