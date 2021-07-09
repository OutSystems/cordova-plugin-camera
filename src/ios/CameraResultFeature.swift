import Foundation
import UIKit

struct CameraResultFeature {
    
    func parseResult(options: PictureOptions, _ media: MediaWrapper) throws -> MediaWrapper {
        switch media {
        case let .picture(image):
            switch options.destination {
            case .nativeUri:
                // TODO: Save image on library and return an asset URL
                break
            case .fileUri:
                let pathExtension = options.encodingType == .jpeg ? "jpg" : "png"
                let filePath = temporaryFilePath(pathExtension: pathExtension)
                guard let data = image.parse(options: options) else { return media }
                try data.write(to: URL(fileURLWithPath: filePath), options: .atomicWrite)
                return .pictureURL(URL(fileURLWithPath: filePath))
            case .dataUrl:
                return image
                    .parse(options: options)
                    .map { MediaWrapper.pictureData($0) }
                    ?? media
            }
        default:
            return media
        }
        return media
    }
    
    private func temporaryFilePath(pathExtension: String) -> String {
        let docsPath = NSTemporaryDirectory()
        let filename = UUID().uuidString
        return "\(docsPath)/cdv_photo_\(filename).\(pathExtension)"
    }
}
