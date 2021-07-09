import Foundation

class CameraFlowController {
    
    private let resultFeature = CameraResultFeature()
    private let options: PictureOptions
    private let completion: (Result<MediaWrapper?, Error>) -> ()
    
    init(options: PictureOptions, completion:@escaping (Result<MediaWrapper?, Error>) -> ()) {
        self.options = options
        self.completion = completion
    }
    
    func takePicture(viewController: UIViewController) {
        let controller = TakePictureController(options: self.options) { [weak self] in
            guard let self = self else { return }
            switch $0 {
            case let .success(media):
                switch media {
                case let .picture(image) where self.options.allowEdit:
                    self.editImage(image: image, viewController: viewController)
                default:
                    if media != nil {
                        do {
                            let result = try self.resultFeature.parseResult(options: self.options, media!)
                            self.completion(.success(result))
                        } catch {
                            self.completion(.failure(error))
                        }
                    } else {
                        self.completion($0)
                    }
                }
            default:
                self.completion($0)
            }
        }
        let picker = PickerViewControllerWrapper(options: self.options, viewController: viewController)
        picker.controller = controller
        picker.showController()
    }
    
    func choosePicture(viewController: UIViewController) {
        let controller = ChoosePictureController(options: self.options) { [weak self] in
            guard let self = self else { return }
            switch $0 {
            case let .success(media):
                switch media {
                case let .picture(image) where self.options.allowEdit:
                    self.editImage(image: image, viewController: viewController)
                default:
                    if media != nil {
                        do {
                            let result = try self.resultFeature.parseResult(options: self.options, media!)
                            self.completion(.success(result))
                        } catch {
                            self.completion(.failure(error))
                        }
                    } else {
                        self.completion($0)
                    }
                }
            default:
                self.completion($0)
            }
        }
        let picker = PickerViewControllerWrapper(options: self.options, viewController: viewController)
        picker.controller = controller
        picker.showController()
    }
    
    private func editImage(image: UIImage, viewController: UIViewController) {
        let controller = ImageEditorControllerImpl(
            saveImageFeature: PassthroughImageSaverImpl(),
            delegate: self
        )

        let vc = ImageEditorViewController(image: image.fixedOrientation(), controller: controller)
        viewController.present(vc, animated: true, completion: nil)
    }
}

private struct PassthroughImageSaverImpl: ImageSaveFeature {
    func saveImage(_ image: UIImage, completion: @escaping (Result<Void, Error>) -> ()) {
        completion(.success(()))
    }
}

extension CameraFlowController: ImageEditorDelegate {
    
    func finishEditing(_ result: Result<UIImage, Error>) {
        switch result {
        case let .success(image):
            do {
                let resultingProduct = try resultFeature.parseResult(options: options, .picture(image))
                completion(.success(resultingProduct))
            } catch {
                completion(.failure(error))
            }
        case let .failure(error):
            completion(.failure(error))
        }
    }
    
    func didCancelEdit() {
        completion(.success(nil))
    }
}
