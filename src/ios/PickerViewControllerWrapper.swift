import UIKit
import MobileCoreServices

protocol PickerViewControllerWrapperDelegate {
    func didReturnInfo(_ info: [UIImagePickerController.InfoKey : Any])
    func didCancel()
}

class PickerViewControllerWrapper: NSObject {
    
    var controller: PickerViewControllerWrapperDelegate?
    private let options: PictureOptions
    private let viewController: UIViewController
    private lazy var pickerController = UIImagePickerController(rootViewController: viewController)
    
    init(options: PictureOptions, viewController: UIViewController) {
        self.viewController = viewController
        self.options = options
    }
    
    func showController() {
        let options = self.options
        DispatchQueue.main.async { [weak self] in
            guard let pickerController = self?.pickerController else { return }
            pickerController.sourceType = options.sourceType.pickerType
            
            switch options.sourceType {
            case .camera:
                pickerController.cameraDevice = options.cameraDirection.pickerType
            default:
                switch options.mediaType {
                case .all:
                    pickerController.mediaTypes = UIImagePickerController.availableMediaTypes(for: .photoLibrary) ?? []
                case .video:
                    pickerController.mediaTypes = [String(kUTTypeMovie)]
                case .picture:
                    pickerController.mediaTypes = [String(kUTTypeImage)]
                }
            }
            
            self?.viewController.present(pickerController, animated: true, completion: nil)
        }
    }
}

extension PickerViewControllerWrapper: UIImagePickerControllerDelegate {
    
    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
        controller?.didReturnInfo(info)
        picker.dismiss(animated: true, completion: nil)
    }
    
    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        controller?.didCancel()
        picker.dismiss(animated: true, completion: nil)
    }
}
