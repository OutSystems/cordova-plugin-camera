import Foundation
import AVFoundation

protocol CameraPickerInterfaceDelegate {
    func didFinish(_ error: Error?)
    func didCancel()
}

class CameraPickerInterface {
    
    enum CameraError: Error {
        case missingInitialization
        case noPermission(permission: AVAuthorizationStatus)
        case theSourceTypeIsUnavailable(sourceType: UIImagePickerController.SourceType)
    }
    
    var delegate: CameraPickerInterfaceDelegate?
    var viewController: UIViewController?
    private var options: PictureOptions?
    private var permissions: PermissionsController?
    private var flowController: CameraFlowController?
    
    func setOptions(options: PictureOptions) throws {
        guard isSourceTypeAvailable(sourceType: options.sourceType.pickerType) else {
            throw CameraError.theSourceTypeIsUnavailable(sourceType: options.sourceType.pickerType)
        }
        self.options = options
    }
    
    func takePicture(completion: @escaping (Result<MediaWrapper?, Error>) -> ()) {
        guard let options = options, let viewController = viewController else {
            completion(.failure(CameraError.missingInitialization))
            return
        }
        let permission = PermissionsController(viewController: viewController) { [weak self] in
            guard let self = self else { return }
            switch $0 {
            case .authorized:
                let flowController = CameraFlowController(options: options, completion: completion)
                flowController.takePicture(viewController: viewController)
                self.flowController = flowController
                break
            default:
                completion(.failure(CameraError.noPermission(permission: $0)))
            }
        }
        permission.validatePermissions(requestAccess: true)
        self.permissions = permission
    }
    
    func choosePicture(completion: @escaping (Result<MediaWrapper?, Error>) -> ()) {
        guard let options = options, let viewController = viewController else {
            completion(.failure(CameraError.missingInitialization))
            return
        }
        let permission = PermissionsController(viewController: viewController) { [weak self] in
            guard let self = self else { return }
            switch $0 {
            case .authorized:
                let flowController = CameraFlowController(options: options, completion: completion)
                flowController.choosePicture(viewController: viewController)
                self.flowController = flowController
                break
            default:
                completion(.failure(CameraError.noPermission(permission: $0)))
            }
        }
        permission.validatePermissions(requestAccess: true)
        self.permissions = permission
    }
}

private func isSourceTypeAvailable(sourceType: UIImagePickerController.SourceType) -> Bool {
    UIImagePickerController.isSourceTypeAvailable(sourceType)
}
