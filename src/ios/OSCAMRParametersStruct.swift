import OSCameraLib

// MARK: - Choose from Gallery Parameters
struct OSCAMRChooseGalleryParameters {
    let mediaType: OSCAMRMediaType
    let allowMultipleSelection: Bool
    let includeMetadata: Bool
    let allowEdit: Bool
    
    init(mediaType: OSCAMRMediaType, allowMultipleSelection: Bool, includeMetadata: Bool, allowEdit: Bool) {
        self.mediaType = mediaType
        self.allowMultipleSelection = allowMultipleSelection
        self.includeMetadata = includeMetadata
        self.allowEdit = allowEdit
    }
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

struct OSCAMRRecordVideoParameters {
    let saveToGallery: Bool
    let includeMetadata: Bool
    
    init(saveToGallery: Bool, includeMetadata: Bool) {
        self.saveToGallery = saveToGallery
        self.includeMetadata = includeMetadata
    }
}

extension OSCAMRRecordVideoParameters: Decodable {
    enum CodingKeys: String, CodingKey {
        case saveToGallery
        case includeMetadata
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        
        let saveToGallery = try container.decode(Bool.self, forKey: .saveToGallery)
        let includeMetadata = try container.decode(Bool.self, forKey: .includeMetadata)
        
        self.init(saveToGallery: saveToGallery, includeMetadata: includeMetadata)
    }
}

extension OSCAMRVideoOptions {
    convenience init(from parameters: OSCAMRRecordVideoParameters) {
        self.init(saveToPhotoAlbum: parameters.saveToGallery, returnMetadata: parameters.includeMetadata)
    }
}

struct OSCAMRPictureParameters: Decodable {
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
    convenience init(from parameters: OSCAMRPictureParameters) {
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