import UIKit

extension UIImage {
    
    func parse(options: PictureOptions) -> Data? {
        switch options.encodingType {
        case .jpeg:
            return jpegData(compressionQuality: CGFloat(options.quality) / 100)
        case .png:
            return pngData()
        }
    }
}
