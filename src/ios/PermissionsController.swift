import UIKit
import AVFoundation

class PermissionsController {
    
    private let viewController: UIViewController
    private let completion: (AVAuthorizationStatus) -> ()
    
    init(viewController: UIViewController, completion:@escaping (AVAuthorizationStatus) -> ()) {
        self.viewController = viewController
        self.completion = completion
    }
    
    func validatePermissions(requestAccess: Bool) {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            completion(.authorized)
        case .notDetermined:
            if requestAccess {
                AVCaptureDevice.requestAccess(for: .video) { [weak self] in
                    if $0 {
                        self?.completion(.authorized)
                    } else {
                        self?.completion(.denied)
                    }
                }
            } else {
                completion(.notDetermined)
            }
            break
        case .restricted:
            completion(.restricted)
            break
        case .denied:
            completion(.denied)
            break
        default:
            completion(.notDetermined)
        }
    }
    
    private func showAlertViewController(status: AVAuthorizationStatus) {
        DispatchQueue.main.async { [weak self] in
            let alertController = UIAlertController(title: Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String ?? "", message: NSLocalizedString("Access to the camera has been prohibited; please enable it in the Settings app to continue.", comment: ""), preferredStyle: .alert)
            
            let okAction = UIAlertAction(
                title: NSLocalizedString("OK", comment: ""),
                style: .default)
            { [weak self] _ in
                self?.completion(status)
            }
            
            let settingsAction = UIAlertAction(
                title: NSLocalizedString("Settings", comment: ""),
                style: .default)
            { _ in
                UIApplication.shared.open(URL(string: UIApplication.openSettingsURLString)!, options: [:], completionHandler: nil)
                self?.completion(status)
            }
            
            alertController.addAction(okAction)
            alertController.addAction(settingsAction)
            self?.viewController.present(alertController, animated: true, completion: nil)
        }
    }
}
