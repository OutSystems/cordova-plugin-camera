import UIKit

enum MediaWrapper {
    case video(URL)
    case picture(UIImage)
    case pictureData(Data)
    case pictureURL(URL)
}
