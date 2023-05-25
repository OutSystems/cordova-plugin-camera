import Foundation
import OSCameraLib

struct OSCAMRPlayVideoParameters {
    let url: URL
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
