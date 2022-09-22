package org.apache.cordova.camera;

public enum CameraError {

    CAMERA_PERMISSION_DENIED_ERROR(3, "You need to provide access to your camera."),
    GALLERY_PERMISSION_DENIED_ERROR(6, "You need to provide access to your photo library."),
    NO_PICTURE_TAKEN_ERROR(8, "No picture taken."),
    NO_IMAGE_SELECTED_ERROR(5, "No image selected."),
    EDIT_IMAGE_ERROR (11, "There was an issue with editing the image."),
    GET_IMAGE_ERROR(13, "Could not take photo."),
    TAKE_PHOTO_ERROR(12, "Could not get image from photo library."),
    PROCESS_IMAGE_ERROR(14, "There was an issue processing the image.");

    final int code;
    final String message;

    CameraError(int code, String message){
        this.code = code;
        this.message = message;
    }

}
