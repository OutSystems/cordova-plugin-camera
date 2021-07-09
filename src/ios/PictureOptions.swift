import Foundation
import AVFoundation

struct PictureOptions {
    
    enum DestinationType: Int {
        case dataUrl
        case fileUri
        case nativeUri
    }
    
    enum EncodingType: Int {
        case jpeg
        case png
    }
    
    enum MediaType: Int {
        case picture
        case video
        case all
    }
    
    enum SourceType: Int {
        case camera
        case photoLibrary
        
        var pickerType: UIImagePickerController.SourceType {
            switch self {
            case .camera:
                return .camera
            default:
                return .photoLibrary
            }
        }
    }
    
    enum CameraDirection: Int {
        case front
        case back
        
        var pickerType: UIImagePickerController.CameraDevice {
            switch self {
            case .front:
                return .front
            default:
                return .rear
            }
        }
    }
    
    let quality: Float
    let destination: DestinationType
    let sourceType: SourceType
    let targetSize: CGSize
    let encodingType: EncodingType
    let mediaType: MediaType
    let allowEdit: Bool
    let correctOrientation: Bool
    let saveToPhotoAlbum: Bool
    let cameraDirection: CameraDirection
}
