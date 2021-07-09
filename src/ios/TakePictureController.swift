import Foundation
import MobileCoreServices

class TakePictureController {
    
    enum TakeImageError: Error {
        case somethingWentWrong
    }
    
    private let options: PictureOptions
    private let completion: (Result<MediaWrapper?, Error>) -> ()
    
    init(options: PictureOptions, completion: @escaping (Result<MediaWrapper?, Error>) -> ()) {
        self.options = options
        self.completion = completion
    }
}

extension TakePictureController: PickerViewControllerWrapperDelegate {
    
    func didReturnInfo(_ info: [UIImagePickerController.InfoKey : Any]) {
        if let mediaType = info[UIImagePickerController.InfoKey.mediaType] as? String, mediaType ==  String(kUTTypeImage) {
            guard let image = info[UIImagePickerController.InfoKey.originalImage] as? UIImage else {
                completion(.failure(TakeImageError.somethingWentWrong))
                return
            }
            completion(.success(.picture(image)))
        } else {
            guard let url = info[UIImagePickerController.InfoKey.mediaURL] as? URL else {
                completion(.failure(TakeImageError.somethingWentWrong))
                return
            }
            do {
                completion(.success(.video(try createTmpFile(path: url))))
            } catch {
                completion(.failure(error))
            }
        }
    }
    
    func didCancel() {
        completion(.success(nil))
    }
    
    private func createTmpFile(path: URL) throws -> URL {
        let pathExt = path.pathExtension
        let destPath = URL(fileURLWithPath: temporaryFilePath(pathExtension: pathExt))
        let fileManager = FileManager.default
        try fileManager.copyItem(at: path, to: destPath)
        return destPath
        
    }
    
    private func temporaryFilePath(pathExtension: String) -> String {
        let docsPath = NSTemporaryDirectory()
        let filename = UUID().uuidString
        return "\(docsPath)/cdv_photo_\(filename).\(pathExtension)"
    }
}
