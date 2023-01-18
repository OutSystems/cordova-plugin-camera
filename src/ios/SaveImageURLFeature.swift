import UIKit

struct SaveImageURLFeature: ImageSaveFeature {
    
    enum SaveImageError: Error {
        case invalidImage
        case invalidURL
    }
    
    let url: URL
    
    func saveImage(_ saveImage: UIImage, completion: @escaping (Result<Void, Error>) -> ()) {
        guard let data = saveImage.pngData() else {
            completion(.failure(SaveImageError.invalidImage))
            return
        }
        do {
            try data.write(to: url)
            completion(.success(()))
        }
        catch {
            completion(.failure(error))
        }
    }
}

extension UIImage {
    func fixedOrientation() -> UIImage {
        if imageOrientation == .up { return self }

        var transform:CGAffineTransform = .identity
        switch imageOrientation {
        case .down, .downMirrored:
            transform = transform.translatedBy(x: size.width, y: size.height).rotated(by: .pi)
        case .left, .leftMirrored:
            transform = transform.translatedBy(x: size.width, y: 0).rotated(by: .pi/2)
        case .right, .rightMirrored:
            transform = transform.translatedBy(x: 0, y: size.height).rotated(by: -.pi/2)
        default: break
        }

        switch imageOrientation {
        case .upMirrored, .downMirrored:
            transform = transform.translatedBy(x: size.width, y: 0).scaledBy(x: -1, y: 1)
        case .leftMirrored, .rightMirrored:
            transform = transform.translatedBy(x: size.height, y: 0).scaledBy(x: -1, y: 1)
        default: break
        }

        let ctx = CGContext(
            data: nil,
            width: Int(size.width),
            height: Int(size.height),
            bitsPerComponent: cgImage!.bitsPerComponent,
            bytesPerRow: 0,
            space: cgImage!.colorSpace!,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        )!
        ctx.concatenate(transform)

        switch imageOrientation {
        case .left, .leftMirrored, .right, .rightMirrored:
            ctx.draw(cgImage!, in: CGRect(x: 0, y: 0, width: size.height,height: size.width))
        default:
            ctx.draw(cgImage!, in: CGRect(x: 0, y: 0, width: size.width,height: size.height))
        }
        return UIImage(cgImage: ctx.makeImage()!)
    }
}
