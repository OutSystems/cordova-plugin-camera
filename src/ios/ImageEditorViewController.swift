import UIKit

protocol ImageEditorController {
    func saveImage(_ image: UIImage, completionHandler: @escaping ((Result<Void, Error>) -> ()))
    func cropImage(_ image: UIImage, rect: CGRect) throws -> UIImage
    func rotateRightImage(_ image: UIImage) throws -> UIImage
    func rotateLeftImage(_ image: UIImage) throws -> UIImage
    func flipImage(_ image: UIImage) -> UIImage
}

final class ImageEditorViewController: UIViewController {
    
    private let controller: ImageEditorController
    private let imageView: ScaledImageView
    private let gesturesView = UIView()
    private let scaleGesture = UIPinchGestureRecognizer()
    private let rotatingGesture = UIRotationGestureRecognizer()
    private let rectPanGesture = UIPanGestureRecognizer()
    private let imagePanGesture = UIPanGestureRecognizer()
    private let originalImage: UIImage?
    private var cropRect: CGRect = .zero
    private var rotationDegree: Double = 0
    private var scalingFactor: Double = 0
    private var transform = CGAffineTransform.identity
    private var actualScale: CGFloat = 0
    private var actualTranslation: CGPoint = .zero
    private var portraitConstraints = [NSLayoutConstraint]()
    private var landscapeConstraints = [NSLayoutConstraint]()
    private var landscapeImageViewCenterXConstraint: NSLayoutConstraint?
    private lazy var cropView = CropView(gesture: rectPanGesture)
    
    private var doneButton: UIBarButtonItem {
        let bt = UIBarButtonItem(
            barButtonSystemItem: .done,
            target: self,
            action: #selector(saveImage(_:))
        )
        bt.tintColor = .white
        return bt
    }
    
    private var cancelButton: UIBarButtonItem {
        let bt = UIBarButtonItem(
            barButtonSystemItem: .cancel,
            target: self,
            action: #selector(cancelEdit(_:))
        )
        bt.tintColor = .white
        return bt
    }
    
    private lazy var rotateLeftButton: UIButton = {
        let rotateLeftButton = ActionButton(image: UIImage(named: "rotate"))
        rotateLeftButton.addTarget(self, action: #selector(rotateLeft(_:)), for: .touchUpInside)
        return rotateLeftButton
    }()
    
    private lazy var flipHorizontalyButton: UIButton = {
        let flipHorizontalyButton = ActionButton(image: UIImage(named: "flip"))
        flipHorizontalyButton.addTarget(self, action: #selector(flipHorizontaly(_:)), for: .touchUpInside)
        return flipHorizontalyButton
    }()
    
    private lazy var buttonsMainStack: UIStackView = {
        let buttonsMainStack = UIStackView(arrangedSubviews: [flipHorizontalyButton, rotateLeftButton])
        buttonsMainStack.translatesAutoresizingMaskIntoConstraints = false
        buttonsMainStack.alignment = .center
        buttonsMainStack.axis = .horizontal
        buttonsMainStack.spacing = 50
        buttonsMainStack.setContentHuggingPriority(.required, for: .horizontal)
        buttonsMainStack.setContentHuggingPriority(.required, for: .vertical)
        return buttonsMainStack
    }()
    
    init(image: UIImage?, controller: ImageEditorController) {
        self.originalImage = image
        self.controller = controller
        self.imageView = ScaledImageView(image: image)
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        view.addSubview(gesturesView)
        view.addSubview(buttonsMainStack)
        setupGesturesView()
        buttonsMainStack.setContentHuggingPriority(.required, for: .vertical)
        portraitConstraints += [
            view.layoutMarginsGuide.bottomAnchor.constraint(equalTo: buttonsMainStack.bottomAnchor, constant: 16),
            buttonsMainStack.centerXAnchor.constraint(equalTo: view.centerXAnchor)
        ]
        landscapeConstraints += [
            buttonsMainStack.rightAnchor.constraint(equalTo: view.layoutMarginsGuide.rightAnchor),
            buttonsMainStack.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ]
        setupPortraitUI()
    }
    
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        guard
            traitCollection.orientation == .landscape,
            let constraint = landscapeImageViewCenterXConstraint,
            constraint.constant != buttonsMainStack.bounds.width + gesturesView.layoutMargins.right
        else { return }
        // Adjust center of Image view
        constraint.constant = buttonsMainStack.bounds.width + gesturesView.layoutMargins.right + 8
    }
    
    private func setupToolbar() {
        self.navigationController?.isToolbarHidden = false
        self.navigationController?.toolbar.backgroundColor = .black
        self.navigationController?.isNavigationBarHidden = true
    }
    
    private func setupNavigationBar() {
        self.navigationController?.isNavigationBarHidden = false
        self.navigationController?.navigationBar.barTintColor = .clear
        self.navigationController?.navigationBar.isTranslucent = false
        self.navigationController?.isToolbarHidden = true
    }
    
    private func setupGesturesView() {
        gesturesView.translatesAutoresizingMaskIntoConstraints = false
        portraitConstraints += [
            gesturesView.topAnchor.constraint(equalTo: view.topAnchor),
            gesturesView.bottomAnchor.constraint(equalTo: buttonsMainStack.topAnchor),
            gesturesView.leftAnchor.constraint(equalTo: view.leftAnchor),
            gesturesView.rightAnchor.constraint(equalTo: view.rightAnchor)
        ]
        landscapeConstraints += [
            gesturesView.topAnchor.constraint(equalTo: view.topAnchor),
            view.bottomAnchor.constraint(equalTo: gesturesView.bottomAnchor, constant: 40),
            gesturesView.leftAnchor.constraint(equalTo: view.leftAnchor),
            gesturesView.rightAnchor.constraint(equalTo: buttonsMainStack.leftAnchor)
        ]
        
        gesturesView.addGestureRecognizer(scaleGesture)
        gesturesView.addGestureRecognizer(rectPanGesture)
        setupImageView()
        setupCropView()
    }
    
    private func setupImageView() {
        imageView.image = originalImage
        imageView.translatesAutoresizingMaskIntoConstraints = false
        imageView.setContentHuggingPriority(.required, for: .vertical)
        gesturesView.addSubview(imageView)
        portraitConstraints += [
            imageView.leftAnchor.constraint(equalTo: gesturesView.layoutMarginsGuide.leftAnchor, constant: 16),
            imageView.rightAnchor.constraint(equalTo: gesturesView.layoutMarginsGuide.rightAnchor, constant: -16),
            imageView.centerYAnchor.constraint(equalTo: gesturesView.centerYAnchor),
            imageView.centerXAnchor.constraint(equalTo: gesturesView.centerXAnchor),
            imageView.bottomAnchor.constraint(lessThanOrEqualTo: gesturesView.layoutMarginsGuide.bottomAnchor),
        ]
        landscapeConstraints += [
            imageView.topAnchor.constraint(equalTo: gesturesView.layoutMarginsGuide.topAnchor),
            imageView.bottomAnchor.constraint(equalTo: gesturesView.layoutMarginsGuide.bottomAnchor),
            imageView.centerYAnchor.constraint(equalTo: gesturesView.centerYAnchor),
            imageView.rightAnchor.constraint(lessThanOrEqualTo: gesturesView.layoutMarginsGuide.rightAnchor),
        ]
        
        landscapeImageViewCenterXConstraint = imageView.centerXAnchor.constraint(equalTo: gesturesView.centerXAnchor)
        landscapeImageViewCenterXConstraint.map { landscapeConstraints.append($0) }
        imageView.contentMode = .scaleAspectFit
        imageView.isUserInteractionEnabled = true
    }
    
    private func setupCropView() {
        cropView.translatesAutoresizingMaskIntoConstraints = false
        gesturesView.addSubview(cropView)
        NSLayoutConstraint.activate([
            cropView.leftAnchor.constraint(equalTo: imageView.leftAnchor),
            cropView.rightAnchor.constraint(equalTo: imageView.rightAnchor),
            cropView.bottomAnchor.constraint(equalTo: imageView.bottomAnchor),
            cropView.topAnchor.constraint(equalTo: imageView.topAnchor)
        ])
    }
    
    private func setupPortraitUI() {
        setupToolbar()
        self.navigationItem.leftBarButtonItem = nil
        self.navigationItem.rightBarButtonItem = nil
        self.toolbarItems = [
            cancelButton,
            UIBarButtonItem(barButtonSystemItem: .flexibleSpace, target: nil, action: nil),
            doneButton
        ]
        buttonsMainStack.axis = .horizontal
        NSLayoutConstraint.deactivate(landscapeConstraints)
        NSLayoutConstraint.activate(portraitConstraints)
    }
    
    private func setupLandscapeUI() {
        setupNavigationBar()
        self.navigationItem.hidesBackButton = true
        self.navigationItem.leftBarButtonItem = cancelButton
        self.navigationItem.rightBarButtonItem = doneButton
        buttonsMainStack.axis = .vertical
        NSLayoutConstraint.deactivate(portraitConstraints)
        NSLayoutConstraint.activate(landscapeConstraints)
    }
    
    override func willTransition(to newCollection: UITraitCollection, with coordinator: UIViewControllerTransitionCoordinator) {
        super.willTransition(to: newCollection, with: coordinator)
        coordinator.animate { [unowned self] _ in
            switch (UIDevice.current.userInterfaceIdiom, newCollection.horizontalSizeClass, newCollection.verticalSizeClass) {
            case (.phone, .compact, .regular), (.pad, .regular, .regular): // Portrait
                setupPortraitUI()
                break
            default: // Landscape
                setupLandscapeUI()
                break
            }
        } completion: { _ in
            
        }
    }
    
    @objc func cancelEdit(_ sender: Any?) {
        self.dismiss(animated: true, completion: nil)
    }
    
    @objc func flipHorizontaly(_ sender: Any?) {
        imageView.image = controller.flipImage(imageView.image!)
    }
    
    @objc func saveImage(_ sender: Any?) {
        let croppedImage: UIImage?
        guard let cgImage = imageView.image?.cgImage else { return }
        let scaledWidth =  CGFloat(cgImage.width) / imageView.bounds.width
        let scaledHeight = CGFloat(cgImage.height) / imageView.bounds.height
        let scaledRect = cropView.cropRect.applying(CGAffineTransform(scaleX: scaledWidth, y: scaledHeight))
        croppedImage = try? controller.cropImage(imageView.image!, rect: scaledRect)
        
        controller.saveImage(croppedImage!) { [weak self] _ in
            DispatchQueue.main.async {
                self?.dismiss(animated: true, completion: nil)
            }
        }
    }
    
    @objc func rotateRight(_ sender: Any?) {
        imageView.image = try? controller.rotateRightImage(imageView.image!)
    }
    
    @objc func rotateLeft(_ sender: Any?) {
        let newImg = try? controller.rotateLeftImage(imageView.image!)
        imageView.image = newImg
    }
}

// MARK: Actions

private class ActionButton: UIButton {
    
    init(image: UIImage?) {
        super.init(frame: .zero)
        translatesAutoresizingMaskIntoConstraints = false
        setImage(image, for: .normal)
        tintColor = .white
        contentHorizontalAlignment = .fill
        contentVerticalAlignment = .fill
        imageView?.contentMode = .scaleAspectFit
        NSLayoutConstraint.activate([
            widthAnchor.constraint(equalToConstant: 32),
            heightAnchor.constraint(equalToConstant: 32)
        ])
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}