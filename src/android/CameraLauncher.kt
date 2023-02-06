/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package org.apache.cordova.camera

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.CallbackContext
import com.outsystems.plugins.camera.model.OSCAMRParameters
import org.apache.cordova.BuildHelper
import org.apache.cordova.PermissionHelper
import org.apache.cordova.PluginResult
import org.apache.cordova.LOG
import com.outsystems.imageeditor.view.ImageEditorActivity
import com.outsystems.plugins.camera.controller.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.RuntimeException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.Exception

/**
 * This class launches the camera view, allows the user to take a picture, closes the camera view,
 * and returns the captured image.  When the camera view is closed, the screen displayed before
 * the camera view was shown is redisplayed.
 */
class CameraLauncher : CordovaPlugin() {
    private var mQuality // Compression quality hint (0-100: 0=low quality & high compression, 100=compress of max quality)
            = 0
    private var targetWidth // desired width of the image
            = 0
    private var targetHeight // desired height of the image
            = 0
    private var imageUri // Uri of captured image
            : Uri? = null
    private var imageFilePath // File where the image is stored
            : String? = null
    private var encodingType // Type of encoding to use
            = 0
    private var mediaType // What type of media to retrieve
            = 0
    private var destType // Source type (needs to be saved for the permission handling)
            = 0
    private var srcType // Destination type (needs to be saved for permission handling)
            = 0
    private var saveToPhotoAlbum // Should the picture be saved to the device's photo album
            = false
    private var correctOrientation // Should the pictures orientation be corrected
            = false
    private var orientationCorrected // Has the picture's orientation been corrected
            = false
    private var allowEdit // Should we allow the user to crop the image.
            = false
    var callbackContext: CallbackContext? = null
    private var numPics = 0
    private var conn // Used to update gallery app with newly-written files
            : MediaScannerConnection? = null
    private var scanMe // Uri of image to be added to content store
            : Uri? = null
    private var croppedUri: Uri? = null
    private var croppedFilePath: String? = null
    private var exifData // Exif data from source
            : ExifHelper? = null
    private lateinit var applicationId: String
    private var pendingDeleteMediaUri: Uri? = null
    private var camController: OSCAMRController? = null
    private var camParameters: OSCAMRParameters? = null

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArray of arguments for the plugin.
     * @param callbackContext   The callback id used when calling back into JavaScript.
     * @return                  A PluginResult object with a status and message.
     */
    @Throws(JSONException::class)
    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        this.callbackContext = callbackContext
        //Adding an API to CoreAndroid to get the BuildConfigValue
        //This allows us to not make this a breaking change to embedding
        applicationId =
            BuildHelper.getBuildConfigValue(cordova.activity, "APPLICATION_ID") as String
        applicationId = preferences.getString("applicationId", applicationId)
        camController = OSCAMRController(applicationId, OSCAMRExifHelper(), OSCAMRFileHelper(), OSCAMRMediaHelper(), OSCAMRImageHelper())
        /**
         * Fix for the OutSystems NativeShell
         * The com.outsystems.myapp.BuildConfig class from BuildHelper.getBuildConfigValue is only created when using the cordova to build our app,
         * since we do not use cordova to build our app, we must add this condition to ensure that the applicationId is not null.
         * TODO: Remove this condition when we start to use cordova build command to build our applications.
         */
        if (applicationId == null) applicationId = cordova.activity.packageName
        if (action == "takePicture") {
            srcType = CAMERA
            destType = FILE_URI
            saveToPhotoAlbum = false
            targetHeight = 0
            targetWidth = 0
            encodingType = JPEG
            mediaType = PICTURE
            mQuality = 50

            //Take the values from the arguments if they're not already defined (this is tricky)
            destType = args.getInt(1)
            srcType = args.getInt(2)
            mQuality = args.getInt(0)
            targetWidth = args.getInt(3)
            targetHeight = args.getInt(4)
            encodingType = args.getInt(5)
            mediaType = args.getInt(6)
            allowEdit = args.getBoolean(7)
            correctOrientation = args.getBoolean(8)
            saveToPhotoAlbum = args.getBoolean(9)

            // If the user specifies a 0 or smaller width/height
            // make it -1 so later comparisons succeed
            if (targetWidth < 1) {
                targetWidth = -1
            }
            if (targetHeight < 1) {
                targetHeight = -1
            }

            // We don't return full-quality PNG files. The camera outputs a JPEG
            // so requesting it as a PNG provides no actual benefit
            if (targetHeight == -1 && targetWidth == -1 && mQuality == 100 &&
                !correctOrientation && encodingType == PNG && srcType == CAMERA
            ) {
                encodingType = JPEG
            }

            //create CameraParameters
            camParameters = OSCAMRParameters(
                mQuality,
                targetWidth,
                targetHeight,
                encodingType,
                mediaType,
                allowEdit,
                correctOrientation,
                saveToPhotoAlbum
            )

            try {
                if (srcType == CAMERA) {
                    callTakePicture(destType, encodingType)
                } else if (srcType == PHOTOLIBRARY || srcType == SAVEDPHOTOALBUM) {
                    callGetImage(srcType, destType, encodingType)
                }
            } catch (e: IllegalArgumentException) {
                callbackContext.error("Illegal Argument Exception")
                val r = PluginResult(PluginResult.Status.ERROR)
                callbackContext.sendPluginResult(r)
                return true
            }
            val r = PluginResult(PluginResult.Status.NO_RESULT)
            r.keepCallback = true
            callbackContext.sendPluginResult(r)
            return true
        }
        return false
    }// Create the cache directory if it doesn't exist

    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------
    private val tempDirectoryPath: String
        private get() {
            val cache = cordova.activity.cacheDir
            // Create the cache directory if it doesn't exist
            cache.mkdirs()
            return cache.absolutePath
        }

    /**
     * Take a picture with the camera.
     * When an image is captured or the camera view is cancelled, the result is returned
     * in CordovaActivity.onActivityResult, which forwards the result to this.onActivityResult.
     *
     * The image can either be returned as a base64 string or a URI that points to the file.
     * To display base64 string in an img tag, set the source to:
     * img.src="data:image/jpeg;base64,"+result;
     * or to display URI in an img tag
     * img.src=result;
     *
     * @param returnType        Set the type of image to return.
     * @param encodingType           Compression quality hint (0-100: 0=low quality & high compression, 100=compress of max quality)
     */
    fun callTakePicture(returnType: Int, encodingType: Int) {
        val saveAlbumPermission = Build.VERSION.SDK_INT < 33 &&
                PermissionHelper.hasPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                PermissionHelper.hasPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                Build.VERSION.SDK_INT >= 33 &&
                PermissionHelper.hasPermission(this, READ_MEDIA_VIDEO) &&
                PermissionHelper.hasPermission(this, READ_MEDIA_IMAGES)
        var takePicturePermission = PermissionHelper.hasPermission(this, Manifest.permission.CAMERA)

        // CB-10120: The CAMERA permission does not need to be requested unless it is declared
        // in AndroidManifest.xml. This plugin does not declare it, but others may and so we must
        // check the package info to determine if the permission is present.
        if (!takePicturePermission) {
            takePicturePermission = true
            try {
                val packageManager = cordova.activity.packageManager
                val permissionsInPackage = packageManager.getPackageInfo(
                    cordova.activity.packageName,
                    PackageManager.GET_PERMISSIONS
                ).requestedPermissions
                if (permissionsInPackage != null) {
                    for (permission in permissionsInPackage) {
                        if (permission == Manifest.permission.CAMERA) {
                            takePicturePermission = false
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(LOG_TAG, e.message.toString())
            }
        }
        if (takePicturePermission && saveAlbumPermission) {
            cordova.setActivityResultCallback(this)
            camController?.takePicture(cordova.activity, returnType, encodingType)
        } else if (saveAlbumPermission && !takePicturePermission) {
            PermissionHelper.requestPermission(this, TAKE_PIC_SEC, Manifest.permission.CAMERA)
        } else if (!saveAlbumPermission && takePicturePermission && Build.VERSION.SDK_INT < 33) {
            PermissionHelper.requestPermissions(
                this,
                TAKE_PIC_SEC,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        } else if (!saveAlbumPermission && takePicturePermission && Build.VERSION.SDK_INT >= 33) {
            PermissionHelper.requestPermissions(
                this,
                TAKE_PIC_SEC,
                arrayOf(READ_MEDIA_VIDEO, READ_MEDIA_IMAGES)
            )
        } else {
            PermissionHelper.requestPermissions(this, TAKE_PIC_SEC, permissions)
        }
    }

    /**
     * Create a file in the applications temporary directory based upon the supplied encoding.
     *
     * @param encodingType of the image to be taken
     * @param fileName or resultant File object.
     * @return a File object pointing to the temporary picture
     */
    /**
     * Create a file in the applications temporary directory based upon the supplied encoding.
     *
     * @param encodingType of the image to be taken
     * @return a File object pointing to the temporary picture
     */
    private fun createCaptureFile(encodingType: Int, fileName: String = ""): File {
        var fileName = fileName
        if (fileName.isEmpty()) {
            fileName = ".Pic"
        }
        fileName = if (encodingType == JPEG) {
            fileName + JPEG_EXTENSION
        } else if (encodingType == PNG) {
            fileName + PNG_EXTENSION
        } else {
            throw IllegalArgumentException("Invalid Encoding Type: $encodingType")
        }
        return File(tempDirectoryPath, fileName)
    }

    /**
     * Get image from photo library.
     *
     * @param srcType           The album to get image from.
     * @param returnType        Set the type of image to return.
     * @param encodingType
     */
    fun callGetImage(srcType: Int, returnType: Int, encodingType: Int) {

        if (Build.VERSION.SDK_INT < 33 && !PermissionHelper.hasPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        ) {
            PermissionHelper.requestPermission(
                this,
                SAVE_TO_ALBUM_SEC,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        } else if (Build.VERSION.SDK_INT >= 33 && (!PermissionHelper.hasPermission(
                this,
                READ_MEDIA_IMAGES
            ) || !PermissionHelper.hasPermission(this, READ_MEDIA_VIDEO))
        ) {
            PermissionHelper.requestPermissions(
                this, SAVE_TO_ALBUM_SEC, arrayOf(
                    READ_MEDIA_VIDEO, READ_MEDIA_IMAGES
                )
            )
        } else {
            camParameters?.let {
                cordova.setActivityResultCallback(this)
                //camController?.getImage(this.cordova.activity, srcType, returnType, it)
            }
        }
    }

    /**
     * Applies all needed transformation to the image received from the camera.
     *
     * @param destType          In which form should we return the image
     * @param intent            An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
     */
    @Throws(IOException::class)
    private fun processResultFromCamera(destType: Int, intent: Intent?) {
        var rotate = 0

        // Create an ExifHelper to save the exif data that is lost during compression
        val exif = ExifHelper()
        val sourcePath = if (allowEdit && croppedUri != null) croppedFilePath else imageFilePath
        if (encodingType == JPEG) {
            try {
                //We don't support PNG, so let's not pretend we do
                exif.createInFile(sourcePath)
                exif.readExifData()
                rotate = exif.orientation
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        var bitmap: Bitmap? = null
        var galleryUri: Uri? = null

        // CB-5479 When this option is given the unchanged image should be saved
        // in the gallery and the modified image is saved in the temporary
        // directory
        if (saveToPhotoAlbum) {
            //galleryUri = Uri.fromFile(new File(getPicturesPath()));
            val galleryPathVO = picturesPath
            galleryUri = Uri.fromFile(File(galleryPathVO.galleryPath))
            if (allowEdit && croppedUri != null) {
                writeUncompressedImage(croppedUri, galleryUri)
            } else {
                //Uri imageUri = this.imageUri;
                //writeUncompressedImage(imageUri, galleryUri);
                if (Build.VERSION.SDK_INT <= 28) { // Between LOLLIPOP_MR1 and P, can be changed later to the constant Build.VERSION_CODES.P
                    writeTakenPictureToGalleryLowerThanAndroidQ(galleryUri)
                } else { // Android Q or higher
                    writeTakenPictureToGalleryStartingFromAndroidQ(galleryPathVO)
                }
            }

            //refreshGallery(galleryUri);
        }

        // If sending base64 image back
        if (destType == DATA_URL) {
            bitmap = getScaledAndRotatedBitmap(sourcePath)
            if (bitmap == null) {
                // Try to get the bitmap from intent.
                if (intent != null) {
                    try {
                        // getExtras can throw different exceptions
                        val extras = intent.extras
                        if (extras != null) {
                            bitmap = extras["data"] as Bitmap?
                        }
                    } catch (e: Exception) {
                        // Don't let the exception bubble up, bitmap will be null (check below)
                    }
                }
            }

            // Double-check the bitmap.
            if (bitmap == null) {
                LOG.d(LOG_TAG, "I either have a null image path or bitmap")
                sendError(CameraError.TAKE_PHOTO_ERROR)
                return
            }
            processPicture(bitmap, encodingType)
            if (!saveToPhotoAlbum) {
                checkForDuplicateImage(DATA_URL)
            }
        } else if (destType == FILE_URI || destType == NATIVE_URI) {
            // If all this is true we shouldn't compress the image.
            if (targetHeight == -1 && targetWidth == -1 && mQuality == 100 &&
                !correctOrientation
            ) {

                // If we saved the uncompressed photo to the album, we can just
                // return the URI we already created
                if (saveToPhotoAlbum) {
                    callbackContext?.success(galleryUri.toString())
                } else {
                    val uri = Uri.fromFile(
                        createCaptureFile(
                            encodingType,
                            System.currentTimeMillis().toString() + ""
                        )
                    )
                    if (allowEdit && croppedUri != null) {
                        val croppedUri = Uri.parse(croppedFilePath)
                        writeUncompressedImage(croppedUri, uri)
                    } else {
                        val imageUri = imageUri
                        writeUncompressedImage(imageUri, uri)
                    }
                    callbackContext?.success(uri.toString())
                }
            } else {
                val uri = Uri.fromFile(
                    createCaptureFile(
                        encodingType,
                        System.currentTimeMillis().toString() + ""
                    )
                )
                bitmap = getScaledAndRotatedBitmap(sourcePath)

                // Double-check the bitmap.
                if (bitmap == null) {
                    LOG.d(LOG_TAG, "I either have a null image path or bitmap")
                    sendError(CameraError.TAKE_PHOTO_ERROR)
                    return
                }


                // Add compressed version of captured image to returned media store Uri
                val os = cordova.activity.contentResolver.openOutputStream(uri)
                //CompressFormat compressFormat = encodingType == JPEG ?
                //        CompressFormat.JPEG :
                //        CompressFormat.PNG;
                val compressFormat = getCompressFormatForEncodingType(encodingType)
                bitmap.compress(compressFormat, mQuality, os)
                os?.close()

                // Restore exif data to file
                if (encodingType == JPEG) {
                    val exifPath: String?
                    exifPath = uri.path
                    //We just finished rotating it by an arbitrary orientation, just make sure it's normal
                    if (rotate != ExifInterface.ORIENTATION_NORMAL) exif.resetOrientation()
                    exif.createOutFile(exifPath)
                    exif.writeExifData()
                }

                // Send Uri back to JavaScript for viewing image
                callbackContext?.success(uri.toString())
            }
        } else {
            throw IllegalStateException()
        }
        cleanup(FILE_URI, imageUri, galleryUri, bitmap)
        bitmap = null
    }

    //private String getPicturesPath() {
    @Throws(IOException::class)
    private fun writeTakenPictureToGalleryLowerThanAndroidQ(galleryUri: Uri?) {
        writeUncompressedImage(imageUri, galleryUri)
        refreshGallery(galleryUri)
    }

    @Throws(IOException::class)
    private fun writeTakenPictureToGalleryStartingFromAndroidQ(galleryPathVO: GalleryPathVO) {
        // Starting from Android Q, working with the ACTION_MEDIA_SCANNER_SCAN_FILE intent is deprecated
        // https://developer.android.com/reference/android/content/Intent#ACTION_MEDIA_SCANNER_SCAN_FILE
        // we must start working with the MediaStore from Android Q on.
        val resolver = cordova.activity.contentResolver
        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, galleryPathVO.galleryFileName)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, getMimetypeForFormat(encodingType))
        val galleryOutputUri =
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        val fileStream = FileHelper.getInputStreamFromUriString(imageUri.toString(), cordova)
        writeUncompressedImage(fileStream, galleryOutputUri)
    }

    private fun getCompressFormatForEncodingType(encodingType: Int): Bitmap.CompressFormat {
        return if (encodingType == JPEG) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
    }

    //String galleryPath = storageDir.getAbsolutePath() + "/" + imageFileName;
    //return galleryPath;
    private val picturesPath: GalleryPathVO
        private get() {
            val timeStamp = SimpleDateFormat(TIME_FORMAT).format(Date())
            val imageFileName =
                "IMG_" + timeStamp + if (encodingType == JPEG) JPEG_EXTENSION else PNG_EXTENSION
            val storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            )
            storageDir.mkdirs()
            //String galleryPath = storageDir.getAbsolutePath() + "/" + imageFileName;
            //return galleryPath;
            return GalleryPathVO(storageDir.absolutePath, imageFileName)
        }

    private fun refreshGallery(contentUri: Uri?) {
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        // Starting from Android Q, working with the ACTION_MEDIA_SCANNER_SCAN_FILE intent is deprecated
        mediaScanIntent.data = contentUri
        cordova.activity.sendBroadcast(mediaScanIntent)
    }

    /**
     * Converts output image format int value to string value of mime type.
     * @param outputFormat int Output format of camera API.
     * Must be value of either JPEG or PNG constant
     * @return String String value of mime type or empty string if mime type is not supported
     */
    private fun getMimetypeForFormat(outputFormat: Int): String {
        if (outputFormat == PNG) return PNG_MIME_TYPE
        return if (outputFormat == JPEG) JPEG_MIME_TYPE else ""
    }

    @Throws(IOException::class)
    private fun outputModifiedBitmap(bitmap: Bitmap, uri: Uri?): String {
        // Some content: URIs do not map to file paths (e.g. picasa).
        val realPath = FileHelper.getRealPath(uri, cordova)

        // Get filename from uri
        val fileName = realPath?.substring(realPath.lastIndexOf('/') + 1)
            ?: "modified." + if (encodingType == JPEG) JPEG_TYPE else PNG_TYPE
        val timeStamp = SimpleDateFormat(TIME_FORMAT).format(Date())
        //String fileName = "IMG_" + timeStamp + (this.encodingType == JPEG ? ".jpg" : ".png");
        val modifiedPath = "$tempDirectoryPath/$fileName"
        val os: OutputStream = FileOutputStream(modifiedPath)
        //CompressFormat compressFormat = this.encodingType == JPEG ?
        //        CompressFormat.JPEG :
        //        CompressFormat.PNG;
        val compressFormat = getCompressFormatForEncodingType(encodingType)
        bitmap.compress(compressFormat, mQuality, os)
        os.close()
        if (exifData != null && encodingType == JPEG) {
            try {
                if (correctOrientation && orientationCorrected) {
                    exifData?.resetOrientation()
                }
                exifData?.createOutFile(modifiedPath)
                exifData?.writeExifData()
                exifData = null
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return modifiedPath
    }

    /**
     * Called when the camera view exits.
     *
     * @param requestCode The request code originally supplied to startActivityForResult(),
     * allowing you to identify who this result came from.
     * @param resultCode  The integer result code returned by the child activity through its setResult().
     * @param intent      An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent) {

        // Get src and dest types from request code for a Camera Activity
        val srcType = requestCode / 16 - 1
        var destType = requestCode % 16 - 1
        if (requestCode == CROP_GALERY) {
            if (resultCode == Activity.RESULT_OK) {
                val result = BitmapFactory.decodeFile(croppedFilePath)
                val byteArrayOutputStream = ByteArrayOutputStream()
                if (result.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)) {
                    val byteArray = byteArrayOutputStream.toByteArray()
                    val base64Result = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                    callbackContext?.success(base64Result)
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                sendError(CameraError.NO_IMAGE_SELECTED_ERROR)
            } else {
                sendError(CameraError.EDIT_IMAGE_ERROR)
            }
        } else if (requestCode >= CROP_CAMERA) {
            if (resultCode == Activity.RESULT_OK) {

                // Because of the inability to pass through multiple intents, this hack will allow us
                // to pass arcane codes back.
                destType = requestCode - CROP_CAMERA
                try {
                    processResultFromCamera(destType, intent)
                } catch (e: IOException) {
                    e.printStackTrace()
                    LOG.e(LOG_TAG, "Unable to write to file")
                }
            } // If cancelled
            else if (resultCode == Activity.RESULT_CANCELED) {
                sendError(CameraError.NO_PICTURE_TAKEN_ERROR)
            } else {
                sendError(CameraError.EDIT_IMAGE_ERROR)
            }
        } else if (srcType == CAMERA) {
            // If image available
            if (resultCode == Activity.RESULT_OK) {
                try {
                    if (allowEdit) {
                        val tmpFile = FileProvider.getUriForFile(
                            cordova.activity,
                            "$applicationId.camera.provider",
                            createCaptureFile(encodingType)
                        )
                        openCropActivity(tmpFile, CROP_CAMERA, destType)
                    } else {
                        camParameters?.let { params ->
                            camController?.processResultFromCamera(
                                cordova.activity,
                                destType,
                                intent,
                                params,
                                {
                                    val pluginResult = PluginResult(PluginResult.Status.OK, it)
                                    this.callbackContext?.sendPluginResult(pluginResult)
                                },
                                {
                                    val pluginResult = PluginResult(PluginResult.Status.ERROR, it.toString())
                                    this.callbackContext?.sendPluginResult(pluginResult)
                                }
                            )
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    sendError(CameraError.TAKE_PHOTO_ERROR)
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                sendError(CameraError.NO_PICTURE_TAKEN_ERROR)
            } else {
                sendError(CameraError.TAKE_PHOTO_ERROR)
            }
        } else if (srcType == PHOTOLIBRARY || srcType == SAVEDPHOTOALBUM) {
            if (resultCode == Activity.RESULT_OK && intent != null) {
                val finalDestType = destType
                if (allowEdit) {
                    val uri = intent.data
                    openCropActivity(uri, CROP_GALERY, destType)
                } else {
                    cordova.threadPool.execute {
                        camParameters?.let { params ->
                            /*
                            camController?.processResultFromGallery(
                                this.cordova.activity,
                                finalDestType,
                                intent,
                                params,
                                {
                                    val pluginResult = PluginResult(PluginResult.Status.OK, it)
                                    this.callbackContext?.sendPluginResult(pluginResult)
                                },
                                {
                                    val pluginResult = PluginResult(PluginResult.Status.ERROR, it.toString())
                                    this.callbackContext?.sendPluginResult(pluginResult)
                                })

                             */
                        }
                        //processResultFromGallery(finalDestType, intent)
                    }
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                sendError(CameraError.NO_IMAGE_SELECTED_ERROR)
            } else {
                sendError(CameraError.GET_IMAGE_ERROR)
            }
        } else if (requestCode == RECOVERABLE_DELETE_REQUEST) {
            // retry media store deletion ...
            val contentResolver = cordova.activity.contentResolver
            try {
                contentResolver.delete(pendingDeleteMediaUri!!, null, null)
            } catch (e: Exception) {
                LOG.e(LOG_TAG, "Unable to delete media store file after permission was granted")
            }
            pendingDeleteMediaUri = null
        }
    }

    private fun exifToDegrees(exifOrientation: Int): Int {
        return if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
            90
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
            180
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
            270
        } else {
            0
        }
    }

    /**
     * Write an inputstream to local disk
     *
     * @param fis - The InputStream to write
     * @param dest - Destination on disk to write to
     * @throws FileNotFoundException
     * @throws IOException
     */
    @Throws(FileNotFoundException::class, IOException::class)
    private fun writeUncompressedImage(fis: InputStream?, dest: Uri?) {
        var os: OutputStream? = null
        try {
            os = cordova.activity.contentResolver.openOutputStream(dest!!)
            val buffer = ByteArray(4096)
            var len: Int
            while (fis?.read(buffer).also { len = it!! } != -1) {
                os?.write(buffer, 0, len)
            }
            os?.flush()
        } finally {
            if (os != null) {
                try {
                    os.close()
                } catch (e: IOException) {
                    LOG.d(LOG_TAG, "Exception while closing output stream.")
                }
            }
            if (fis != null) {
                try {
                    fis.close()
                } catch (e: IOException) {
                    LOG.d(LOG_TAG, "Exception while closing file input stream.")
                }
            }
        }
    }

    /**
     * In the special case where the default width, height and quality are unchanged
     * we just write the file out to disk saving the expensive Bitmap.compress function.
     *
     * @param src
     * @throws FileNotFoundException
     * @throws IOException
     */
    @Throws(FileNotFoundException::class, IOException::class)
    private fun writeUncompressedImage(src: Uri?, dest: Uri?) {

        //FileInputStream fis = new FileInputStream(FileHelper.stripFileProtocol(src.toString()));
        val fis = FileHelper.getInputStreamFromUriString(src.toString(), cordova)
        writeUncompressedImage(fis, dest)
    }

    /**
     * Return a scaled and rotated bitmap based on the target width and height
     *
     * @param imageUrl
     * @return
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun getScaledAndRotatedBitmap(imageUrl: String?): Bitmap? {
        // If no new width or height were specified, and orientation is not needed return the original bitmap
        if (targetWidth <= 0 && targetHeight <= 0 && !correctOrientation) {
            var fileStream: InputStream? = null
            var image: Bitmap? = null
            try {
                fileStream = FileHelper.getInputStreamFromUriString(imageUrl, cordova)
                image = BitmapFactory.decodeStream(fileStream)
            } catch (e: OutOfMemoryError) {
                callbackContext?.error(e.localizedMessage)
            } catch (e: Exception) {
                callbackContext?.error(e.localizedMessage)
            } finally {
                if (fileStream != null) {
                    try {
                        fileStream.close()
                    } catch (e: IOException) {
                        LOG.d(LOG_TAG, "Exception while closing file input stream.")
                    }
                }
            }
            return image
        }


        /*  Copy the inputstream to a temporary file on the device.
            We then use this temporary file to determine the width/height/orientation.
            This is the only way to determine the orientation of the photo coming from 3rd party providers (Google Drive, Dropbox,etc)
            This also ensures we create a scaled bitmap with the correct orientation

             We delete the temporary file once we are done
         */
        var localFile: File? = null
        var galleryUri: Uri? = null
        var rotate = 0
        try {
            val fileStream = FileHelper.getInputStreamFromUriString(imageUrl, cordova)
            if (fileStream != null) {
                // Generate a temporary file
                val timeStamp = SimpleDateFormat(TIME_FORMAT).format(Date())
                val fileName =
                    "IMG_" + timeStamp + if (encodingType == JPEG) JPEG_EXTENSION else PNG_EXTENSION
                localFile = File(tempDirectoryPath + fileName)
                galleryUri = Uri.fromFile(localFile)
                writeUncompressedImage(fileStream, galleryUri)
                try {
                    //  ExifInterface doesn't like the file:// prefix
                    val filePath = galleryUri.toString().replace("file://", "")
                    // read exifData of source
                    exifData = ExifHelper()
                    exifData?.createInFile(filePath)
                    exifData?.readExifData()
                    // Use ExifInterface to pull rotation information
                    if (correctOrientation) {
                        val exif = ExifInterface(filePath)
                        rotate = exifToDegrees(
                            exif.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_UNDEFINED
                            )
                        )
                    }
                } catch (oe: Exception) {
                    LOG.w(LOG_TAG, "Unable to read Exif data: $oe")
                    rotate = 0
                }
            }
        } catch (e: Exception) {
            LOG.e(LOG_TAG, "Exception while getting input stream: $e")
            return null
        }
        return try {
            // figure out the original width and height of the image
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            var fileStream: InputStream? = null
            try {
                fileStream = FileHelper.getInputStreamFromUriString(galleryUri.toString(), cordova)
                BitmapFactory.decodeStream(fileStream, null, options)
            } finally {
                if (fileStream != null) {
                    try {
                        fileStream.close()
                    } catch (e: IOException) {
                        LOG.d(LOG_TAG, "Exception while closing file input stream.")
                    }
                }
            }


            //CB-2292: WTF? Why is the width null?
            if (options.outWidth == 0 || options.outHeight == 0) {
                return null
            }

            // User didn't specify output dimensions, but they need orientation
            if (targetWidth <= 0 && targetHeight <= 0) {
                targetWidth = options.outWidth
                targetHeight = options.outHeight
            }

            // Setup target width/height based on orientation
            val rotatedWidth: Int
            val rotatedHeight: Int
            var rotated = false
            if (rotate == 90 || rotate == 270) {
                rotatedWidth = options.outHeight
                rotatedHeight = options.outWidth
                rotated = true
            } else {
                rotatedWidth = options.outWidth
                rotatedHeight = options.outHeight
            }

            // determine the correct aspect ratio
            val widthHeight = calculateAspectRatio(rotatedWidth, rotatedHeight)


            // Load in the smallest bitmap possible that is closest to the size we want
            options.inJustDecodeBounds = false
            options.inSampleSize =
                calculateSampleSize(rotatedWidth, rotatedHeight, widthHeight[0], widthHeight[1])
            var unscaledBitmap: Bitmap? = null
            try {
                fileStream = FileHelper.getInputStreamFromUriString(galleryUri.toString(), cordova)
                unscaledBitmap = BitmapFactory.decodeStream(fileStream, null, options)
            } finally {
                if (fileStream != null) {
                    try {
                        fileStream.close()
                    } catch (e: IOException) {
                        LOG.d(LOG_TAG, "Exception while closing file input stream.")
                    }
                }
            }
            if (unscaledBitmap == null) {
                return null
            }
            val scaledWidth = if (!rotated) widthHeight[0] else widthHeight[1]
            val scaledHeight = if (!rotated) widthHeight[1] else widthHeight[0]
            var scaledBitmap =
                Bitmap.createScaledBitmap(unscaledBitmap, scaledWidth, scaledHeight, true)
            if (scaledBitmap != unscaledBitmap) {
                unscaledBitmap.recycle()
                unscaledBitmap = null
            }
            if (correctOrientation && rotate != 0) {
                val matrix = Matrix()
                matrix.setRotate(rotate.toFloat())
                try {
                    scaledBitmap = Bitmap.createBitmap(
                        scaledBitmap,
                        0,
                        0,
                        scaledBitmap.width,
                        scaledBitmap.height,
                        matrix,
                        true
                    )
                    orientationCorrected = true
                } catch (oom: OutOfMemoryError) {
                    orientationCorrected = false
                }
            }
            scaledBitmap
        } finally {
            // delete the temporary copy
            localFile?.delete()
        }
    }

    /**
     * Maintain the aspect ratio so the resulting image does not look smooshed
     *
     * @param origWidth
     * @param origHeight
     * @return
     */
    fun calculateAspectRatio(origWidth: Int, origHeight: Int): IntArray {
        var newWidth = targetWidth
        var newHeight = targetHeight

        // If no new width or height were specified return the original bitmap
        if (newWidth <= 0 && newHeight <= 0) {
            newWidth = origWidth
            newHeight = origHeight
        } else if (newWidth > 0 && newHeight <= 0) {
            newHeight = ((newWidth / origWidth.toDouble()) * origHeight).toInt()
        } else if (newWidth <= 0 && newHeight > 0) {
            newWidth = ((newHeight / origHeight.toDouble()) * origWidth).toInt()
        } else {
            val newRatio = newWidth / newHeight.toDouble()
            val origRatio = origWidth / origHeight.toDouble()
            if (origRatio > newRatio) {
                newHeight = newWidth * origHeight / origWidth
            } else if (origRatio < newRatio) {
                newWidth = newHeight * origWidth / origHeight
            }
        }
        val retval = IntArray(2)
        retval[0] = newWidth
        retval[1] = newHeight
        return retval
    }

    /**
     * Creates a cursor that can be used to determine how many images we have.
     *
     * @return a cursor
     */
    private fun queryImgDB(contentStore: Uri): Cursor? {
        return cordova.activity.contentResolver.query(
            contentStore, arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            null
        )
    }

    /**
     * Cleans up after picture taking. Checking for duplicates and that kind of stuff.
     *
     * @param newImage
     */

    private fun cleanup(imageType: Int, oldImage: Uri?, newImage: Uri?, bitmap: Bitmap?) {
        bitmap?.recycle()

        // Clean up initial camera-written image file.
        File(FileHelper.stripFileProtocol(oldImage.toString())).delete()
        checkForDuplicateImage(imageType)
        // Scan for the gallery to update pic refs in gallery
        if (saveToPhotoAlbum && newImage != null) {
            //scanForGallery(newImage)
        }
        System.gc()
    }

    /**
     * Used to find out if we are in a situation where the Camera Intent adds to images
     * to the content store. If we are using a FILE_URI and the number of images in the DB
     * increases by 2 we have a duplicate, when using a DATA_URL the number is 1.
     *
     * @param type FILE_URI or DATA_URL
     */
    private fun checkForDuplicateImage(type: Int) {
        var diff = 1
        val contentStore = whichContentStore()
        val cursor = queryImgDB(contentStore)
        val currentNumOfImages = cursor?.count
        if (type == FILE_URI && saveToPhotoAlbum) {
            diff = 2
        }

        // delete the duplicate file if the difference is 2 for file URI or 1 for Data URL
        if (currentNumOfImages!! - numPics == diff) {
            cursor.moveToLast()
            var id =
                Integer.valueOf(cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media._ID)))
            if (diff == 2) {
                id--
            }
            val uri = Uri.parse("$contentStore/$id")
            try {
                cordova.activity.contentResolver.delete(uri, null, null)
            } catch (securityException: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val recoverableSecurityException: RecoverableSecurityException
                    recoverableSecurityException =
                        if (securityException is RecoverableSecurityException) {
                            securityException
                        } else {
                            throw RuntimeException(securityException.message, securityException)
                        }
                    val pendingIntent = recoverableSecurityException.userAction.actionIntent
                    cordova.setActivityResultCallback(this)
                    pendingDeleteMediaUri = uri
                    try {
                        cordova.activity.startIntentSenderForResult(
                            pendingIntent.intentSender,
                            RECOVERABLE_DELETE_REQUEST, null, 0, 0,
                            0, null
                        )
                    } catch (e: IntentSender.SendIntentException) {
                        e.printStackTrace()
                    }
                } else {
                    throw RuntimeException(securityException.message, securityException)
                }
            }
            cursor.close()
        }
    }

    /**
     * Determine if we are storing the images in internal or external storage
     *
     * @return Uri
     */
    private fun whichContentStore(): Uri {
        return if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.INTERNAL_CONTENT_URI
        }
    }

    /**
     * Compress bitmap using jpeg, convert to Base64 encoded string, and return to JavaScript.
     *
     * @param bitmap
     */
    fun processPicture(bitmap: Bitmap, encodingType: Int) {
        var jpeg_data: ByteArrayOutputStream? = ByteArrayOutputStream()
        //CompressFormat compressFormat = encodingType == JPEG ?
        //        CompressFormat.JPEG :
        //        CompressFormat.PNG;
        val compressFormat = getCompressFormatForEncodingType(encodingType)
        try {
            if (bitmap.compress(compressFormat, mQuality, jpeg_data)) {
                var code = jpeg_data?.toByteArray()
                var output = Base64.encode(code, Base64.NO_WRAP)
                var js_out: String? = String(output)
                callbackContext?.success(js_out)
                js_out = null
                output = null
                code = null
            }
        } catch (e: Exception) {
            sendError(CameraError.PROCESS_IMAGE_ERROR)
        }
        jpeg_data = null
    }

    /**
     * Send error message to JavaScript.
     *
     * @param err
     */
    fun failPicture(err: String?) {
        callbackContext?.error(err)
    }

    /*
    private fun scanForGallery(newImage: Uri) {
        scanMe = newImage
        if (conn != null) {
            conn?.disconnect()
        }
        conn = MediaScannerConnection(cordova.activity.applicationContext, this)
        conn?.connect()
    }
     */

    override fun onRequestPermissionResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        for (i in grantResults.indices) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED && permissions[i] == Manifest.permission.CAMERA) {
                sendError(CameraError.CAMERA_PERMISSION_DENIED_ERROR)
                return
            } else if (grantResults[i] == PackageManager.PERMISSION_DENIED && ((Build.VERSION.SDK_INT < 33
                        && (permissions[i] == Manifest.permission.READ_EXTERNAL_STORAGE || permissions[i] == Manifest.permission.WRITE_EXTERNAL_STORAGE))
                        || (Build.VERSION.SDK_INT >= 33
                        && (permissions[i] == READ_MEDIA_IMAGES || permissions[i] == READ_MEDIA_VIDEO)))
            ) {
                sendError(CameraError.GALLERY_PERMISSION_DENIED_ERROR)
                return
            }
        }
        when (requestCode) {
            TAKE_PIC_SEC -> {
                cordova.setActivityResultCallback(this)
                camController?.takePicture(this.cordova.activity, destType, encodingType)
            }
            SAVE_TO_ALBUM_SEC -> callGetImage(srcType, destType, encodingType)
        }
    }

    /**
     * Taking or choosing a picture launches another Activity, so we need to implement the
     * save/restore APIs to handle the case where the CordovaActivity is killed by the OS
     * before we get the launched Activity's result.
     */
    override fun onSaveInstanceState(): Bundle {
        val state = Bundle()
        state.putInt("destType", destType)
        state.putInt("srcType", srcType)
        state.putInt("mQuality", mQuality)
        state.putInt("targetWidth", targetWidth)
        state.putInt("targetHeight", targetHeight)
        state.putInt("encodingType", encodingType)
        state.putInt("mediaType", mediaType)
        state.putInt("numPics", numPics)
        state.putBoolean("allowEdit", allowEdit)
        state.putBoolean("correctOrientation", correctOrientation)
        state.putBoolean("saveToPhotoAlbum", saveToPhotoAlbum)
        if (croppedUri != null) {
            state.putString(CROPPED_URI_KEY, croppedFilePath)
        }
        if (imageUri != null) {
            state.putString(IMAGE_URI_KEY, imageFilePath)
        }
        if (imageFilePath != null) {
            state.putString(IMAGE_FILE_PATH_KEY, imageFilePath)
        }
        return state
    }

    override fun onRestoreStateForActivityResult(state: Bundle, callbackContext: CallbackContext) {
        destType = state.getInt("destType")
        srcType = state.getInt("srcType")
        mQuality = state.getInt("mQuality")
        targetWidth = state.getInt("targetWidth")
        targetHeight = state.getInt("targetHeight")
        encodingType = state.getInt("encodingType")
        mediaType = state.getInt("mediaType")
        numPics = state.getInt("numPics")
        allowEdit = state.getBoolean("allowEdit")
        correctOrientation = state.getBoolean("correctOrientation")
        saveToPhotoAlbum = state.getBoolean("saveToPhotoAlbum")
        if (state.containsKey(CROPPED_URI_KEY)) {
            croppedUri = Uri.parse(state.getString(CROPPED_URI_KEY))
        }
        if (state.containsKey(IMAGE_URI_KEY)) {
            //I have no idea what type of URI is being passed in
            imageUri = Uri.parse(state.getString(IMAGE_URI_KEY))
        }
        if (state.containsKey(IMAGE_FILE_PATH_KEY)) {
            imageFilePath = state.getString(IMAGE_FILE_PATH_KEY)
        }
        this.callbackContext = callbackContext
    }

    private fun openCropActivity(picUri: Uri?, requestCode: Int, destType: Int) {
        val cropIntent = Intent(cordova.activity, ImageEditorActivity::class.java)

        // create output file
        croppedFilePath =
            createCaptureFile(JPEG, System.currentTimeMillis().toString() + "").absolutePath
        croppedUri = Uri.parse(croppedFilePath)
        cropIntent.putExtra(ImageEditorActivity.IMAGE_OUTPUT_URI_EXTRAS, croppedFilePath)
        cropIntent.putExtra(ImageEditorActivity.IMAGE_INPUT_URI_EXTRAS, picUri.toString())
        if (cordova != null) {
            cordova.startActivityForResult(
                this,
                cropIntent,
                requestCode + destType
            )
        }
    }

    private fun sendError(error: CameraError) {
        val jsonResult = JSONObject()
        try {
            jsonResult.put("code", formatErrorCode(error.code))
            jsonResult.put("message", error.message)
            callbackContext?.error(jsonResult)
        } catch (e: JSONException) {
            LOG.d(LOG_TAG, "Error: JSONException occurred while preparing to send an error.")
            callbackContext?.error("There was an error performing the operation.")
        }
    }

    private fun formatErrorCode(code: Int): String {
        val stringCode = Integer.toString(code)
        return ERROR_FORMAT_PREFIX + "0000$stringCode".substring(stringCode.length)
    }

    companion object {
        private const val DATA_URL = 0 // Return base64 encoded string
        private const val FILE_URI =
            1 // Return file uri (content://media/external/images/media/2 for Android)
        private const val NATIVE_URI = 2 // On Android, this is the same as FILE_URI
        private const val PHOTOLIBRARY =
            0 // Choose image from picture library (same as SAVEDPHOTOALBUM for Android)
        private const val CAMERA = 1 // Take picture from camera
        private const val SAVEDPHOTOALBUM =
            2 // Choose image from picture library (same as PHOTOLIBRARY for Android)
        private const val RECOVERABLE_DELETE_REQUEST = 3 // Result of Recoverable Security Exception
        private const val PICTURE =
            0 // allow selection of still pictures only. DEFAULT. Will return format specified via DestinationType
        private const val VIDEO = 1 // allow selection of video only, ONLY RETURNS URL
        private const val ALLMEDIA = 2 // allow selection from all media types
        private const val JPEG = 0 // Take a picture of type JPEG
        private const val PNG = 1 // Take a picture of type PNG
        private const val JPEG_TYPE = "jpg"
        private const val PNG_TYPE = "png"
        private const val JPEG_EXTENSION = "." + JPEG_TYPE
        private const val PNG_EXTENSION = "." + PNG_TYPE
        private const val PNG_MIME_TYPE = "image/png"
        private const val JPEG_MIME_TYPE = "image/jpeg"
        private const val GET_PICTURE = "Get Picture"
        private const val GET_VIDEO = "Get Video"
        private const val GET_All = "Get All"
        private const val CROPPED_URI_KEY = "croppedUri"
        private const val IMAGE_URI_KEY = "imageUri"
        private const val IMAGE_FILE_PATH_KEY = "imageFilePath"
        private const val TAKE_PICTURE_ACTION = "takePicture"
        const val TAKE_PIC_SEC = 0
        const val SAVE_TO_ALBUM_SEC = 1
        private const val LOG_TAG = "CameraLauncher"

        //Where did this come from?
        private const val CROP_CAMERA = 100
        private const val CROP_GALERY = 666
        private const val TIME_FORMAT = "yyyyMMdd_HHmmss"

        //we need literal values because we cannot simply do Manifest.permission.READ_MEDIA_IMAGES, because of the target sdk
        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"

        //for errors
        private const val ERROR_FORMAT_PREFIX = "OS-PLUG-CAMR-"
        protected val permissions = createPermissionArray()

        /**
         * Figure out what ratio we can load our image into memory at while still being bigger than
         * our desired width and height
         *
         * @param srcWidth
         * @param srcHeight
         * @param dstWidth
         * @param dstHeight
         * @return
         */
        fun calculateSampleSize(srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int): Int {
            val srcAspect = srcWidth.toFloat() / srcHeight.toFloat()
            val dstAspect = dstWidth.toFloat() / dstHeight.toFloat()
            return if (srcAspect > dstAspect) {
                srcWidth / dstWidth
            } else {
                srcHeight / dstHeight
            }
        }

        private fun createPermissionArray(): Array<String> {
            return if (Build.VERSION.SDK_INT < 33) {
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            } else {
                arrayOf(
                    Manifest.permission.CAMERA,
                    READ_MEDIA_IMAGES,
                    READ_MEDIA_VIDEO
                )
            }
        }
    }
}